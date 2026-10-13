package com.vben.service.module.ai.rerank;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rerank 精排模型配置接口（系统管理-精排配置）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Rerank:List      列表</li>
 *   <li>System:Rerank:Add       新增</li>
 *   <li>System:Rerank:Edit      编辑/连通测试</li>
 *   <li>System:Rerank:Delete    删除</li>
 *   <li>System:Rerank:Activate  激活切换</li>
 * </ul>
 */
@RestController
@Tag(name = "精排配置", description = "知识库检索精排所用 Rerank 模型的增删改查、激活切换与连通测试")
@RequestMapping("/system/rerank")
@RequiredArgsConstructor
public class RerankConfigController {

  private final SysRerankConfigService configService;

  /** 分页列表：api_key 已脱敏，激活配置置顶 */
  @Operation(summary = "精排配置列表", description = "名称模糊过滤 + 分页；api_key 脱敏回显")
  @RequirePermission("System:Rerank:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String name) {
    var page = configService.page(pageNo, pageSize, name);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 新增配置 */
  @Operation(summary = "新增精排配置", description = "名称唯一；base_url 为 OpenAI 兼容根地址（不带末尾斜杠）")
  @RequirePermission("System:Rerank:Add")
  @RateLimit(name = "rerank:save", limit = 20, windowSeconds = 60)
  @Idempotent(name = "rerank:add", intervalSeconds = 3)
  @PostMapping
  public R<SysRerankConfigService.Item> add(@RequestBody SysRerankConfig cfg) {
    return R.ok(configService.create(cfg));
  }

  /** 编辑配置：apiKey 传空=保持不变 */
  @Operation(summary = "编辑精排配置", description = "api_key 传空表示保持原值")
  @RequirePermission("System:Rerank:Edit")
  @RateLimit(name = "rerank:save", limit = 20, windowSeconds = 60)
  @PutMapping("/{id}")
  public R<SysRerankConfigService.Item> update(@PathVariable Long id,
                                               @RequestBody SysRerankConfig cfg) {
    return R.ok(configService.update(id, cfg));
  }

  /** 删除配置（激活中禁止删除） */
  @Operation(summary = "删除精排配置", description = "当前激活的配置不可删除，需先切换其他配置")
  @RequirePermission("System:Rerank:Delete")
  @OperLog(module = "精排配置", description = "删除Rerank配置")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    configService.delete(id);
    return R.ok();
  }

  /** 激活为当前全局 Rerank（知识库检索精排即时切换） */
  @Operation(summary = "激活精排配置", description = "全局唯一激活；停用状态的配置不可激活；切换即时生效无需重启")
  @RequirePermission("System:Rerank:Activate")
  @OperLog(module = "精排配置", description = "切换激活Rerank配置")
  @Idempotent(name = "rerank:activate", intervalSeconds = 2)
  @PostMapping("/{id}/activate")
  public R<Void> activate(@PathVariable Long id) {
    configService.activate(id);
    return R.ok();
  }

  /** 连通测试：真实精排一组样例文本，返回耗时与 Top-1 相关性得分 */
  @Operation(summary = "连通测试", description = "用该配置真实精排一组样例文本，返回耗时与 Top-1 相关性得分")
  @RequirePermission("System:Rerank:Edit")
  @RateLimit(name = "rerank:test", limit = 6, windowSeconds = 60)
  @PostMapping("/{id}/test")
  public R<Map<String, Object>> test(@PathVariable Long id) {
    return R.ok(configService.ping(id));
  }
}
