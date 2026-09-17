package com.vben.service.module.dashboard;

import com.vben.service.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘接口。
 *
 * <p>工作台/分析页共用的聚合统计（全员同版）：登录即可访问，
 * 不挂权限码，仅返回聚合数据与非敏感摘要。
 */
@RestController
@Tag(name = "仪表盘", description = "工作台/分析页聚合统计（登录即可访问）")
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  /** 仪表盘聚合数据：totals / today / loginTrend / deptDistribution / moduleDistribution / recentLogins / recentOpers */
  @Operation(summary = "仪表盘聚合数据", description = "一次返回统计卡、今日概况、近 14 天趋势、分布与最近记录")
  @GetMapping("/summary")
  public R<Map<String, Object>> summary() {
    return R.ok(dashboardService.summary());
  }
}
