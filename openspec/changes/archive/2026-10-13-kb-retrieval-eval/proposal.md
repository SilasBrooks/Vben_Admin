# kb-retrieval-eval

## Why

混合检索与 Rerank 精排上线后，检索质量只能靠「检索测试」弹窗人工逐条感知——改了分词、调了召回参数、开关精排，效果到底变好还是变坏没有数字可讲。评测是 RAG 工程的标准闭环：内置 QA 评测集，对 `KbSearchService` 批量执行检索，产出 **Hit@1/3/5、MRR** 等量化指标，让检索优化从「感觉」变成「指标」，也为精排配置的价值提供前后对比数据。

## What Changes

1. **评测集管理**：新增评测集（名称/描述）与评测用例（query + 期望命中文档 docId + 可选期望块序 seq + 备注）CRUD；期望文档从知识库文档列表下拉选择，校验文档必须存在。
2. **评测执行与指标**：对整个评测集批量调用 `KbSearchService.search(query, 5)`，逐用例判定期望命中是否出现在 Top-K 及其排名，计算 **Hit@1 / Hit@3 / Hit@5**（期望命中进入 Top-K 的用例比例）与 **MRR**（命中排名倒数的平均值）；每次运行落库为运行记录（指标 + 精排激活状态快照 + 逐用例命中详情 JSON），保留历史可对比。
3. **前端检索评测页面**：系统管理下新增「检索评测」页——评测集与用例管理（列表/新增/编辑/删除）、执行评测（loading 状态）、运行历史（指标概览 + 精排是否生效标注）、运行报告详情（指标卡 + 逐用例命中排名明细，命中高亮/未命中标红）。
4. **菜单与权限**：新增权限码 `Kb:Eval:List` / `Kb:Eval:Manage` / `Kb:Eval:Run`，新菜单「检索评测」（系统管理 leaf，super/admin 可见），`DatabaseSeeder` 与存量库升级 SQL 两处同步；菜单标题 i18n（中英语言包 + `AiSystemTools.MENU_TITLE_ZH`）同步。

## Capabilities

### New Capabilities

（无——评测能力归属既有知识库能力，不新建目录）

### Modified Capabilities

- `ai/knowledge-base`：ADDED 三条需求——「检索质量评测集管理」「检索评测执行与指标」「前端检索评测页面」；既有需求不变（评测只读调用检索链路，不改变检索行为）。

## Impact

- **后端**：新增 `module/ai/kb/` 下 `KbEvalSet/KbEvalCase/KbEvalRun` 实体、mapper（`module/ai/kb/mapper` 子包）、`KbEvalService`、`KbEvalController`（`/system/kb-eval/*`）；`sys_kb_eval_set` / `sys_kb_eval_case` / `sys_kb_eval_run` 建表 + 菜单权限码 SQL（`schema-postgres.sql` + `DatabaseSeeder` + 存量升级 SQL 三处一致）；`AiSystemTools.MENU_TITLE_ZH` 同步；评测执行接口挂 `@Idempotent`。
- **前端**：新增 `views/system/kb/kb-eval/` 页面与 `api/system/kbEval.ts`；locales 中英同构新增 `page.system.kbEval` 命名空间。
- **兼容性**：纯加法升级，不改动检索链路与既有接口；评测执行失败（无激活 Embedding / 维度不一致等）复用 `KbSearchService` 既有可读错误语义。
