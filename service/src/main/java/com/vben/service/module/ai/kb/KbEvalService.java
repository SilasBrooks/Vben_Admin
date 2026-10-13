package com.vben.service.module.ai.kb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbEndpoint;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.module.ai.kb.entity.KbEvalCase;
import com.vben.service.module.ai.kb.entity.KbEvalRun;
import com.vben.service.module.ai.kb.entity.KbEvalSet;
import com.vben.service.module.ai.kb.mapper.KbDocMapper;
import com.vben.service.module.ai.kb.mapper.KbEvalCaseMapper;
import com.vben.service.module.ai.kb.mapper.KbEvalRunMapper;
import com.vben.service.module.ai.kb.mapper.KbEvalSetMapper;
import com.vben.service.module.ai.rerank.ActiveRerankResolver;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索质量评测：评测集/用例管理 + 批量执行 + Hit@K / MRR 指标。
 *
 * <p>评测是 {@link KbSearchService} 的只读消费者（固定 Top-K=5，含激活精排），
 * 不改变检索链路任何行为。命中判定：结果中存在 docId 等于期望文档的命中即命中，
 * 用例填写期望块序号时 docId 与 seq 须同时匹配（严格口径）。
 *
 * <p>运行记录为快照语义：指标、精排激活状态、Embedding 模型名、期望文档名与
 * 逐用例明细在执行完成时一次性落库，后续编辑用例或文档不影响历史记录；
 * 执行中途任一用例检索失败立即终止（注明出错用例），不产生本次运行记录。
 */
@Service
@RequiredArgsConstructor
public class KbEvalService {

  /** 评测固定检索条数（Hit@1/3/5 与 MRR 均可由一次 Top-5 排名算出） */
  public static final int EVAL_TOP_K = 5;

  /** 单评测集用例数上限（同步执行规模边界，真实上游 API 下控制总耗时） */
  public static final int MAX_CASES_PER_SET = 100;

  private final KbEvalSetMapper setMapper;
  private final KbEvalCaseMapper caseMapper;
  private final KbEvalRunMapper runMapper;
  private final KbDocMapper docMapper;
  private final KbSearchService searchService;
  private final ActiveEmbeddingResolver embeddingResolver;
  private final ActiveRerankResolver rerankResolver;
  private final ObjectMapper objectMapper;

  // ------------------------------------------------------------------
  // DTO（接口出参）
  // ------------------------------------------------------------------

  /** 评测集列表项：含用例数与最近一次运行概览 */
  @Data
  public static class SetItem {
    private Long id;
    private String name;
    private String description;
    private Long caseCount;
    private Long lastRunId;
    private Double lastMrr;
    private String createTime;
  }

  /** 用例列表项：附期望文档名（编辑前回显，文档被删后显示为空） */
  @Data
  public static class CaseItem {
    private Long id;
    private Long setId;
    private String query;
    private Long expectDocId;
    private String expectDocName;
    private Integer expectSeq;
    private String note;
    private String createTime;
  }

  /** 运行记录列表/报告项（detail 仅报告接口解析填充） */
  @Data
  public static class RunItem {
    private Long id;
    private Long setId;
    private String setName;
    private Integer topK;
    private Integer totalCases;
    private Double hitAt1;
    private Double hitAt3;
    private Double hitAt5;
    private Double mrr;
    private Long elapsedMs;
    private Boolean rerankActive;
    private String embeddingModel;
    private String createTime;
    private List<CaseResult> detail;
  }

  /** 逐用例结果（快照） */
  @Data
  public static class CaseResult {
    private String query;
    private Long expectDocId;
    private String expectDocName;
    private Integer expectSeq;
    /** 期望命中排名（1 起始；null=Top-K 内未命中） */
    private Integer rank;
    private List<HitBrief> topHits;
  }

  /** Top-5 命中片段摘要（快照） */
  @Data
  public static class HitBrief {
    private Long docId;
    private Integer seq;
    private String fileName;
    private Double score;
    private Double rerankScore;

    static HitBrief from(KbSearchHit hit) {
      HitBrief b = new HitBrief();
      b.setDocId(hit.getDocId());
      b.setSeq(hit.getSeq());
      b.setFileName(hit.getFileName());
      b.setScore(hit.getScore());
      b.setRerankScore(hit.getRerankScore());
      return b;
    }
  }

  /** 评测集新增/编辑入参 */
  @Data
  public static class SetReq {
    private String name;
    private String description;
  }

  /** 用例新增/编辑入参 */
  @Data
  public static class CaseReq {
    private String query;
    private Long expectDocId;
    private Integer expectSeq;
    private String note;
  }

