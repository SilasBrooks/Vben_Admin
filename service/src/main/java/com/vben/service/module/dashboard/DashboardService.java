package com.vben.service.module.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysDeptMapper deptMapper;
  private final SysFileMapper fileMapper;
  private final SysDictTypeMapper dictTypeMapper;
  private final SysLoginLogMapper loginLogMapper;
  private final SysOperLogMapper operLogMapper;
  private final OnlineSessionService onlineSessionService;

  /** 聚合响应（LinkedHashMap 保序，前端 TS 接口字段一一对应） */
  public Map<String, Object> summary() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("totals", totals());
    data.put("today", today());
    data.put("loginTrend", loginTrend());
    data.put("deptDistribution", deptDistribution());
    data.put("moduleDistribution", moduleDistribution());
    data.put("recentLogins", recentLogins());
    data.put("recentOpers", recentOpers());
    return data;
  }

  /** 统计卡：用户/角色/部门/在线/文件/字典类型 总数 */
  private Map<String, Object> totals() {
    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("userCount", userMapper.selectCount(null));
    totals.put("roleCount", roleMapper.selectCount(null));
    totals.put("deptCount", deptMapper.selectCount(null));
    totals.put("onlineCount", onlineSessionService.listAll().size());
    totals.put("fileCount", fileMapper.selectCount(null));
    totals.put("dictTypeCount", dictTypeMapper.selectCount(null));
    // 分析页概览卡需要的累计量（成功+失败全量）
    totals.put("loginCount", loginLogMapper.selectCount(null));
    totals.put("operCount", operLogMapper.selectCount(null));
    return totals;
  }

  /** 今日概况：登录成功/失败次数、操作次数 */
  private Map<String, Object> today() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    Map<String, Object> today = new LinkedHashMap<>();
    today.put("loginSuccess", loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
        .ge(SysLoginLog::getLoginTime, todayStart)
        .eq(SysLoginLog::getStatus, 0)));
    today.put("loginFail", loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
        .ge(SysLoginLog::getLoginTime, todayStart)
        .eq(SysLoginLog::getStatus, 1)));
    today.put("operCount", operLogMapper.selectCount(new LambdaQueryWrapper<SysOperLog>()
        .ge(SysOperLog::getOperTime, todayStart)));
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
}
