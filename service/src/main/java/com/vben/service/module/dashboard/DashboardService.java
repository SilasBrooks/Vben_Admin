package com.vben.service.module.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.module.ai.usage.entity.SysLlmCallLog;
import com.vben.service.module.ai.usage.mapper.SysLlmCallLogMapper;
import com.vben.service.module.monitor.entity.SysLoginLog;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.mapper.SysLoginLogMapper;
import com.vben.service.module.monitor.mapper.SysOperLogMapper;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysDictTypeMapper;
import com.vben.service.module.system.mapper.SysFileMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.OnlineSessionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 仪表盘聚合查询：工作台/分析页共用。
 *
 * <p>全部为聚合统计或非敏感摘要（不含密码、入参原文等明细）。
 * 日志类查询限近 14 天；演示规模数据量小，日期分组在内存完成，
 * 避免引入数据库方言相关的日期函数 SQL。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

  /** 登录趋势/模块分布的统计窗口（天） */
  private static final int TREND_DAYS = 14;

  /** 监控数据块的权限码（明细与衍生统计同码，无权限不下发数据） */
  private static final String PERM_OPER_LOG_LIST = "Monitor:OperLog:List";
  private static final String PERM_LOGIN_LOG_LIST = "Monitor:LoginLog:List";

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysDeptMapper deptMapper;
  private final SysFileMapper fileMapper;
  private final SysDictTypeMapper dictTypeMapper;
  private final SysLoginLogMapper loginLogMapper;
  private final SysOperLogMapper operLogMapper;
  private final SysLlmCallLogMapper llmCallLogMapper;
  private final OnlineSessionService onlineSessionService;

  /** 聚合响应（LinkedHashMap 保序，前端 TS 接口字段一一对应）；监控类数据块按登录用户权限过滤，无权限装配为 null */
  public Map<String, Object> summary() {
    LoginUser user = LoginUserHolder.require();
    boolean canOperLog = user.hasPermission(PERM_OPER_LOG_LIST);
    boolean canLoginLog = user.hasPermission(PERM_LOGIN_LOG_LIST);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("totals", totals(canLoginLog, canOperLog));
    data.put("today", today(canLoginLog, canOperLog));
    data.put("loginTrend", canLoginLog ? loginTrend() : null);
    data.put("deptDistribution", deptDistribution());
    data.put("moduleDistribution", canOperLog ? moduleDistribution() : null);
    data.put("recentLogins", canLoginLog ? recentLogins() : null);
    data.put("recentOpers", canOperLog ? recentOpers() : null);
    data.put("aiUsage", aiUsage());
    return data;
  }

  /** 统计卡：用户/角色/部门/在线/文件/字典类型 总数（全员）；累计登录/操作数受监控权限控制 */
  private Map<String, Object> totals(boolean canLoginLog, boolean canOperLog) {
    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("userCount", userMapper.selectCount(null));
    totals.put("roleCount", roleMapper.selectCount(null));
    totals.put("deptCount", deptMapper.selectCount(null));
    totals.put("onlineCount", onlineSessionService.listAll().size());
    totals.put("fileCount", fileMapper.selectCount(null));
    totals.put("dictTypeCount", dictTypeMapper.selectCount(null));
    // 分析页概览卡需要的累计量（成功+失败全量），无监控权限不下发
    totals.put("loginCount", canLoginLog ? loginLogMapper.selectCount(null) : null);
    totals.put("operCount", canOperLog ? operLogMapper.selectCount(null) : null);
    return totals;
  }

  /** 今日概况：登录成功/失败次数、操作次数（分别受监控权限控制，无权限不下发） */
  private Map<String, Object> today(boolean canLoginLog, boolean canOperLog) {
    Map<String, Object> today = new LinkedHashMap<>();
    if (canLoginLog) {
      LocalDateTime todayStart = LocalDate.now().atStartOfDay();
      today.put("loginSuccess", loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
          .ge(SysLoginLog::getLoginTime, todayStart)
          .eq(SysLoginLog::getStatus, 0)));
      today.put("loginFail", loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
          .ge(SysLoginLog::getLoginTime, todayStart)
          .eq(SysLoginLog::getStatus, 1)));
    } else {
      today.put("loginSuccess", null);
      today.put("loginFail", null);
    }
    if (canOperLog) {
      LocalDateTime todayStart = LocalDate.now().atStartOfDay();
      today.put("operCount", operLogMapper.selectCount(new LambdaQueryWrapper<SysOperLog>()
          .ge(SysOperLog::getOperTime, todayStart)));
    } else {
      today.put("operCount", null);
    }
    return today;
  }

  /** 近 14 天每日登录成功/失败次数（日期升序，缺数据日补 0） */
  private List<Map<String, Object>> loginTrend() {
    LocalDate today = LocalDate.now();
    LocalDateTime start = today.minusDays(TREND_DAYS - 1).atStartOfDay();
    List<SysLoginLog> logs = loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>()
        .select(SysLoginLog::getLoginTime, SysLoginLog::getStatus)
        .ge(SysLoginLog::getLoginTime, start));

    Map<LocalDate, long[]> byDay = new HashMap<>();
    for (SysLoginLog log : logs) {
      if (log.getLoginTime() == null) {
        continue;
      }
      long[] counts = byDay.computeIfAbsent(log.getLoginTime().toLocalDate(),
          k -> new long[2]);
      boolean success = log.getStatus() != null && log.getStatus() == 0;
      counts[success ? 0 : 1]++;
    }

    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = 0; i < TREND_DAYS; i++) {
      LocalDate day = today.minusDays(TREND_DAYS - 1 - i);
      long[] counts = byDay.getOrDefault(day, new long[2]);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", day.toString());
      item.put("success", counts[0]);
      item.put("fail", counts[1]);
      trend.add(item);
    }
    return trend;
  }

  /** 部门人数分布：sys_user 按 dept_id 分组后回填部门名（按人数降序） */
  private List<Map<String, Object>> deptDistribution() {
    List<Map<String, Object>> rows = userMapper.selectMaps(
        new QueryWrapper<SysUser>().select("dept_id", "count(*) as cnt").groupBy("dept_id"));
    Map<Long, String> deptNames = deptMapper.selectList(null).stream()
        .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Object deptId = row.get("dept_id");
      String name = deptId == null ? "未分配部门"
          : deptNames.getOrDefault(((Number) deptId).longValue(), "未知部门");
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", name);
      item.put("value", ((Number) row.get("cnt")).longValue());
      result.add(item);
    }
    result.sort((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")));
    return result;
  }

  /** 近 14 天操作日志按模块聚合 Top5（按次数降序） */
  private List<Map<String, Object>> moduleDistribution() {
    LocalDateTime start = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
    List<Map<String, Object>> rows = operLogMapper.selectMaps(
        new QueryWrapper<SysOperLog>()
            .select("module", "count(*) as cnt")
            .ge("oper_time", start)
            .isNotNull("module")
            .groupBy("module"));

    return rows.stream()
        .map(row -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("name", String.valueOf(row.get("module")));
          item.put("value", ((Number) row.get("cnt")).longValue());
          return item;
        })
        .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("value"))
            .reversed())
        .limit(5)
        .collect(Collectors.toList());
  }

  /** 最近 8 条登录记录（用户名/IP/时间/成败，按时间倒序） */
  private List<Map<String, Object>> recentLogins() {
    List<SysLoginLog> logs = loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>()
        .select(SysLoginLog::getUsername, SysLoginLog::getStatus,
            SysLoginLog::getIp, SysLoginLog::getLoginTime)
        .orderByDesc(SysLoginLog::getId)
        .last("LIMIT 8"));
    List<Map<String, Object>> result = new ArrayList<>();
    for (SysLoginLog log : logs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("username", log.getUsername());
      item.put("status", log.getStatus());
      item.put("ip", log.getIp());
      item.put("loginTime", log.getLoginTime());
      result.add(item);
    }
    return result;
  }

  /** 最近 8 条操作记录（操作人/模块/动作/耗时/成败，按时间倒序） */
  private List<Map<String, Object>> recentOpers() {
    List<SysOperLog> logs = operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
        .select(SysOperLog::getOperName, SysOperLog::getModule, SysOperLog::getDescription,
            SysOperLog::getStatus, SysOperLog::getCostMs, SysOperLog::getOperTime)
        .orderByDesc(SysOperLog::getId)
        .last("LIMIT 8"));
    List<Map<String, Object>> result = new ArrayList<>();
    for (SysOperLog log : logs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("operName", log.getOperName());
      item.put("module", log.getModule());
      item.put("description", log.getDescription());
      item.put("status", log.getStatus());
      item.put("costMs", log.getCostMs());
      item.put("operTime", log.getOperTime());
      result.add(item);
    }
    return result;
  }

  /** AI 用量统计：累计/今日/本月 token 与调用次数、近 14 天趋势、近 30 天模型 token Top5 */
  private Map<String, Object> aiUsage() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    LocalDateTime trendStart = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
    LocalDateTime modelStart = LocalDate.now().minusDays(29).atStartOfDay();

    // 聚合口径：tokens 为 NULL 的记录计入调用次数、不计入 token 合计（sum 忽略 NULL）
    Map<String, Object> totalRow = llmCallLogMapper.selectMaps(new QueryWrapper<SysLlmCallLog>()
        .select("count(*) as calls", "coalesce(sum(total_tokens),0) as tokens")).get(0);
    Map<String, Object> todayRow = llmCallLogMapper.selectMaps(new QueryWrapper<SysLlmCallLog>()
        .select("count(*) as calls", "coalesce(sum(total_tokens),0) as tokens")
        .ge("create_time", todayStart)).get(0);
    Map<String, Object> monthRow = llmCallLogMapper.selectMaps(new QueryWrapper<SysLlmCallLog>()
        .select("count(*) as calls", "coalesce(sum(total_tokens),0) as tokens")
        .ge("create_time", monthStart)).get(0);

    // 近 14 天趋势：内存按天分组（与 loginTrend 同风格，避免日期方言 SQL）
    List<SysLlmCallLog> logs = llmCallLogMapper.selectList(new LambdaQueryWrapper<SysLlmCallLog>()
        .select(SysLlmCallLog::getCreateTime, SysLlmCallLog::getTotalTokens)
        .ge(SysLlmCallLog::getCreateTime, trendStart));
    // long[0]=token 合计，long[1]=调用次数
    Map<LocalDate, long[]> byDay = new HashMap<>();
    for (SysLlmCallLog log : logs) {
      if (log.getCreateTime() == null) {
        continue;
      }
      long[] arr = byDay.computeIfAbsent(log.getCreateTime().toLocalDate(), k -> new long[2]);
      arr[1]++;
      if (log.getTotalTokens() != null) {
        arr[0] += log.getTotalTokens();
      }
    }
    List<Map<String, Object>> tokenTrend = new ArrayList<>();
    for (int i = 0; i < TREND_DAYS; i++) {
      LocalDate day = LocalDate.now().minusDays(TREND_DAYS - 1 - i);
      long[] arr = byDay.getOrDefault(day, new long[2]);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", day.toString());
      item.put("tokens", arr[0]);
      item.put("calls", arr[1]);
      tokenTrend.add(item);
    }

    // 近 30 天按模型聚合（按 token 降序取 Top5）
    List<Map<String, Object>> modelRows = llmCallLogMapper.selectMaps(new QueryWrapper<SysLlmCallLog>()
        .select("model", "count(*) as calls", "coalesce(sum(total_tokens),0) as tokens")
        .ge("create_time", modelStart)
        .groupBy("model"));
    List<Map<String, Object>> modelDistribution = modelRows.stream()
        .map(row -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("name", String.valueOf(row.get("model")));
          item.put("tokens", ((Number) row.get("tokens")).longValue());
          item.put("calls", ((Number) row.get("calls")).longValue());
          return item;
        })
        .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("tokens"))
            .reversed())
        .limit(5)
        .collect(Collectors.toList());

    Map<String, Object> aiUsage = new LinkedHashMap<>();
    aiUsage.put("totalTokens", ((Number) totalRow.get("tokens")).longValue());
    aiUsage.put("totalCalls", ((Number) totalRow.get("calls")).longValue());
    aiUsage.put("todayTokens", ((Number) todayRow.get("tokens")).longValue());
    aiUsage.put("todayCalls", ((Number) todayRow.get("calls")).longValue());
    aiUsage.put("monthTokens", ((Number) monthRow.get("tokens")).longValue());
    aiUsage.put("monthCalls", ((Number) monthRow.get("calls")).longValue());
    aiUsage.put("tokenTrend", tokenTrend);
    aiUsage.put("modelDistribution", modelDistribution);
    return aiUsage;
  }
}
