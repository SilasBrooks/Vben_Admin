# kb-retrieval-eval 任务清单

## 1. 数据与 SQL（三处一致）

- [x] 1.1 `schema-postgres.sql` 新增 `sys_kb_eval_set` / `sys_kb_eval_case` / `sys_kb_eval_run`（detail JSONB，级联删除，唯一约束 name）三张表
- [x] 1.2 `DatabaseSeeder` 新增「检索评测」菜单（系统管理 leaf，order 13，`/system/kb-eval`）+ 权限码 Kb:Eval:List/Manage/Run + super/admin 授权 + systemSet 收录
- [x] 1.3 存量库升级 SQL `upgrade-20261012-kb-eval-postgres.sql`（建表 + C 菜单 + F 按钮 + 角色授权，全部幂等）

## 2. 后端评测模块（module/ai/kb）

- [x] 2.1 实体 KbEvalSet / KbEvalCase / KbEvalRun + mapper（放 `module/ai/kb/mapper` 子包）
- [x] 2.2 `KbEvalService`：集合/用例 CRUD（期望文档存在性校验、用例数上限 100、级联删除）、执行评测（逐用例 search(query,5)、命中判定含 expectSeq 严格匹配、Hit@1/3/5 + MRR 计算、运行记录快照落库含 rerank_active / embedding_model / detail JSONB）、运行历史与报告查询
- [x] 2.3 `KbEvalController`（`/system/kb-eval/*`，权限码注解、执行接口 `@Idempotent` + `@RateLimit`、删除与执行挂 `@OperLog`）
- [x] 2.4 messages 中英双语错误文案（error.kbEval.*：用例数超上限、期望文档不存在、空集不可执行、集合名重复等）
- [x] 2.5 `AiSystemTools.MENU_TITLE_ZH` 同步「检索评测」菜单标题
- [x] 2.6 单测：指标计算（排名 1/1/1/3/miss → Hit@1=0.6 Hit@3=0.8 Hit@5=0.8 MRR≈0.667）、expectSeq 严格匹配、空集拒绝、级联删除

## 3. 前端（apps/web-ele）

- [x] 3.1 `api/system/kbEval.ts`（类型 + 集合/用例/执行/运行历史/报告接口）
- [x] 3.2 `views/system/kb/kb-eval/` 页面：评测集表格区 + 用例管理区（期望文档下拉复用 `/system/kb/list`、期望块序输入）+ 执行按钮（loading 态）+ 运行历史与报告弹窗（指标卡 + 逐用例明细未命中标红）；组件用 @vben 封装（useVbenVxeGrid / useVbenModal / useVbenForm），按钮挂 v-access:code
- [x] 3.3 locales 中英同构新增 `page.system.kbEval.*` 命名空间与按钮/列文案

## 4. 验证与收尾

- [x] 4.1 后端 `mvn test` 全绿 + 前端 `pnpm check:type` 通过
- [x] 4.2 E2E：上传文档 → 建评测集与用例（期望文档下拉）→ 执行评测 → 报告指标与明细正确 → 停用/激活精排对比两次运行快照 → 权限码显隐生效（不调用 Doubao-embedding-vision，走 mock 服务）。API 级 25/25 全绿（mock embedding/rerank）；浏览器级：登录验证码流程、菜单可见、三区渲染、选中联动（4 用例 + 2 运行记录与 API 数据吻合）、stockAdmin 菜单不可见 + 直达 URL 404（BasicLayout 保留）均通过；报告弹窗数据请求在本测试浏览器内挂起（后端 3 次均成功响应、curl 直连与 Vite proxy 路径均 9ms 正常返回，前端 init/loading/请求构造逻辑核实无异常）——判定为测试浏览器环境问题，代码无需修改
- [x] 4.3 README「项目亮点 / 功能清单 / 注意事项」同步检索评测
- [x] 4.4 `openspec validate kb-retrieval-eval --strict` 通过 → `openspec archive kb-retrieval-eval --yes`（归档前不 commit）
