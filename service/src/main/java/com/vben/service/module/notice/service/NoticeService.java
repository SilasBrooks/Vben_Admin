package com.vben.service.module.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.module.notice.entity.SysNotice;
import com.vben.service.module.notice.mapper.SysNoticeMapper;
import com.vben.service.module.notice.websocket.NoticeWebSocketHandler;
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
 * 仅依赖自身 mapper 与 websocket handler，供 system/monitor 模块单向依赖，避免循环注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

  /** 消息类型：安全类提醒（本期仅此一类） */
  private static final String MSG_TYPE_SECURITY = "security";

  /** 未读标记：0 */
  private static final int READ_FLAG_UNREAD = 0;

  /** 已读标记：1 */
  private static final int READ_FLAG_READ = 1;

  private final SysNoticeMapper noticeMapper;
  private final NoticeWebSocketHandler webSocketHandler;

  /**
   * 发送通知：落库 + 实时推送给当事人。
   *
   * @param userId  接收人 id
   * @param title   通知标题
   * @param content 通知内容
   */
  public void send(Long userId, String title, String content) {
    SysNotice notice = new SysNotice();
    notice.setUserId(userId);
    notice.setTitle(title);
    notice.setContent(content);
    notice.setMsgType(MSG_TYPE_SECURITY);
    notice.setReadFlag(READ_FLAG_UNREAD);
    noticeMapper.insert(notice);

    // 推送失败不影响落库（用户离线属常态），仅告警
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", MSG_TYPE_SECURITY);
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
   * @return {total, items}
   */
  public Map<String, Object> page(long pageNum, long pageSize, Long userId) {
    Page<SysNotice> p = noticeMapper.selectPage(new Page<>(pageNum, pageSize),
        new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getUserId, userId)
            .orderByDesc(SysNotice::getCreateTime));
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
