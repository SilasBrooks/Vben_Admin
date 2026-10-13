package com.vben.service.module.ai.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 检索评测服务单测：Hit@K/MRR 指标口径、期望块序严格匹配、快照落库字段、
 * 空集拒绝、单用例检索失败终止且不落记录、级联删除、用例上限与期望文档校验。
 */
class KbEvalServiceTest {

  private final KbEvalSetMapper setMapper = Mockito.mock(KbEvalSetMapper.class);
  private final KbEvalCaseMapper caseMapper = Mockito.mock(KbEvalCaseMapper.class);
  private final KbEvalRunMapper runMapper = Mockito.mock(KbEvalRunMapper.class);
  private final KbDocMapper docMapper = Mockito.mock(KbDocMapper.class);
  private final KbSearchService searchService = Mockito.mock(KbSearchService.class);
  private final ActiveEmbeddingResolver embeddingResolver =
      Mockito.mock(ActiveEmbeddingResolver.class);
  private final ActiveRerankResolver rerankResolver = Mockito.mock(ActiveRerankResolver.class);

  private final KbEvalService service = new KbEvalService(setMapper, caseMapper, runMapper,
      docMapper, searchService, embeddingResolver, rerankResolver, new ObjectMapper());

  private KbEvalSet set() {
    KbEvalSet set = new KbEvalSet();
    set.setId(1L);
    set.setName("基础检索集");
    return set;
  }

  private KbEvalCase evalCase(long id, String query, long docId, Integer seq) {
    KbEvalCase c = new KbEvalCase();
    c.setId(id);
    c.setSetId(1L);
    c.setQuery(query);
    c.setExpectDocId(docId);
    c.setExpectSeq(seq);
    return c;
  }

  /** 单条命中：docId + 块序可区分 */
  private KbSearchHit hit(long docId, int seq) {
    KbSearchHit hit = new KbSearchHit();
    hit.setDocId(docId);
    hit.setSeq(seq);
    hit.setFileName("文档" + docId + ".md");
    hit.setScore(0.01);
    return hit;
  }

  private void stubRunBase(List<KbEvalCase> cases) {
    when(setMapper.selectById(1L)).thenReturn(set());
    when(caseMapper.selectList(any())).thenReturn(cases);
    when(embeddingResolver.requireActive()).thenReturn(
        new EmbEndpoint("https://api.example.com", "sk-x", "bge-m3", null, 30));
    when(rerankResolver.resolveOrNull()).thenReturn(null);
    when(docMapper.selectBatchIds(any())).thenReturn(List.of());
  }

  @Test
  void metricsFollowHitRankDefinition() {
    // 5 用例：排名 1/1/1/3/未命中 → Hit@1=0.6 Hit@3=0.8 Hit@5=0.8 MRR=(1+1+1+1/3)/5≈0.6667
    KbEvalCase c1 = evalCase(1, "q1", 11, null);
    KbEvalCase c2 = evalCase(2, "q2", 21, null);
    KbEvalCase c3 = evalCase(3, "q3", 31, null);
    KbEvalCase c4 = evalCase(4, "q4", 41, null);
    KbEvalCase c5 = evalCase(5, "q5", 51, null);
    stubRunBase(List.of(c1, c2, c3, c4, c5));
    when(searchService.search("q1", 5)).thenReturn(
        List.of(hit(11, 0), hit(91, 0), hit(92, 0), hit(93, 0), hit(94, 0)));
    when(searchService.search("q2", 5)).thenReturn(
        List.of(hit(21, 0), hit(90, 0), hit(92, 0), hit(93, 0), hit(94, 0)));
    when(searchService.search("q3", 5)).thenReturn(
        List.of(hit(31, 0), hit(90, 0), hit(91, 0), hit(93, 0), hit(94, 0)));
    when(searchService.search("q4", 5)).thenReturn(
        List.of(hit(90, 0), hit(91, 0), hit(41, 0), hit(93, 0), hit(94, 0)));
    when(searchService.search("q5", 5)).thenReturn(
        List.of(hit(90, 0), hit(91, 0), hit(92, 0), hit(93, 0), hit(94, 0)));

    KbEvalService.RunItem run = service.run(1L);

    assertThat(run.getTotalCases()).isEqualTo(5);
    assertThat(run.getHitAt1()).isEqualTo(0.6);
    assertThat(run.getHitAt3()).isEqualTo(0.8);
    assertThat(run.getHitAt5()).isEqualTo(0.8);
    assertThat(run.getMrr()).isEqualTo(0.6667);
    assertThat(run.getRerankActive()).isFalse();
    assertThat(run.getEmbeddingModel()).isEqualTo("bge-m3");
    assertThat(run.getTopK()).isEqualTo(5);
    // 快照明细：第 4 条 rank=3，第 5 条未命中 rank=null
    assertThat(run.getDetail()).hasSize(5);
    assertThat(run.getDetail().get(3).getRank()).isEqualTo(3);
    assertThat(run.getDetail().get(4).getRank()).isNull();
    assertThat(run.getDetail().get(4).getTopHits()).hasSize(5);
    verify(runMapper).insert(any(KbEvalRun.class));
  }

