# kb-retrieval-eval 设计

## Context

- 被测函数唯一：`KbSearchService.search(query, topK)`（三层链路：召回 → RRF → 可插拔精排），返回 `KbSearchHit`（含 docId / seq / fileName / score / vecScore / matchType / rerankScore）。评测是**只读消费者**，不改检索链路任何行为。
- 既有惯例可复用：菜单/权限码（DatabaseSeeder + 存量 upgrade SQL 两处同步，先例 `upgrade-20261012-rerank-citation-postgres.sql`）、`@Idempotent` / `@RateLimit` / `@OperLog`、useVbenVxeGrid + useVbenModal + useVbenForm、locales 中英同构、`AiSystemTools.MENU_TITLE_ZH`。
- 评测规模假设：单集合 ≤ 100 用例，管理页低频操作，本地/mock Embedding 毫秒级、真实 API 约 0.2-0.5s/条。

## Goals / Non-Goals

**Goals**

- 最小闭环：评测集/用例管理 → 批量执行 → Hit@K / MRR 指标 → 历史运行对比（含精排开关前后对比的叙事支撑）。
- 快照语义：运行记录不受后续用例/文档编辑影响，历史可信。

**Non-Goals**

- 不做异步执行/进度推送（同步接口 + 前端 loading 足够，避免复制 kb 管线那套线程池+断点复杂度）。
- 不做指标图表库可视化（指标卡 + 表格即可，不引入图表依赖）。
- 不做多评测集并行跑分、自动定时评测、Precision/Recall/NDCG 扩展指标。

## Decisions

1. **同步执行，运行记录完成时一次性落库**：执行中途失败（任一用例检索抛错）立即终止并返回可读错误，不产生半截记录。理由：评测幂等可重跑，失败重试零成本；避免了 RUNNING 状态残留、进度轮询、断点续跑一整套复杂度。
2. **固定 Top-K=5**：Hit@1/3/5 与 MRR 都能从一次 Top-5 检索的排名算出；`search` 内部 clamp(1,10) 与精排召回放宽（max(5×4,16)=20 ≤ 30）均安全。
3. **三张表，运行明细存 JSON 文本**：
   - `sys_kb_eval_set`（id, name 唯一, description, 审计列）
   - `sys_kb_eval_case`（id, set_id, query, expect_doc_id, expect_seq 可空, note, 审计列；set_id 外键级联删除）
   - `sys_kb_eval_run`（id, set_id, set_name 快照, top_k=5, total_cases, hit_at_1/3/5, mrr, elapsed_ms, rerank_active, embedding_model 快照, detail TEXT 存 JSON 文本, 审计列）
   - detail 存逐用例数组（query、expectDocId、expectSeq、rank 可空、topHits[≤5]{docId,seq,fileName,score,rerankScore}），单列读整体报告，免去逐用例子表；用 TEXT 而非 JSONB（JDBC URL 无 stringtype=unspecified，MyBatis-Plus 字符串直插 jsonb 会类型报错，且明细仅按 id 整读无 JSON 查询需求）。
   - 集合删除采用外键 `ON DELETE CASCADE`（先例：kb_upload_session.user_id/会话表），用例与运行随集合级联移除。
4. **指标口径**：Hit@K = 期望命中排名 ≤ K 的用例数 / 总用例数；MRR = Σ(1/rank) / N（未命中贡献 0）；expectSeq 填写时 docId 与 seq 须同时匹配（严格口径，见 delta spec）。分数后端保留 4 位小数。
5. **期望文档校验放在创建/编辑时**（docId 必须存在于 kb_doc），删除文档不回溯清理用例——运行时该用例自然未命中（docId 无法匹配），快照语义下历史不受影响。
6. **权限与菜单**：权限码 `Kb:Eval:List` / `Kb:Eval:Manage` / `Kb:Eval:Run`；菜单 leaf「检索评测」挂系统管理 order 13（`/system/kb-eval`，component `/system/kb-eval/index`），authority super,admin；`schema-postgres.sql`（三表）+ `DatabaseSeeder`（菜单/授权）+ `upgrade-20261012-kb-eval-postgres.sql`（存量库，幂等）三处一致；`AiSystemTools.MENU_TITLE_ZH` 与前端 locales（`page.system.kbEval.*`，中英同构）同步。
7. **接口形态**（`/system/kb-eval`，挂 `@RequirePermission` 语义与 KbController 一致）：
   - `GET /sets`（List）、`POST /sets`、`PUT /sets/{id}`、`DELETE /sets/{id}`（Manage）
   - `GET /sets/{id}/cases`（List）、`POST /sets/{id}/cases`、`PUT /cases/{id}`、`DELETE /cases/{id}`（Manage）
   - `POST /sets/{id}/runs`（Run，`@Idempotent` + `@RateLimit`，防连点与上游配额消耗）
   - `GET /sets/{id}/runs`（List）、`GET /runs/{id}`（List）
   - 前端下拉所需文档列表复用既有 `GET /system/kb/list` 分页接口（size 取大值）。
8. **前端页面结构**（单页三区，复用 useVbenVxeGrid / useVbenModal / useVbenForm）：
   - 区一：评测集表格（名称/描述/用例数/最近一次运行 MRR 概览）+ 新增/编辑/删除
   - 区二：选中集合的用例表格 + 新增/编辑弹窗（期望文档下拉、期望块序数字输入）+ 执行按钮（loading 态）
   - 区三：运行历史表格 → 「查看报告」弹窗（指标卡四枚 + 逐用例 vxe-table：排名列未命中标红、Top-5 片段展开）
9. **审计**：评测执行、集合/用例删除挂 `@OperLog`（写操作留痕）；错误文案走 `BizException.badRequest("error.kbEval.*")` + messages 中英双语。

## Risks / Trade-offs

- **同步执行时长**：真实上游 API 下 100 用例可能 30s+，HTTP 超时风险 → 用例上限 100 且文案引导控制规模；若未来需要更大规模再演进异步（留 Non-Goal）。
- **快照 JSONB 无索引**：明细仅按 run id 整读，无检索需求，可接受。
- **期望文档被删后用例失真**：报告明细中的期望文档名取运行时快照，能看出「当时」指向；不做孤儿清理（快照语义优先）。