  // ------------------------------------------------------------------
  // 评测集 CRUD
  // ------------------------------------------------------------------

  /** 评测集分页列表（附用例数与最近一次运行 MRR 概览） */
  public IPage<SetItem> pageSets(long pageNo, long pageSize, String name) {
    QueryWrapper<KbEvalSet> qw = new QueryWrapper<KbEvalSet>().orderByDesc("create_time");
    if (name != null && !name.isBlank()) {
      qw.like("name", name.trim());
    }
    IPage<KbEvalSet> page = setMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    return page.convert(this::toSetItem);
  }

  private SetItem toSetItem(KbEvalSet set) {
    SetItem item = new SetItem();
    item.setId(set.getId());
    item.setName(set.getName());
    item.setDescription(set.getDescription());
    item.setCaseCount(caseMapper.selectCount(new QueryWrapper<KbEvalCase>().eq("set_id", set.getId())));
    KbEvalRun last = runMapper.selectOne(new QueryWrapper<KbEvalRun>()
        .eq("set_id", set.getId()).orderByDesc("id").last("LIMIT 1"));
    if (last != null) {
      item.setLastRunId(last.getId());
      item.setLastMrr(last.getMrr() == null ? null : last.getMrr().doubleValue());
    }
    item.setCreateTime(stripNanos(set.getCreateTime()));
    return item;
  }

  /** 新增评测集（名称唯一） */
  @Transactional
  public SetItem createSet(SetReq req) {
    String name = requireText(req.getName(), 64, "error.kbEval.nameRequired");
    checkSetNameFree(name, null);
    KbEvalSet set = new KbEvalSet();
    set.setName(name);
    set.setDescription(clip(req.getDescription(), 255));
    setMapper.insert(set);
    return toSetItem(set);
  }

  /** 编辑评测集（名称唯一，排除自身） */
  @Transactional
  public SetItem updateSet(Long id, SetReq req) {
    KbEvalSet set = requireSet(id);
    String name = requireText(req.getName(), 64, "error.kbEval.nameRequired");
    checkSetNameFree(name, id);
    set.setName(name);
    set.setDescription(clip(req.getDescription(), 255));
    setMapper.updateById(set);
    return toSetItem(set);
  }

  /** 删除评测集：用例与运行记录一并删除（显式删除 + FK 级联兜底） */
  @Transactional
  public void deleteSet(Long id) {
    requireSet(id);
    caseMapper.delete(new QueryWrapper<KbEvalCase>().eq("set_id", id));
    runMapper.delete(new QueryWrapper<KbEvalRun>().eq("set_id", id));
    setMapper.deleteById(id);
  }

  private KbEvalSet requireSet(Long id) {
    KbEvalSet set = setMapper.selectById(id);
    if (set == null) {
      throw BizException.notFound("error.kbEval.setNotFound");
    }
    return set;
  }

  private void checkSetNameFree(String name, Long excludeId) {
    QueryWrapper<KbEvalSet> qw = new QueryWrapper<KbEvalSet>().eq("name", name);
    if (excludeId != null) {
      qw.ne("id", excludeId);
    }
    if (setMapper.selectCount(qw) > 0) {
      throw BizException.badRequest("error.kbEval.setDup");
    }
  }

  // ------------------------------------------------------------------
  // 用例 CRUD
  // ------------------------------------------------------------------

  /** 用例列表（按 id 升序，附期望文档名快照回显） */
  public List<CaseItem> listCases(Long setId) {
    requireSet(setId);
    List<KbEvalCase> cases = caseMapper.selectList(new QueryWrapper<KbEvalCase>()
        .eq("set_id", setId).orderByAsc("id"));
    Map<Long, String> docNames = docNamesOf(cases.stream().map(KbEvalCase::getExpectDocId).toList());
    List<CaseItem> items = new ArrayList<>(cases.size());
    for (KbEvalCase c : cases) {
      CaseItem item = new CaseItem();
      item.setId(c.getId());
      item.setSetId(c.getSetId());
      item.setQuery(c.getQuery());
      item.setExpectDocId(c.getExpectDocId());
      item.setExpectDocName(docNames.get(c.getExpectDocId()));
      item.setExpectSeq(c.getExpectSeq());
      item.setNote(c.getNote());
      item.setCreateTime(stripNanos(c.getCreateTime()));
      items.add(item);
    }
    return items;
  }