  @Test
  void expectSeqRequiresExactChunkMatch() {
    // docId 在第 1 名命中但块序不符，第 2 名 docId+seq 同时匹配 → rank=2（严格口径）
    stubRunBase(List.of(evalCase(1, "q", 11, 2)));
    when(searchService.search("q", 5)).thenReturn(
        List.of(hit(11, 0), hit(11, 2), hit(92, 0), hit(93, 0), hit(94, 0)));

    KbEvalService.RunItem run = service.run(1L);

    assertThat(run.getDetail().get(0).getRank()).isEqualTo(2);
    assertThat(run.getMrr()).isEqualTo(0.5);

    // 全部块序不符 → 未命中
    stubRunBase(List.of(evalCase(1, "q", 11, 9)));
    when(searchService.search("q", 5)).thenReturn(
        List.of(hit(11, 0), hit(11, 2), hit(92, 0), hit(93, 0), hit(94, 0)));

    KbEvalService.RunItem miss = service.run(1L);

    assertThat(miss.getDetail().get(0).getRank()).isNull();
    assertThat(miss.getMrr()).isEqualTo(0.0);
  }

  @Test
  void rerankActiveSnapshotRecorded() {
    stubRunBase(List.of(evalCase(1, "q", 11, null)));
    when(rerankResolver.resolveOrNull()).thenReturn(
        new com.vben.service.module.ai.rerank.RerankEndpoint(
            1L, "https://api.example.com", "sk-x", "bge-m3", 30));
    when(searchService.search("q", 5)).thenReturn(List.of(hit(11, 0)));

    KbEvalService.RunItem run = service.run(1L);

    assertThat(run.getRerankActive()).isTrue();
  }

  @Test
  void emptySetRejectedWithoutRunRecord() {
    stubRunBase(List.of());

    assertThatThrownBy(() -> service.run(1L)).isInstanceOf(BizException.class);
    verify(runMapper, never()).insert(any(KbEvalRun.class));
  }

  @Test
  void caseSearchFailureTerminatesRun() {
    stubRunBase(List.of(evalCase(1, "q1", 11, null), evalCase(2, "q2", 21, null)));
    when(searchService.search("q1", 5)).thenReturn(List.of(hit(11, 0)));
    when(searchService.search("q2", 5))
        .thenThrow(BizException.badRequest("error.kb.dimensionMismatch", 1024, 768));

    // 任一用例检索失败立即终止（无容器时消息为原始 key），不产生运行记录
    assertThatThrownBy(() -> service.run(1L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("error.kbEval.caseFailed");
    verify(runMapper, never()).insert(any(KbEvalRun.class));
  }

  @Test
  void deleteSetRemovesCasesAndRuns() {
    when(setMapper.selectById(1L)).thenReturn(set());

    service.deleteSet(1L);

    verify(caseMapper).delete(any());
    verify(runMapper).delete(any());
    verify(setMapper).deleteById(1L);
  }

  @Test
  void createCaseValidatesDocExistsAndLimit() {
    when(setMapper.selectById(1L)).thenReturn(set());

    // 期望文档必须存在于 kb_doc
    when(docMapper.selectById(11L)).thenReturn(null);
    KbEvalService.CaseReq req = new KbEvalService.CaseReq();
    req.setQuery("问题");
    req.setExpectDocId(11L);
    assertThatThrownBy(() -> service.createCase(1L, req)).isInstanceOf(BizException.class);

    // 用例数已达上限 100 拒绝新增
    when(docMapper.selectById(11L)).thenReturn(new KbDoc());
    when(caseMapper.selectCount(any())).thenReturn((long) KbEvalService.MAX_CASES_PER_SET);
    assertThatThrownBy(() -> service.createCase(1L, req)).isInstanceOf(BizException.class);

    // 空查询文本拒绝
    when(caseMapper.selectCount(any())).thenReturn(0L);
    req.setQuery("  ");
    assertThatThrownBy(() -> service.createCase(1L, req)).isInstanceOf(BizException.class);
    verify(caseMapper, never()).insert(any(KbEvalCase.class));
  }

  @Test
  void ratioKeepsFourDecimals() {
    assertThat(KbEvalService.ratio(3, 5)).isEqualByComparingTo("0.6000");
    assertThat(KbEvalService.ratio(0, 5)).isEqualByComparingTo("0.0000");
  }
}
