package com.vben.service.module.monitor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.R;
import com.vben.service.module.monitor.entity.SysLoginLog;
import com.vben.service.module.monitor.service.MonitorLoginLogService;
import com.vben.service.security.RequirePermission;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志接口。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>Monitor:LoginLog:List    列表</li>
 *   <li>Monitor:LoginLog:Delete  删除/清空</li>
 * </ul>
 */
@RestController
@Tag(name = "登录日志", description = "登录日志查询")
@RequestMapping("/monitor/login-log")
@RequiredArgsConstructor
public class MonitorLoginLogController {

  private final MonitorLoginLogService loginLogService;

  /** 登录日志分页列表（用户名模糊 + 状态 + 时间范围） */
  @RequirePermission("Monitor:LoginLog:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate beginTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate endTime) {

    IPage<SysLoginLog> page =
        loginLogService.page(pageNo, pageSize, username, status, beginTime, endTime);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 删除单条 */
  @RequirePermission("Monitor:LoginLog:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    loginLogService.remove(id);
    return R.ok();
  }

  /** 清空全部 */
  @RequirePermission("Monitor:LoginLog:Delete")
  @DeleteMapping("/clear")
  public R<Void> clear() {
    loginLogService.clear();
    return R.ok();
  }
}
