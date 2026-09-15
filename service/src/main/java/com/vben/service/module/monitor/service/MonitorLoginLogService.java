package com.vben.service.module.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.IpUtil;
import com.vben.service.common.LogExecutor;
import com.vben.service.module.monitor.entity.SysLoginLog;
import com.vben.service.module.monitor.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 登录日志：每次登录尝试（成功/失败）异步记录
 */
@Service
@RequiredArgsConstructor
public class MonitorLoginLogService {

  private final SysLoginLogMapper loginLogMapper;
  private final LogExecutor logExecutor;

  /**
   * 记录一次登录尝试（异步，绝不抛错影响登录流程）
   *
   * @param username 尝试的用户名（原文，用户可能不存在）
   * @param success  是否成功
   * @param message  结果消息
   * @param request  当前请求（取 IP / UA，可为 null）
   */
  public void record(String username, boolean success, String message,
      HttpServletRequest request) {
    SysLoginLog logEntity = new SysLoginLog();
    logEntity.setUsername(username);
    logEntity.setStatus(success ? 0 : 1);
    logEntity.setMessage(MonitorOperLogService.truncate(message, 255));
    if (request != null) {
      logEntity.setIp(IpUtil.getClientIp(request));
      logEntity.setUserAgent(
          MonitorOperLogService.truncate(request.getHeader("User-Agent"), 512));
    }
    logEntity.setLoginTime(LocalDateTime.now());
    logExecutor.submit(() -> loginLogMapper.insert(logEntity));
  }

  /** 分页查询：用户名模糊 + 状态 + 时间范围 */
  public IPage<SysLoginLog> page(long pageNo, long pageSize, String username,
      Integer status, LocalDate beginTime, LocalDate endTime) {
    QueryWrapper<SysLoginLog> q = new QueryWrapper<>();
    if (username != null && !username.isBlank()) {
      q.like("username", username);
    }
    if (status != null) {
      q.eq("status", status);
    }
    if (beginTime != null) {
      q.ge("login_time", beginTime.atStartOfDay());
    }
    if (endTime != null) {
      q.le("login_time", endTime.atTime(23, 59, 59));
    }
    q.orderByDesc("login_time");
    return loginLogMapper.selectPage(new Page<>(pageNo, pageSize), q);
  }

  public void remove(Long id) {
    loginLogMapper.deleteById(id);
  }

  public void clear() {
    loginLogMapper.delete(null);
  }
}
