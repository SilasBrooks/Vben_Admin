package com.vben.service.module.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.module.notice.entity.SysNotice;
import com.vben.service.module.notice.mapper.SysNoticeMapper;
import com.vben.service.module.notice.websocket.NoticeWebSocketHandler;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务：落库为准，WebSocket 推送为实时通道。
 *
 * <p>{@link #send} 先落库再推送，推送失败（用户未在线/会话异常）仅记 warn，
 * 不影响落库与业务主流程——用户下次拉取列表仍能看到通知。
 * 依赖自身 mapper、websocket handler 与 SysUserMapper（公告广播解析接收人），
 * 供 system/monitor 模块单向依赖，避免循环注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

  /** 消息类型：安全类提醒 */
  private static final String MSG_TYPE_SECURITY = "security";

  /** 消息类型：管理员公告广播 */
  private static final String MSG_TYPE_ANNOUNCEMENT = "announcement";

  /** 用户状态：启用 */
  private static final int USER_STATUS_ENABLED = 0;

  /** 公告目标范围：全员 */
  private static final String TARGET_ALL = "all";

  /** 公告目标范围：按部门 */
  private static final String TARGET_DEPT = "dept";

  /** 公告目标范围：按用户 */
  private static final String TARGET_USER = "user";

  /** 未读标记：0 */
  private static final int READ_FLAG_UNREAD = 0;

  /** 已读标记：1 */
  private static final int READ_FLAG_READ = 1;

  private final SysNoticeMapper noticeMapper;
  private final NoticeWebSocketHandler webSocketHandler;
  private final SysUserMapper userMapper;

  /**
   * 发送安全类通知：落库 + 实时推送给当事人。
   *
   * @param userId  接收人 id
   * @param title   通知标题
   * @param content 通知内容
   */
  public void send(Long userId, String title, String content) {
    send(userId, title, content, MSG_TYPE_SECURITY);
  }

  /**
   * 公告广播：按目标范围解析「启用」用户并逐人落库 + 实时推送（msg_type=announcement）。
   *
   * @param title      公告标题
   * @param content    公告内容
   * @param targetType 目标范围：all 全员 / dept 按部门 / user 按用户
   * @param deptIds    targetType=dept 时的部门 id 集合
   * @param userIds    targetType=user 时的用户 id 集合
   * @return 实际发送人数（去重、过滤停用账号后）
   */
  public int announce(String title, String content, String targetType,
      List<Long> deptIds, List<Long> userIds) {
    List<Long> targets = resolveTargets(targetType, deptIds, userIds);
    for (Long userId : targets) {
      send(userId, title, content, MSG_TYPE_ANNOUNCEMENT);
    }
    return targets.size();
  }

  /** 解析公告接收人：仅启用（status=0）用户，SQL 层去重 */
  private List<Long> resolveTargets(String targetType, List<Long> deptIds, List<Long> userIds) {
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
        .select(SysUser::getId)
        .eq(SysUser::getStatus, USER_STATUS_ENABLED);
    switch (targetType == null ? "" : targetType) {
      case TARGET_ALL -> {
        // 无附加过滤：全部启用用户
      }
      case TARGET_DEPT -> {
        if (deptIds == null || deptIds.isEmpty()) {
          throw BizException.badRequest("error.notice.dept.required");
        }
        wrapper.in(SysUser::getDeptId, deptIds);
      }
      case TARGET_USER -> {
        if (userIds == null || userIds.isEmpty()) {
          throw BizException.badRequest("error.notice.user.required");
        }
        wrapper.in(SysUser::getId, userIds);
      }
      default -> throw BizException.badRequest("error.notice.target.invalid", targetType);
    }
    return userMapper.selectList(wrapper).stream().map(SysUser::getId).toList();
  }

  /** 通用发送：落库 + 推送，推送失败仅告警不影响落库 */
  private void send(Long userId, String title, String content, String msgType) {
    SysNotice notice = new SysNotice();
    notice.setUserId(userId);
    notice.setTitle(title);
    notice.setContent(content);
    notice.setMsgType(msgType);
    notice.setReadFlag(READ_FLAG_UNREAD);
    noticeMapper.insert(notice);

    // 推送失败不影响落库（用户离线属常态），仅告警
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", msgType);
      payload.put("id", notice.getId());
      payload.put("title", notice.getTitle());
      payload.put("content", notice.getContent());
      payload.put("createTime", notice.getCreateTime());
      webSocketHandler.sendToUser(userId, payload);
    } catch (Exception e) {
      log.warn("站内通知 WebSocket 推送失败 userId={} title={}", userId, title, e);
    }
  }

  /**
   * 通知分页列表：按接收人过滤，create_time 倒序。
   *
   * @param msgType 消息类型过滤，空/null 表示不过滤（security=通知 announcement=公告）
   * @return {total, items}
   */
  public Map<String, Object> page(long pageNum, long pageSize, Long userId, String msgType) {
    LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
        .eq(SysNotice::getUserId, userId);
    if (msgType != null && !msgType.isBlank()) {
      wrapper.eq(SysNotice::getMsgType, msgType);
    }
    wrapper.orderByDesc(SysNotice::getCreateTime);
    Page<SysNotice> p = noticeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", p.getTotal());
    data.put("items", p.getRecords());
    return data;
  }

  /** 当前用户未读通知条数 */
  public long unreadCount(Long userId) {
    return noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>()
        .eq(SysNotice::getUserId, userId)
        .eq(SysNotice::getReadFlag, READ_FLAG_UNREAD));
  }

  /**
   * 批量标记已读：限定 user_id 本人，防止越权修改他人通知。
   *
   * @param userId 当前登录用户 id
   * @param ids    通知 id 列表（非空，controller 已校验）
   */
  public void markRead(Long userId, List<Long> ids) {
    SysNotice patch = new SysNotice();
    patch.setReadFlag(READ_FLAG_READ);
    patch.setReadTime(LocalDateTime.now());
    noticeMapper.update(patch, new LambdaUpdateWrapper<SysNotice>()
        .eq(SysNotice::getUserId, userId)
        .in(SysNotice::getId, ids));
  }

  /** 全部标记已读：仅覆盖本人未读通知 */
  public void markAllRead(Long userId) {
    SysNotice patch = new SysNotice();
    patch.setReadFlag(READ_FLAG_READ);
    patch.setReadTime(LocalDateTime.now());
    noticeMapper.update(patch, new LambdaUpdateWrapper<SysNotice>()
        .eq(SysNotice::getUserId, userId)
        .eq(SysNotice::getReadFlag, READ_FLAG_UNREAD));
  }
}