  /** 新增用例：期望文档必须存在，用例数上限 {@value MAX_CASES_PER_SET} */
  @Transactional
  public CaseItem createCase(Long setId, CaseReq req) {
    requireSet(setId);
    KbEvalCase c = buildCase(req);
    c.setSetId(setId);
    if (caseMapper.selectCount(new QueryWrapper<KbEvalCase>().eq("set_id", setId))
        >= MAX_CASES_PER_SET) {
      throw BizException.badRequest("error.kbEval.caseLimit", MAX_CASES_PER_SET);
    }
    caseMapper.insert(c);
    return toCaseItem(c);
  }

  /** 编辑用例（期望文档存在性同新建校验） */
  @Transactional
  public CaseItem updateCase(Long id, CaseReq req) {
    KbEvalCase c = caseMapper.selectById(id);
    if (c == null) {
      throw BizException.notFound("error.kbEval.caseNotFound");
    }
    KbEvalCase updated = buildCase(req);
    c.setQuery(updated.getQuery());
    c.setExpectDocId(updated.getExpectDocId());
    c.setExpectSeq(updated.getExpectSeq());
    c.setNote(updated.getNote());
    caseMapper.updateById(c);
    return toCaseItem(c);
  }

  /** 删除用例 */
  @Transactional
  public void deleteCase(Long id) {
    if (caseMapper.selectById(id) == null) {
      throw BizException.notFound("error.kbEval.caseNotFound");
    }
    caseMapper.deleteById(id);
  }

  private KbEvalCase buildCase(CaseReq req) {
    if (req.getQuery() == null || req.getQuery().isBlank()) {
      throw BizException.badRequest("error.kbEval.queryBlank");
    }
    String query = req.getQuery().trim();
    if (query.length() > 512) {
      throw BizException.badRequest("error.kbEval.queryTooLong");
    }
    if (req.getExpectDocId() == null
        || docMapper.selectById(req.getExpectDocId()) == null) {
      throw BizException.badRequest("error.kbEval.docNotFound");
    }
    KbEvalCase c = new KbEvalCase();
    c.setQuery(query);
    c.setExpectDocId(req.getExpectDocId());
    c.setExpectSeq(req.getExpectSeq());
    c.setNote(clip(req.getNote(), 255));
    return c;
  }

  private CaseItem toCaseItem(KbEvalCase c) {
    CaseItem item = new CaseItem();
    item.setId(c.getId());
    item.setSetId(c.getSetId());
    item.setQuery(c.getQuery());
    item.setExpectDocId(c.getExpectDocId());
    item.setExpectDocName(docNamesOf(List.of(c.getExpectDocId())).get(c.getExpectDocId()));
    item.setExpectSeq(c.getExpectSeq());
    item.setNote(c.getNote());
    item.setCreateTime(stripNanos(c.getCreateTime()));
    return item;
  }

  // ------------------------------------------------------------------
  // 执行评测与指标
  // ------------------------------------------------------------------

  /**
   * 执行评测：逐用例检索（Top-5）→ 命中判定与排名 → Hit@1/3/5 + MRR → 快照落库。
   * 任一用例检索失败立即终止并注明用例序号，不产生运行记录。
   */
  @Transactional
  public RunItem run(Long setId) {
    KbEvalSet set = requireSet(setId);
    List<KbEvalCase> cases = caseMapper.selectList(new QueryWrapper<KbEvalCase>()
        .eq("set_id", setId).orderByAsc("id"));
    if (cases.isEmpty()) {
      throw BizException.badRequest("error.kbEval.emptySet");
    }

    // 快照：执行时的 Embedding 模型名与精排激活状态（KbSearchService 内部解析同一激活配置）
    EmbEndpoint emb = embeddingResolver.requireActive();
    boolean rerankActive = rerankResolver.resolveOrNull() != null;
    Map<Long, String> docNames = docNamesOf(cases.stream().map(KbEvalCase::getExpectDocId).toList());

    long start = System.currentTimeMillis();
    List<CaseResult> results = new ArrayList<>(cases.size());
    double reciprocalSum = 0;
    int hit1 = 0;
    int hit3 = 0;
    int hit5 = 0;
    for (int i = 0; i < cases.size(); i++) {
      KbEvalCase c = cases.get(i);
      List<KbSearchHit> hits;
      try {
        hits = searchService.search(c.getQuery(), EVAL_TOP_K);
      } catch (BizException e) {
        throw BizException.badRequest("error.kbEval.caseFailed", i + 1, e.getMessage());
      }
      CaseResult r = new CaseResult();
      r.setQuery(c.getQuery());
      r.setExpectDocId(c.getExpectDocId());
      r.setExpectDocName(docNames.get(c.getExpectDocId()));
      r.setExpectSeq(c.getExpectSeq());
      r.setTopHits(hits.stream().map(HitBrief::from).toList());

      int rank = 0;
      for (int j = 0; j < hits.size(); j++) {
        KbSearchHit hit = hits.get(j);
        if (c.getExpectDocId().equals(hit.getDocId())
            && (c.getExpectSeq() == null || c.getExpectSeq().equals(hit.getSeq()))) {
          rank = j + 1;
          break;
        }
      }
      if (rank > 0) {
        r.setRank(rank);
        reciprocalSum += 1.0 / rank;
        if (rank <= 1) {
          hit1++;
        }
        if (rank <= 3) {
          hit3++;
        }
        if (rank <= 5) {
          hit5++;
        }
      }
      results.add(r);
    }
    long elapsed = System.currentTimeMillis() - start;

    int total = cases.size();
    KbEvalRun run = new KbEvalRun();
    run.setSetId(setId);
    run.setSetName(set.getName());
    run.setTopK(EVAL_TOP_K);
    run.setTotalCases(total);
    run.setHitAt1(ratio(hit1, total));
    run.setHitAt3(ratio(hit3, total));
    run.setHitAt5(ratio(hit5, total));
    run.setMrr(BigDecimal.valueOf(reciprocalSum / total).setScale(4, RoundingMode.HALF_UP));
    run.setElapsedMs(elapsed);
    run.setRerankActive(rerankActive ? 1 : 0);
    run.setEmbeddingModel(emb.model() == null ? "" : emb.model());
    run.setDetail(writeDetail(results));
    runMapper.insert(run);
    RunItem item = toRunItem(run);
    item.setDetail(results);
    return item;
  }

