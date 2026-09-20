package com.vben.service.module.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.common.R;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.OnlineSessionService;
import com.vben.service.security.RequirePermission;
import com.vben.service.security.TokenVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户接口。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>Monitor:Online:List  列表</li>
 *   <li>Monitor:Online:Kick  强制下线</li>
 * </ul>
 */
@RestController
@Tag(name = "在线用户", description = "在线会话查看与强制下线")
@RequestMapping("/monitor/online")
@RequiredArgsConstructor
public class MonitorOnlineController {

  private final OnlineSessionService onlineSessionService;
  private final TokenVersionService tokenVersionService;
  private final SysUserMapper userMapper;
  private final NoticeService noticeService;

  /** 在线用户列表（Redis 实时会话，用户名模糊 + 手动分页，按登录时间倒序） */
  @Operation(summary = "在线用户列表", description = "来源于 Redis 在线会话；支持用户名模糊过滤与分页")
  @RequirePermission("Monitor:Online:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String username) {

    List<OnlineSessionService.OnlineUserView> sessions = onlineSessionService.listAll();
    // 幽灵会话自愈：用户已不存在（重置种子库/删用户后 Redis 残留 key，TTL 比数据活得久）
    // → 从列表剔除并顺带删除残留会话，避免同账号出现多条记录
    Set<Long> existingIds = userMapper.selectObjs(
            new LambdaQueryWrapper<SysUser>().select(SysUser::getId)).stream()
        .map(id -> (Long) id)
        .collect(Collectors.toSet());
    sessions.stream()
        .filter(u -> !existingIds.contains(u.userId()))
        .forEach(u -> onlineSessionService.remove(u.userId()));

    List<OnlineSessionService.OnlineUserView> all = sessions.stream()
        .filter(u -> existingIds.contains(u.userId()))
        .filter(u -> username == null || username.isBlank()
            || (u.username() != null && u.username().contains(username)))
        .sorted(Comparator.comparing(OnlineSessionService.OnlineUserView::loginTime,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();

    long total = all.size();
    long from = Math.min((pageNo - 1) * pageSize, total);
    long to = Math.min(from + pageSize, total);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", all.subList((int) from, (int) to));
    data.put("total", total);
    return R.ok(data);
  }

  /** 强制下线：版本 +1（旧 token 立即失效）并移除在线会话 */
  @Operation(summary = "强制下线", description = "目标用户已签发 token 立即失效并从在线列表移除；禁止对自己执行")
  @RequirePermission("Monitor:Online:Kick")
  @PostMapping("/{userId}/kick")
  public R<Void> kick(@PathVariable Long userId) {
    LoginUser current = LoginUserHolder.require();
    if (userId.equals(current.getUserId())) {
      throw BizException.badRequest("error.online.selfKick");
    }
    tokenVersionService.bump(userId);
    // 强退成功后通知当事人（落库 + WebSocket 推送，推送失败不影响结果）
    noticeService.send(userId, "会话已被管理员强制下线", "如有疑问请联系管理员。");
    return R.ok();
  }
}
