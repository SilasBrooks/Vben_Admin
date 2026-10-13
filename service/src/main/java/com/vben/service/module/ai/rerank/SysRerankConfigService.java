package com.vben.service.module.ai.rerank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.rerank.mapper.SysRerankConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rerank 精排模型配置服务：CRUD + 全局唯一激活 + 连通测试（真实精排一次）。
 *
 * <p>安全约定与嵌入配置一致：api_key 明文入库（内网），出参一律经
 * {@link #maskKey} 脱敏；编辑时 key 传空表示保持不变；激活中的配置禁止删除。
 */
@Service
@RequiredArgsConstructor
public class SysRerankConfigService {

  private final SysRerankConfigMapper mapper;
  private final RerankClient rerankClient;

  /** 列表回显项（api_key 已脱敏） */
  public record Item(Long id, String name, String baseUrl, String apiKey, String model,
                     Integer timeoutSeconds, Integer enabled, Integer isActive,
                     String remark, String createTime) {

    static Item from(SysRerankConfig c) {
      return new Item(c.getId(), c.getName(), c.getBaseUrl(), maskKey(c.getApiKey()),
          c.getModel(), c.getTimeoutSeconds(), c.getEnabled(), c.getIsActive(),
          c.getRemark(),
          c.getCreateTime() == null ? null : c.getCreateTime().toString().split("\\.")[0]);
    }
  }

  /** 分页：名称模糊过滤；激活配置置顶，其余按创建时间倒序 */
  public IPage<Item> page(long pageNo, long pageSize, String name) {
    QueryWrapper<SysRerankConfig> q = new QueryWrapper<>();
    if (StringUtils.hasText(name)) {
      q.like("name", name);
    }
    q.orderByDesc("is_active").orderByDesc("create_time");
    IPage<SysRerankConfig> page = mapper.selectPage(new Page<>(pageNo, pageSize), q);
    return page.convert(Item::from);
  }

  /** 新增：名称唯一；base_url 去末尾斜杠 */
  public Item create(SysRerankConfig cfg) {
    validate(cfg);
    cfg.setBaseUrl(stripTrailingSlash(cfg.getBaseUrl()));
    cfg.setIsActive(0);
    if (cfg.getApiKey() == null) {
      cfg.setApiKey("");
    }
    if (cfg.getTimeoutSeconds() == null) {
      cfg.setTimeoutSeconds(30);
    }
    requireNameFree(cfg.getName(), null);
    mapper.insert(cfg);
    return Item.from(mapper.selectById(cfg.getId()));
  }

  /** 更新：apiKey 传空=保持不变 */
  public Item update(Long id, SysRerankConfig patch) {
    SysRerankConfig exists = requireExists(id);
    validate(patch);
    String baseUrl = stripTrailingSlash(patch.getBaseUrl());
    if (!patch.getName().equals(exists.getName())) {
      requireNameFree(patch.getName(), id);
    }
    exists.setName(patch.getName());
    exists.setBaseUrl(baseUrl);
    exists.setModel(patch.getModel());
    exists.setTimeoutSeconds(patch.getTimeoutSeconds() == null ? 30 : patch.getTimeoutSeconds());
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
    SysRerankConfig exists = requireExists(id);
    if (exists.getIsActive() != null && exists.getIsActive() == 1) {
      throw BizException.badRequest("error.ai.rerank.activeDelete");
    }
    mapper.deleteById(id);
  }

  /** 激活为当前全局 Rerank：先清全表标记再置目标行（停用行不可激活） */
  @Transactional
  public void activate(Long id) {
    SysRerankConfig exists = requireExists(id);
    if (exists.getEnabled() == null || exists.getEnabled() != 1) {
      throw BizException.badRequest("error.ai.rerank.disabled");
    }
    mapper.update(null, new UpdateWrapper<SysRerankConfig>().set("is_active", 0));
    mapper.update(null, new UpdateWrapper<SysRerankConfig>()
        .set("is_active", 1).eq("id", id));
  }

  /**
   * 连通测试：真实精排一组样例文本，返回耗时与 Top-1 相关性得分。
   * 失败抛可读业务错误（管理员需要看到具体原因）。
   */
  public Map<String, Object> ping(Long id) {
    SysRerankConfig cfg = requireExists(id);
    RerankEndpoint endpoint = RerankEndpoint.from(cfg);
    long start = System.currentTimeMillis();
    List<RerankClient.RerankHit> hits = rerankClient.rerank(endpoint, "什么是知识库检索",
        List.of("知识库检索是把文档切块向量化后按相似度查找的能力",
            "今天的天气非常适合户外运动和散步",
            "系统支持向量与全文两路召回再融合排序"), 3);
    long elapsed = System.currentTimeMillis() - start;

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ok", true);
    data.put("elapsedMs", elapsed);
    data.put("topScore", hits.isEmpty() ? 0.0
        : Math.round(hits.get(0).relevanceScore() * 10000) / 10000.0);
    return data;
  }

  // ------------------------------------------------------------------

  private SysRerankConfig requireExists(Long id) {
    SysRerankConfig cfg = mapper.selectById(id);
    if (cfg == null) {
      throw BizException.badRequest("error.ai.rerank.notFound");
    }
    return cfg;
  }

  private void requireNameFree(String name, Long excludeId) {
    QueryWrapper<SysRerankConfig> q = new QueryWrapper<SysRerankConfig>().eq("name", name);
    if (excludeId != null) {
      q.ne("id", excludeId);
    }
    if (mapper.selectCount(q) > 0) {
      throw BizException.badRequest("error.ai.rerank.nameDup", name);
    }
  }

  private void validate(SysRerankConfig cfg) {
    if (!StringUtils.hasText(cfg.getName()) || !StringUtils.hasText(cfg.getBaseUrl())
        || !StringUtils.hasText(cfg.getModel())) {
      throw BizException.badRequest("error.ai.rerank.required");
    }
    if (cfg.getName().length() > 64 || cfg.getBaseUrl().length() > 255
        || cfg.getModel().length() > 64) {
      throw BizException.badRequest("error.ai.rerank.tooLong");
    }
  }

  private String stripTrailingSlash(String url) {
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /**
   * API Key 脱敏：与嵌入/对话模型配置同一规则（长度大于 10 保留前 5 后 4，其余全打码）。
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
