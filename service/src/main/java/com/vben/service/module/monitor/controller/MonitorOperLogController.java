package com.vben.service.module.monitor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.R;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.service.MonitorOperLogService;
import com.vben.service.security.RequirePermission;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>Monitor:OperLog:List    列表</li>
 *   <li>Monitor:OperLog:Delete  删除/清空</li>
 * </ul>
 */
@RestController
@RequestMapping("/monitor/oper-log")
@RequiredArgsConstructor
public class MonitorOperLogController {

  private final MonitorOperLogService operLogService;

  /** 操作日志分页列表（操作人模糊 + 状态 + 时间范围） */
  @RequirePermission("Monitor:OperLog:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String operName,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate beginTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate endTime) {

    IPage<SysOperLog> page =
        operLogService.page(pageNo, pageSize, operName, status, beginTime, endTime);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 删除单条 */
  @RequirePermission("Monitor:OperLog:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    operLogService.remove(id);
    return R.ok();
  }

  /** 清空全部 */
  @RequirePermission("Monitor:OperLog:Delete")
  @DeleteMapping("/clear")
  public R<Void> clear() {
    operLogService.clear();
    return R.ok();
  }
}
