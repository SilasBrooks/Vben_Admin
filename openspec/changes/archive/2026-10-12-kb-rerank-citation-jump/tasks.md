# Tasks: kb-rerank-citation-jump

## 1. 数据库与种子

- [x] 1.1 sys_rerank_config 建表（schema-postgres.sql + upgrade SQL 幂等）+ 精排配置菜单/权限码 SQL（DatabaseSeeder + 存量库 upgrade SQL 两处一致）

## 2. 后端 Rerank 模块

- [x] 2.1 module/ai/rerank：SysRerankConfig 实体 + mapper（子包）+ SysRerankConfigService（CRUD/脱敏/留空不改 Key/激活唯一/ping 连通测试）
- [x] 2.2 ActiveRerankResolver + RerankClient（OpenAI 兼容 POST /rerank，失败 log.warn 返回 null 降级）
- [x] 2.3 RerankConfigController（/system/rerank，权限码/限流/幂等/审计对齐嵌入配置）+ messages 中英错误文案
- [x] 2.4 KbSearchService 精排链路（召回放宽 ≤30、rerankScore 字段、降级语义）+ KbSearchHit 扩展 + 单测

## 3. 后端溯源跳转

- [x] 3.1 AiKnowledgeTools snippets 加 docId + AiChatService KbCitation 加 docId（SSE 链路）
- [x] 3.2 KbController 文档原文预览接口（仅登录态、限流、kb_chunk 按 seq 全量）

## 4. 前端

- [x] 4.1 api：rerank.ts + kb.ts preview 方法 + ai chat citations 类型加 docId（去重 key 改 docId#seq）
- [x] 4.2 精排配置页面（参照嵌入配置页）+ 路由菜单 + locales 中英
- [x] 4.3 溯源卡片「查看原文」+ CitationDocPreview 预览弹窗（seq 定位高亮）+ 检索测试弹窗精排分展示
- [x] 4.4 AiSystemTools.MENU_TITLE_ZH 同步精排配置菜单

## 5. 验证与收尾

- [x] 5.1 mvn test 全绿 + pnpm check:type
- [x] 5.2 E2E 验证（精排配置页 CRUD/激活/测试、检索测试精排分、溯源卡片查看原文定位高亮；mock rerank 不触碰 Doubao-embedding-vision）
- [x] 5.3 README 三处更新（项目亮点/功能清单/注意事项）
- [x] 5.4 openspec validate --strict → archive（sync delta）

