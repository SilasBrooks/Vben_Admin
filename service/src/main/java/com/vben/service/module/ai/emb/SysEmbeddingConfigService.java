package com.vben.service.module.ai.emb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.mapper.SysEmbeddingConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embedding 模型配置服务：CRUD + 全局唯一激活 + 连通测试（实测维度回填）。
 *
 * <p>安全约定与对话模型配置一致：api_key 明文入库（内网），出参一律经
 * {@link #maskKey} 脱敏；编辑时 key 传空表示保持不变；激活中的配置禁止删除。
 */
@Service
@RequiredArgsConstructor
public class SysEmbeddingConfigService {

  private final SysEmbeddingConfigMapper mapper;
  private final EmbeddingClient embeddingClient;

  /** 列表回显项（api_key 已脱敏） */
  public record Item(Long id, String name, String baseUrl, String apiKey, String model,
                     String protocol, Integer dimension, Integer timeoutSeconds, Integer enabled,
                     Integer isActive, String remark, String createTime) {

    static Item from(SysEmbeddingConfig c) {
      return new Item(c.getId(), c.getName(), c.getBaseUrl(), maskKey(c.getApiKey()),
          c.getModel(), normalizeProtocol(c.getProtocol()), c.getDimension(), c.getTimeoutSeconds(),
          c.getEnabled(), c.getIsActive(), c.getRemark(),
          c.getCreateTime() == null ? null : c.getCreateTime().toString().split("\\.")[0]);
    }
  }

  /** 分页：名称模糊过滤；激活配置置顶，其余按创建时间倒序 */
  public IPage<Item> page(long pageNo, long pageSize, String name) {
    QueryWrapper<SysEmbeddingConfig> q = new QueryWrapper<>();
    if (StringUtils.hasText(name)) {
      q.like("name", name);
    }
    q.orderByDesc("is_active").orderByDesc("create_time");
    IPage<SysEmbeddingConfig> page = mapper.selectPage(new Page<>(pageNo, pageSize), q);
    return page.convert(Item::from);
  }

  /** 新增：名称唯一；base_url 去末尾斜杠；协议归一化 */
  public Item create(SysEmbeddingConfig cfg) {
    validate(cfg);
    cfg.setBaseUrl(stripTrailingSlash(cfg.getBaseUrl()));
    cfg.setProtocol(normalizeProtocol(cfg.getProtocol()));
    cfg.setIsActive(0);
    cfg.setDimension(null);
    if (cfg.getApiKey() == null) {
      cfg.setApiKey("");
    }
    requireNameFree(cfg.getName(), null);
    mapper.insert(cfg);
    return Item.from(mapper.selectById(cfg.getId()));
  }

  /** 更新：apiKey 传空=保持不变；维度由连通测试回填，本接口不改 */
  public Item update(Long id, SysEmbeddingConfig patch) {
    SysEmbeddingConfig exists = requireExists(id);
    validate(patch);
    String baseUrl = stripTrailingSlash(patch.getBaseUrl());
    if (!patch.getName().equals(exists.getName())) {
      requireNameFree(patch.getName(), id);
    }
    exists.setName(patch.getName());
    exists.setBaseUrl(baseUrl);
    exists.setModel(patch.getModel());
    exists.setProtocol(normalizeProtocol(patch.getProtocol()));
    exists.setTimeoutSeconds(patch.getTimeoutSeconds() == null ? 60 : patch.getTimeoutSeconds());
    exists.setEnabled(patch.getEnabled() == null ? exists.getEnabled() : patch.getEnabled());
    exists.setRemark(patch.getRemark());
    // key 传空=不修改（回显是脱敏值，客户端无法回传原文）
    if (StringUtils.hasText(patch.getApiKey())) {
      exists.setApiKey(patch.getApiKey());
    }
    // 模型/地址变更后旧维度失效
    exists.setDimension(null);
    mapper.updateById(exists);
    return Item.from(mapper.selectById(id));
  }

  /** 删除：激活中的配置禁止删除 */
  public void delete(Long id) {
    SysEmbeddingConfig exists = requireExists(id);
    if (exists.getIsActive() != null && exists.getIsActive() == 1) {
      throw BizException.badRequest("error.ai.emb.activeDelete");
    }
    mapper.deleteById(id);
  }

  /** 激活为当前全局 Embedding：先清全表标记再置目标行（停用行不可激活） */
  @Transactional
  public void activate(Long id) {
    SysEmbeddingConfig exists = requireExists(id);
    if (exists.getEnabled() == null || exists.getEnabled() != 1) {
      throw BizException.badRequest("error.ai.emb.disabled");
    }
    mapper.update(null, new UpdateWrapper<SysEmbeddingConfig>().set("is_active", 0));
    mapper.update(null, new UpdateWrapper<SysEmbeddingConfig>()
        .set("is_active", 1).eq("id", id));
  }

  /**
   * 连通测试：真实向量化一条样本文本，返回维度与耗时，并把实测维度回填到配置。
   */
  public Map<String, Object> ping(Long id) {
    SysEmbeddingConfig cfg = requireExists(id);
    EmbEndpoint endpoint = EmbEndpoint.from(cfg);
    long start = System.currentTimeMillis();
    float[] vector = embeddingClient.embedOne(endpoint, "连通测试");
    long elapsed = System.currentTimeMillis() - start;

    cfg.setDimension(vector.length);
    mapper.updateById(cfg);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ok", true);
    data.put("elapsedMs", elapsed);
    data.put("dimension", vector.length);
    return data;
  }

  // ------------------------------------------------------------------

  private SysEmbeddingConfig requireExists(Long id) {
    SysEmbeddingConfig cfg = mapper.selectById(id);
    if (cfg == null) {
      throw BizException.badRequest("error.ai.emb.notFound");
    }
    return cfg;
  }

  private void requireNameFree(String name, Long excludeId) {
    QueryWrapper<SysEmbeddingConfig> q = new QueryWrapper<SysEmbeddingConfig>().eq("name", name);
    if (excludeId != null) {
      q.ne("id", excludeId);
    }
    if (mapper.selectCount(q) > 0) {
      throw BizException.badRequest("error.ai.emb.nameDup", name);
    }
  }

  private void validate(SysEmbeddingConfig cfg) {
    if (!StringUtils.hasText(cfg.getName()) || !StringUtils.hasText(cfg.getBaseUrl())
        || !StringUtils.hasText(cfg.getModel())) {
      throw BizException.badRequest("error.ai.emb.required");
    }
    if (cfg.getName().length() > 64 || cfg.getBaseUrl().length() > 255
        || cfg.getModel().length() > 64) {
      throw BizException.badRequest("error.ai.emb.tooLong");
    }
  }

  private String stripTrailingSlash(String url) {
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /** 协议归一化：空值回落 standard，白名单外的值拒绝（配合ping路径区分协议） */
  private static String normalizeProtocol(String protocol) {
    if (protocol == null || protocol.isBlank()) {
      return SysEmbeddingConfig.PROTOCOL_STANDARD;
    }
    String p = protocol.trim();
    if (SysEmbeddingConfig.PROTOCOL_STANDARD.equals(p)
        || SysEmbeddingConfig.PROTOCOL_ARK_MULTIMODAL.equals(p)) {
      return p;
    }
    throw BizException.badRequest("error.ai.emb.protocolInvalid", p);
  }

  /**
   * API Key 脱敏：与对话模型配置同一规则（长度大于 10 保留前 5 后 4，其余全打码）。
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
