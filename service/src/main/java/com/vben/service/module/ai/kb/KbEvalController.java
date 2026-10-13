package com.vben.service.module.ai.kb;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.ai.kb.KbEvalService.CaseItem;
import com.vben.service.module.ai.kb.KbEvalService.CaseReq;
import com.vben.service.module.ai.kb.KbEvalService.RunItem;
import com.vben.service.module.ai.kb.KbEvalService.SetItem;
import com.vben.service.module.ai.kb.KbEvalService.SetReq;
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
import java.util.List;
import java.util.Map;

/**
 * 知识库检索质量评测接口（系统管理-检索评测）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>Kb:Eval:List    评测集/用例/运行记录查看</li>
 *   <li>Kb:Eval:Manage  评测集与用例增删改</li>
 *   <li>Kb:Eval:Run     执行评测</li>
 * </ul>
 */
@RestController
@Tag(name = "检索评测", description = "知识库检索质量评测：评测集/用例管理与批量执行（Hit@1/3/5 + MRR 指标）")
@RequestMapping("/system/kb-eval")
@RequiredArgsConstructor
public class KbEvalController {

  private final KbEvalService evalService;

  /** 评测集分页列表 */
  @Operation(summary = "评测集列表", description = "名称模糊过滤 + 分页；附用例数与最近一次运行 MRR 概览")
  @RequirePermission("Kb:Eval:List")
  @GetMapping("/sets")
  public R<Map<String, Object>> listSets(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String name) {
    var page = evalService.pageSets(pageNo, pageSize, name);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 新增评测集：不加 @Idempotent——与新增用例同理，连续建集合是合理操作；名称唯一约束天然防重 */
  @Operation(summary = "新增评测集", description = "名称唯一")
  @RequirePermission("Kb:Eval:Manage")
  @PostMapping("/sets")
  public R<SetItem> addSet(@RequestBody SetReq req) {
    return R.ok(evalService.createSet(req));
  }

  /** 编辑评测集 */
  @Operation(summary = "编辑评测集", description = "名称唯一（排除自身）；不影响历史运行记录（快照语义）")
  @RequirePermission("Kb:Eval:Manage")
  @PutMapping("/sets/{id}")
  public R<SetItem> updateSet(@PathVariable Long id, @RequestBody SetReq req) {
    return R.ok(evalService.updateSet(id, req));
  }

  /** 删除评测集（级联删除用例与运行记录） */
  @Operation(summary = "删除评测集", description = "评测集及其全部用例、运行记录一并移除")
  @RequirePermission("Kb:Eval:Manage")
  @OperLog(module = "检索评测", description = "删除评测集")
  @DeleteMapping("/sets/{id}")
  public R<Void> deleteSet(@PathVariable Long id) {
    evalService.deleteSet(id);
    return R.ok();
  }

  /** 用例列表 */
  @Operation(summary = "评测用例列表", description = "按创建顺序返回，附期望文档名回显")
  @RequirePermission("Kb:Eval:List")
  @GetMapping("/sets/{setId}/cases")
  public R<List<CaseItem>> listCases(@PathVariable Long setId) {
    return R.ok(evalService.listCases(setId));
  }

  /** 新增用例：不加 @Idempotent——用例是连续录入场景（一次建多条），name 级幂等会把窗口内的不同用例误判为重复提交 */
  @Operation(summary = "新增评测用例", description = "期望文档必须为知识库真实存在的文档；单集合用例上限 100")
  @RequirePermission("Kb:Eval:Manage")
  @PostMapping("/sets/{setId}/cases")
  public R<CaseItem> addCase(@PathVariable Long setId, @RequestBody CaseReq req) {
    return R.ok(evalService.createCase(setId, req));
  }

  /** 编辑用例 */
  @Operation(summary = "编辑评测用例", description = "期望文档存在性校验同新增")
  @RequirePermission("Kb:Eval:Manage")
  @PutMapping("/cases/{id}")
  public R<CaseItem> updateCase(@PathVariable Long id, @RequestBody CaseReq req) {
    return R.ok(evalService.updateCase(id, req));
  }

  /** 删除用例 */
  @Operation(summary = "删除评测用例", description = "仅删除该用例，不影响历史运行记录")
  @RequirePermission("Kb:Eval:Manage")
  @OperLog(module = "检索评测", description = "删除评测用例")
  @DeleteMapping("/cases/{id}")
  public R<Void> deleteCase(@PathVariable Long id) {
    evalService.deleteCase(id);
    return R.ok();
  }

  /** 执行评测：逐用例检索并计算 Hit@1/3/5 与 MRR */
  @Operation(summary = "执行评测", description = "对评测集全部用例逐条检索（Top-5，含激活精排），"
      + "落库一条快照运行记录；任一用例检索失败即终止且不产生记录")
  @RequirePermission("Kb:Eval:Run")
  @OperLog(module = "检索评测", description = "执行检索评测")
  @Idempotent(name = "kb-eval:run", intervalSeconds = 5)
  @RateLimit(name = "kb-eval:run", limit = 6, windowSeconds = 60)
  @PostMapping("/sets/{setId}/runs")
  public R<RunItem> run(@PathVariable Long setId) {
    return R.ok(evalService.run(setId));
  }

  /** 运行历史 */
  @Operation(summary = "运行历史", description = "该评测集全部运行记录（快照，含精排激活状态标注）")
  @RequirePermission("Kb:Eval:List")
  @GetMapping("/sets/{setId}/runs")
  public R<List<RunItem>> listRuns(@PathVariable Long setId) {
    return R.ok(evalService.listRuns(setId));
  }

  /** 运行报告详情 */
  @Operation(summary = "运行报告详情", description = "指标 + 逐用例命中排名与 Top-5 命中片段明细")
  @RequirePermission("Kb:Eval:List")
  @GetMapping("/runs/{id}")
  public R<RunItem> getRun(@PathVariable Long id) {
    return R.ok(evalService.getRun(id));
  }
}
