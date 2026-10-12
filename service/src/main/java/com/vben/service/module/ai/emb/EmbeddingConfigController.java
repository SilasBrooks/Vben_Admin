package com.vben.service.module.ai.emb;

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
 * Embedding 模型配置接口（系统管理-嵌入配置）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Emb:List      列表</li>
 *   <li>System:Emb:Add       新增</li>
 *   <li>System:Emb:Edit      编辑/连通测试</li>
 *   <li>System:Emb:Delete    删除</li>
 *   <li>System:Emb:Activate  激活切换</li>
 * </ul>
 */
@RestController
@Tag(name = "嵌入配置", description = "知识库向量化所用 Embedding 模型的增删改查、激活切换与连通测试")
@RequestMapping("/system/embedding")
@RequiredArgsConstructor
public class EmbeddingConfigController {

  private final SysEmbeddingConfigService configService;

  /** 分页列表：api_key 已脱敏，激活配置置顶 */
  @Operation(summary = "嵌入配置列表", description = "名称模糊过滤 + 分页；api_key 脱敏回显")
  @RequirePermission("System:Emb:List")
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
  @Operation(summary = "新增嵌入配置", description = "名称唯一；base_url 为 OpenAI 兼容根地址（不带末尾斜杠）")
  @RequirePermission("System:Emb:Add")
  @RateLimit(name = "emb:save", limit = 20, windowSeconds = 60)
  @Idempotent(name = "emb:add", intervalSeconds = 3)
  @PostMapping
  public R<SysEmbeddingConfigService.Item> add(@RequestBody SysEmbeddingConfig cfg) {
    return R.ok(configService.create(cfg));
  }

  /** 编辑配置：apiKey 传空=保持不变 */
  @Operation(summary = "编辑嵌入配置", description = "api_key 传空表示保持原值；模型/地址变更后维度失效待重新测试")
  @RequirePermission("System:Emb:Edit")
  @RateLimit(name = "emb:save", limit = 20, windowSeconds = 60)
  @PutMapping("/{id}")
  public R<SysEmbeddingConfigService.Item> update(@PathVariable Long id,
                                                  @RequestBody SysEmbeddingConfig cfg) {
    return R.ok(configService.update(id, cfg));
  }

  /** 删除配置（激活中禁止删除） */
  @Operation(summary = "删除嵌入配置", description = "当前激活的配置不可删除，需先切换其他配置")
  @RequirePermission("System:Emb:Delete")
  @OperLog(module = "嵌入配置", description = "删除Embedding配置")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    configService.delete(id);
    return R.ok();
  }

  /** 激活为当前全局 Embedding（知识库向量化/检索即时切换） */
  @Operation(summary = "激活嵌入配置", description = "全局唯一激活；停用状态的配置不可激活；切换即时生效无需重启")
  @RequirePermission("System:Emb:Activate")
  @OperLog(module = "嵌入配置", description = "切换激活Embedding配置")
  @Idempotent(name = "emb:activate", intervalSeconds = 2)
  @PostMapping("/{id}/activate")
  public R<Void> activate(@PathVariable Long id) {
    configService.activate(id);
    return R.ok();
  }

  /** 连通测试：真实向量化一条样本文本，返回维度与耗时并回填维度 */
  @Operation(summary = "连通测试", description = "用该配置真实向量化一条样本文本，返回维度与耗时，维度实测回填")
  @RequirePermission("System:Emb:Edit")
  @RateLimit(name = "emb:test", limit = 6, windowSeconds = 60)
  @PostMapping("/{id}/test")
  public R<Map<String, Object>> test(@PathVariable Long id) {
    return R.ok(configService.ping(id));
  }
}
