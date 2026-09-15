package com.vben.service.module.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.LogExecutor;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 操作日志：异步写入 + 分页查询 + 删除/清空
 */
@Service
@RequiredArgsConstructor
public class MonitorOperLogService {

  private final SysOperLogMapper operLogMapper;
  private final LogExecutor logExecutor;

  /** 异步落库（由切面调用，绝不抛错） */
  public void recordAsync(SysOperLog logEntity) {
    logExecutor.submit(() -> operLogMapper.insert(logEntity));
  }

  /** 分页查询：操作人模糊 + 状态 + 时间范围 */
  public IPage<SysOperLog> page(long pageNo, long pageSize, String operName,
      Integer status, LocalDate beginTime, LocalDate endTime) {
    QueryWrapper<SysOperLog> q = new QueryWrapper<>();
    if (operName != null && !operName.isBlank()) {
      q.like("oper_name", operName);
    }
    if (status != null) {
      q.eq("status", status);
    }
    if (beginTime != null) {
      q.ge("oper_time", beginTime.atStartOfDay());
    }
    if (endTime != null) {
      q.le("oper_time", endTime.atTime(23, 59, 59));
    }
    q.orderByDesc("oper_time");
    return operLogMapper.selectPage(new Page<>(pageNo, pageSize), q);
  }

  public void remove(Long id) {
    operLogMapper.deleteById(id);
  }

  /** 清空全部（自增 id 不重置，H2 重建库即归零） */
  public void clear() {
    operLogMapper.delete(null);
  }

  /** 截断到列长上限 */
  public static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
