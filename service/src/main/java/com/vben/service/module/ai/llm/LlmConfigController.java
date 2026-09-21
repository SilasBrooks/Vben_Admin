package com.vben.service.module.ai.llm;

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
 * LLM 模型配置接口（系统管理-模型配置）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Llm:List      列表</li>
 *   <li>System:Llm:Add       新增</li>
 *   <li>System:Llm:Edit      编辑/连通测试</li>
 *   <li>System:Llm:Delete    删除</li>
 *   <li>System:Llm:Activate  激活切换</li>
 * </ul>
 */
@RestController
@Tag(name = "模型配置", description = "AI 助手底层 LLM 的增删改查、激活切换与连通测试")
@RequestMapping("/system/llm")
@RequiredArgsConstructor
public class LlmConfigController {

  private final SysLlmConfigService configService;

  /** 分页列表：api_key 已脱敏，激活模型置顶 */
  @Operation(summary = "模型配置列表", description = "名称模糊过滤 + 分页；api_key 脱敏回显")
  @RequirePermission("System:Llm:List")
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
  @Operation(summary = "新增模型配置", description = "名称唯一；base_url 为 OpenAI 兼容根地址（不带末尾斜杠）")
  @RequirePermission("System:Llm:Add")
  @RateLimit(name = "llm:save", limit = 20, windowSeconds = 60)
  @Idempotent(name = "llm:add", intervalSeconds = 3)
  @PostMapping
  public R<SysLlmConfigService.Item> add(@RequestBody SysLlmConfig cfg) {
    return R.ok(configService.create(cfg));
  }

  /** 编辑配置：apiKey 传空=保持不变 */
  @Operation(summary = "编辑模型配置", description = "api_key 传空表示保持原值（列表回显为脱敏值，无法回传原文）")
  @RequirePermission("System:Llm:Edit")
  @RateLimit(name = "llm:save", limit = 20, windowSeconds = 60)
  @PutMapping("/{id}")
  public R<SysLlmConfigService.Item> update(@PathVariable Long id, @RequestBody SysLlmConfig cfg) {
    return R.ok(configService.update(id, cfg));
  }

  /** 删除配置（激活中禁止删除） */
  @Operation(summary = "删除模型配置", description = "当前激活的配置不可删除，需先切换其他模型")
  @RequirePermission("System:Llm:Delete")
  @OperLog(module = "模型配置", description = "删除LLM模型配置")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    configService.delete(id);
    return R.ok();
  }

  /** 激活为当前全局模型（AI 助手对话/摘要/计划执行即时切换） */
  @Operation(summary = "激活模型", description = "全局唯一激活；停用状态的配置不可激活；切换即时生效无需重启")
  @RequirePermission("System:Llm:Activate")
  @OperLog(module = "模型配置", description = "切换激活LLM模型")
  @Idempotent(name = "llm:activate", intervalSeconds = 2)
  @PostMapping("/{id}/activate")
  public R<Void> activate(@PathVariable Long id) {
    configService.activate(id);
    return R.ok();
  }

  /** 连通性测试：真实发一次最小补全请求 */
  @Operation(summary = "连通测试", description = "用该配置真实请求一次 max_tokens=1 的补全，返回耗时与模型回复")
  @RequirePermission("System:Llm:Edit")
  @RateLimit(name = "llm:test", limit = 6, windowSeconds = 60)
  @PostMapping("/{id}/test")
  public R<Map<String, Object>> test(@PathVariable Long id) {
    return R.ok(configService.ping(id));
  }
}