  /** 运行历史（按时间倒序，全量，管理页低频） */
  public List<RunItem> listRuns(Long setId) {
    requireSet(setId);
    List<KbEvalRun> runs = runMapper.selectList(new QueryWrapper<KbEvalRun>()
        .eq("set_id", setId).orderByDesc("id"));
    return runs.stream().map(this::toRunItem).toList();
  }

  /** 运行报告详情（含逐用例明细解析） */
  public RunItem getRun(Long id) {
    KbEvalRun run = runMapper.selectById(id);
    if (run == null) {
      throw BizException.notFound("error.kbEval.runNotFound");
    }
    RunItem item = toRunItem(run);
    item.setDetail(readDetail(run.getDetail()));
    return item;
  }

  private RunItem toRunItem(KbEvalRun run) {
    RunItem item = new RunItem();
    item.setId(run.getId());
    item.setSetId(run.getSetId());
    item.setSetName(run.getSetName());
    item.setTopK(run.getTopK());
    item.setTotalCases(run.getTotalCases());
    item.setHitAt1(run.getHitAt1() == null ? null : run.getHitAt1().doubleValue());
    item.setHitAt3(run.getHitAt3() == null ? null : run.getHitAt3().doubleValue());
    item.setHitAt5(run.getHitAt5() == null ? null : run.getHitAt5().doubleValue());
    item.setMrr(run.getMrr() == null ? null : run.getMrr().doubleValue());
    item.setElapsedMs(run.getElapsedMs());
    item.setRerankActive(run.getRerankActive() != null && run.getRerankActive() == 1);
    item.setEmbeddingModel(run.getEmbeddingModel());
    item.setCreateTime(stripNanos(run.getCreateTime()));
    return item;
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /** 命中占比（4 位小数） */
  static BigDecimal ratio(int hit, int total) {
    return BigDecimal.valueOf(hit)
        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
  }

  private Map<Long, String> docNamesOf(List<Long> docIds) {
    List<Long> distinct = docIds.stream().distinct().toList();
    if (distinct.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> names = new LinkedHashMap<>();
    for (KbDoc doc : docMapper.selectBatchIds(distinct)) {
      names.put(doc.getId(), doc.getFileName());
    }
    return names;
  }

  private String writeDetail(List<CaseResult> results) {
    try {
      return objectMapper.writeValueAsString(results);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("评测明细序列化失败", e);
    }
  }

  private List<CaseResult> readDetail(String detail) {
    if (detail == null || detail.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(detail,
          objectMapper.getTypeFactory().constructCollectionType(List.class, CaseResult.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("评测明细解析失败", e);
    }
  }

  private String requireText(String value, int max, String blankKey) {
    if (value == null || value.isBlank()) {
      throw BizException.badRequest(blankKey);
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw BizException.badRequest("error.kbEval.textTooLong", max);
    }
    return trimmed;
  }

  private String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(trimmed.length(), max));
  }

  /** 时间展示去掉 LocalDateTime 微秒尾巴 */
  private String stripNanos(java.time.LocalDateTime time) {
    return time == null ? null : time.toString().split("\\.")[0];
  }
}
