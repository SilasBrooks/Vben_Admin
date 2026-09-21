package com.vben.service.module.ai.llm;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.client.LlmClient;
import com.vben.service.module.ai.llm.mapper.SysLlmConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 模型配置服务：CRUD + 全局唯一激活 + 连通性测试。
 *
 * <p>安全约定：api_key 明文入库（内网），出参一律经 {@link #maskKey} 脱敏；
 * 编辑时 key 传空表示保持不变；激活中的配置禁止删除。
 */
@Service
@RequiredArgsConstructor
public class SysLlmConfigService {

  private final SysLlmConfigMapper mapper;
  private final LlmClient llmClient;

  /** 列表回显项（api_key 已脱敏） */
  public record Item(Long id, String name, String baseUrl, String apiKey, String model,
                     java.math.BigDecimal temperature, Integer maxTokens, Integer timeoutSeconds,
                     Integer enabled, Integer isActive, String remark, String createTime) {

    static Item from(SysLlmConfig c) {
      return new Item(c.getId(), c.getName(), c.getBaseUrl(), maskKey(c.getApiKey()),
          c.getModel(), c.getTemperature(), c.getMaxTokens(), c.getTimeoutSeconds(),
          c.getEnabled(), c.getIsActive(), c.getRemark(),
          c.getCreateTime() == null ? null : c.getCreateTime().toString().split("\\.")[0]);
    }
  }

  /** 分页：名称模糊过滤；激活模型置顶，其余按创建时间倒序 */
  public IPage<Item> page(long pageNo, long pageSize, String name) {
    QueryWrapper<SysLlmConfig> q = new QueryWrapper<>();
    if (StringUtils.hasText(name)) {
      q.like("name", name);
    }
    q.orderByDesc("is_active").orderByDesc("create_time");
    IPage<SysLlmConfig> page = mapper.selectPage(new Page<>(pageNo, pageSize), q);
    return page.convert(Item::from);
  }

  /** 新增：名称唯一；base_url 去末尾斜杠 */
  public Item create(SysLlmConfig cfg) {
    validate(cfg);
    cfg.setBaseUrl(stripTrailingSlash(cfg.getBaseUrl()));
    cfg.setIsActive(0);
    if (cfg.getApiKey() == null) {
      cfg.setApiKey("");
    }
    requireNameFree(cfg.getName(), null);
    mapper.insert(cfg);
    return Item.from(mapper.selectById(cfg.getId()));
  }

  /** 更新：apiKey 传空=保持不变；激活/启用状态不由本接口直接改（激活走 activate） */
  public Item update(Long id, SysLlmConfig patch) {
    SysLlmConfig exists = requireExists(id);
    validate(patch);
    String baseUrl = stripTrailingSlash(patch.getBaseUrl());
    if (!patch.getName().equals(exists.getName())) {
      requireNameFree(patch.getName(), id);
    }
    exists.setName(patch.getName());
    exists.setBaseUrl(baseUrl);
    exists.setModel(patch.getModel());
    exists.setTemperature(patch.getTemperature());
    exists.setMaxTokens(patch.getMaxTokens());
    exists.setTimeoutSeconds(patch.getTimeoutSeconds() == null ? 60 : patch.getTimeoutSeconds());
    exists.setEnabled(patch.getEnabled() == null ? exists.getEnabled() : patch.getEnabled());
    exists.setRemark(patch.getRemark());
    // key 传空=不修改（回显是脱敏值，客户端无法回传原文）
    if (StringUtils.hasText(patch.getApiKey())) {
      exists.setApiKey(patch.getApiKey());
    }
    mapper.updateById(exists);
    return Item.from(mapper.selectById(id));
  }

  /** 删除：激活中的配置禁止删除 */
  public void delete(Long id) {
    SysLlmConfig exists = requireExists(id);
    if (exists.getIsActive() != null && exists.getIsActive() == 1) {
      throw BizException.badRequest("error.ai.llm.activeDelete");
    }
    mapper.deleteById(id);
  }

  /** 激活为当前全局模型：先清全表标记再置目标行（停用行不可激活） */
  @Transactional
  public void activate(Long id) {
    SysLlmConfig exists = requireExists(id);
    if (exists.getEnabled() == null || exists.getEnabled() != 1) {
      throw BizException.badRequest("error.ai.llm.disabled");
    }
    mapper.update(null, new UpdateWrapper<SysLlmConfig>().set("is_active", 0));
    mapper.update(null, new UpdateWrapper<SysLlmConfig>()
        .set("is_active", 1).eq("id", id));
  }

  /** 连通性测试：真实发一次最小补全请求，返回耗时与模型回复 */
  public Map<String, Object> ping(Long id) {
    SysLlmConfig cfg = requireExists(id);
    LlmEndpoint endpoint = LlmEndpoint.from(cfg);
    LlmClient.PingResult result = llmClient.ping(endpoint);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ok", true);
    data.put("elapsedMs", result.elapsedMs());
    data.put("model", result.model());
    data.put("reply", result.reply());
    return data;
  }

  // ------------------------------------------------------------------

  private SysLlmConfig requireExists(Long id) {
    SysLlmConfig cfg = mapper.selectById(id);
    if (cfg == null) {
      throw BizException.badRequest("error.ai.llm.notFound");
    }
    return cfg;
  }

  private void requireNameFree(String name, Long excludeId) {
    QueryWrapper<SysLlmConfig> q = new QueryWrapper<SysLlmConfig>().eq("name", name);
    if (excludeId != null) {
      q.ne("id", excludeId);
    }
    if (mapper.selectCount(q) > 0) {
      throw BizException.badRequest("error.ai.llm.nameDup", name);
    }
  }

  private void validate(SysLlmConfig cfg) {
    if (!StringUtils.hasText(cfg.getName()) || !StringUtils.hasText(cfg.getBaseUrl())
        || !StringUtils.hasText(cfg.getModel())) {
      throw BizException.badRequest("error.ai.llm.required");
    }
    if (cfg.getName().length() > 64 || cfg.getBaseUrl().length() > 255
        || cfg.getModel().length() > 64) {
      throw BizException.badRequest("error.ai.llm.tooLong");
    }
    if (cfg.getTemperature() != null
        && (cfg.getTemperature().doubleValue() < 0 || cfg.getTemperature().doubleValue() > 2)) {
      throw BizException.badRequest("error.ai.llm.temperatureRange");
    }
    if (cfg.getMaxTokens() != null && (cfg.getMaxTokens() < 1 || cfg.getMaxTokens() > 1_000_000)) {
      throw BizException.badRequest("error.ai.llm.maxTokensRange");
    }
  }

  private String stripTrailingSlash(String url) {
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /**
   * API Key 脱敏：长度大于 10 保留前 5 后 4（如 sk-89****x2ab），其余全打码。
   * 空值原样返回（显示"未配置"由前端处理）。
   */
  public static String maskKey(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    String k = key.trim();
    if (k.length() <= 10) {
      return "*".repeat(k.length());
    }
    return k.substring(0, 5) + "****" + k.substring(k.length() - 4);
  }
}
