package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import com.vben.service.module.dashboard.DashboardService;
import com.vben.service.module.monitor.entity.SysLoginLog;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.service.MonitorLoginLogService;
import com.vben.service.module.monitor.service.MonitorOperLogService;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.OnlineSessionService;
import com.vben.service.security.TokenVersionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 监控与仪表盘类 AI 工具：在线用户查看/强退、登录与操作日志查询、仪表盘统计。
 * 强退为高危操作（danger=true）。
 */
@Component
@RequiredArgsConstructor
public class AiMonitorTools {

  private static final int LOG_LIMIT = 10;

  private final OnlineSessionService onlineSessionService;
  private final TokenVersionService tokenVersionService;
  private final NoticeService noticeService;
  private final MonitorLoginLogService loginLogService;
  private final MonitorOperLogService operLogService;
  private final DashboardService dashboardService;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;

  @AiAgentTool(name = "query_online_users", title = "查询在线用户", kind = AiToolKind.QUERY,
      permission = "Monitor:Online:List",
      description = "查询当前在线（近 30 分钟活跃）的用户会话。结果包含用户 id、用户名、昵称、登录时间、IP。"
          + "用户问\"现在谁在线/有没有人在线\"时使用。")
  public AiToolResult queryOnlineUsers() {
    List<Map<String, Object>> items = new ArrayList<>();
    for (OnlineSessionService.OnlineUserView u : onlineSessionService.listAll()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("userId", u.userId());
      m.put("username", u.username());
      m.put("nickname", u.nickname());
      m.put("loginTime", u.loginTime());
      m.put("ip", u.ip());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", items.size(), "users", items)));
  }

  @AiAgentTool(name = "kick_user", title = "强制下线用户", kind = AiToolKind.WRITE,
      permission = "Monitor:Online:Kick", danger = true,
      description = "按登录用户名强制某用户立即下线（高危）：其已签发的 token 立即失效并从在线列表移除，"
          + "同时收到系统通知。不能对操作者自己执行。执行前必须与用户确认用户名。")
  public AiToolResult kickUser(
      @AiToolParam(value = "要强制下线的登录用户名（精确）", required = true) String username) {
    SysUser target = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, username.trim()));
    if (target == null) {
      throw BizException.badRequest("error.ai.user.notFound", username);
    }
    LoginUser me = LoginUserHolder.require();
    if (target.getId().equals(me.getUserId())) {
      throw BizException.badRequest("error.online.selfKick");
    }
    tokenVersionService.bump(target.getId());
    onlineSessionService.remove(target.getId());
    noticeService.send(target.getId(), "会话已被管理员强制下线", "如有疑问请联系管理员。");
    String summary = "已强制用户「" + username + "」下线，其现有登录已立即失效";
    return AiToolResult.created(
        json(Map.of("userId", target.getId(), "username", username)), summary);
  }

  @AiAgentTool(name = "query_login_logs", title = "查询登录日志", kind = AiToolKind.QUERY,
      permission = "Monitor:LoginLog:List",
      description = "查询最近 10 条登录日志，可按用户名模糊过滤。结果包含用户名、是否成功、消息、IP、登录时间。"
          + "用户问\"谁登录过/登录失败记录/最近登录情况\"时使用。")
  public AiToolResult queryLoginLogs(
      @AiToolParam("用户名关键字，可省略") String username) {
    IPage<SysLoginLog> page = loginLogService.page(1, LOG_LIMIT, username, null, null, null);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysLoginLog l : page.getRecords()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("username", l.getUsername());
      m.put("success", l.getStatus() != null && l.getStatus() == 0);
      m.put("message", l.getMessage());
      m.put("ip", l.getIp());
      m.put("loginTime", l.getLoginTime());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", page.getTotal(), "logs", items)));
  }

  @AiAgentTool(name = "query_oper_logs", title = "查询操作日志", kind = AiToolKind.QUERY,
      permission = "Monitor:OperLog:List",
      description = "查询最近 10 条操作日志，可按操作人用户名模糊过滤。结果包含操作人、模块、操作说明、"
          + "请求方式、是否成功、耗时毫秒、IP、操作时间。用户问\"谁做了什么操作/操作记录\"时使用。")
  public AiToolResult queryOperLogs(
      @AiToolParam("操作人用户名关键字，可省略") String operName) {
    IPage<SysOperLog> page = operLogService.page(1, LOG_LIMIT, operName, null, null, null);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysOperLog l : page.getRecords()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("operName", l.getOperName());
      m.put("module", l.getModule());
      m.put("description", l.getDescription());
      m.put("requestMethod", l.getRequestMethod());
      m.put("success", l.getStatus() != null && l.getStatus() == 0);
      m.put("costMs", l.getCostMs());
      m.put("ip", l.getIp());
      m.put("operTime", l.getOperTime());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", page.getTotal(), "logs", items)));
  }

  @AiAgentTool(name = "query_dashboard", title = "查询系统概况统计", kind = AiToolKind.QUERY,
      description = "查询系统整体概况：用户/角色/部门/在线/文件等总数、今日新增与登录、近 14 天登录趋势、"
          + "部门分布、模块分布、最近登录与操作记录。用户问\"系统概况/统计数据/有多少用户/今天多少人登录\"时使用。")
  public AiToolResult queryDashboard() {
    return AiToolResult.success(json(dashboardService.summary()));
  }

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
