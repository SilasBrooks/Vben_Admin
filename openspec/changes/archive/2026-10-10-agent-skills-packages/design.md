## Context

`ai/agent-skills` 已上线简化版技能（DB 单记录：name/title/description/content/enabled/builtIn），渐进式披露两级（系统提示词清单 → activate_skill 正文）。社区技能包是目录结构 zip：`skill-name/SKILL.md`（YAML frontmatter）+ `references/` + `assets/` + `scripts/`。本变更把包形态接入既有模型，不推翻现有实现。存储复用 `common/storage/StorageService`（local/minio 可插拔，`store(InputStream, originalName, size) → StoredFile`、`open(key)`）。

## Goals / Non-Goals

**Goals**
- 一键上传 zip → 解析 frontmatter 建技能，原包可追溯（清单/下载）
- AI 三级披露：清单 → activate_skill 正文 + 文件清单附注 → load_skill_file 按需加载参考文档
- 安全面闭环：zip 炸弹/路径穿越/条目上限防护，脚本零执行
- 纯增量：手工技能与内置技能行为不变，零新权限码

**Non-Goals**
- scripts 执行（安全设计量大，后置）
- 技能包市场/在线检索、包内文件在线编辑
- 内置技能改造成包形态（种子内容继续走 SQL）

## Decisions

### D1：原包整体存储，文件按需提取（不拆条目落存储）
上传时把原始 zip 作为单个对象 `store()` 进 StorageService，`agent_skill` 记 `package_file_key`。清单用 `ZipInputStream` 遍历 central directory，单文件下载按路径精确匹配提取。
- 理由：一次存储、零额外表；包内容不可变（编辑走重新上传），提取是纯只读；minio/local 双实现天然兼容
- 代价：每次读包需解压扫描（包 ≤10MB、条目 ≤200，成本可忽略）
- 拒绝方案：拆条目存存储（对象数翻倍、删除清理复杂）或包文件落库（大对象进 PG 不合适）

### D2：zip 安全校验放在解析首步，全部校验通过才落库落存储
读取流后先在内存/临时文件中做三道检查：①每个条目名规范化后必须无 `..` 段、非绝对路径；②条目数 ≤200 且声明解压大小累计 ≤20MB（读 ZipEntry size，超限立即失败，防 zip bomb）；③SKILL.md 定位（包根或唯一一级目录，两级都出现视为多处存在拒绝）。任何一道失败即整体拒绝，**不写存储不建记录**（与 KBIngest 失败清理语义一致的「全有或全无」）。
- frontmatter 解析：手写轻量解析（`---` 起止行内 `name:` / `description:` 简单 KV），不引入 snakeyaml 新依赖；正文取第二个 `---` 之后内容 trim
- name 冲突、description/content 长度校验复用 AgentSkillService 既有校验逻辑

### D3：编辑语义——DB content 为准，包文件是「来源归档 + 参考资料」
上传后 content 仍可在线编辑（activate_skill 与清单始终读 DB），包内文件仅供浏览/下载/AI 加载。两者允许分叉，不做同步。
- 理由：把「SKILL.md 正文」与「包参考资料」职责分开，避免编辑即失效整个包；管理员想更新包 → 重新上传（删除旧技能重建，或后续版本做覆盖）
- 弹窗内对带包技能显示提示文案说明这一语义

### D4：新端点与权限复用
| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/system/skill/package` | POST multipart | System:Skill:Edit | 上传解析建技能，`@Idempotent` + `@RateLimit` |
| `/system/skill/{id}/files` | GET | System:Skill:List | 文件清单（path/size/text 标记） |
| `/system/skill/{id}/files/content` | GET | System:Skill:List | 单文件下载（`Content-Disposition: attachment`，文件名 RFC 5987 编码） |
| `/system/skill/{id}/package` | GET | System:Skill:List | 原包下载 |
无包技能调 files/content/package 返回 404 语义业务错误。删除技能在既有 delete 事务后 best-effort 清理存储（key 判空跳过）。

### D5：AI 工具——load_skill_file 独立工具类方法 + activate_skill 附注
- `load_skill_file(skillName, path)` 加在 `AiSkillTools`（QUERY，无权限码，登录即可）：校验启用 → 定位包 → 只允许提取该包条目（路径必须精确匹配清单，天然免疫穿越）；文本（按扩展名白名单 .md/.txt/.json/.yaml/.yml/.csv/.xml/.html/.ts/.js/.py/.sh/.sql/.java 等代码与配置类）读出 ≤64KB 截断加提示；其余视为二进制返回下载指引
- activate_skill 返回值追加「本技能附带参考文件：path1、path2…（可用 load_skill_file 加载）」，无包不追加
- 系统提示词清单尾部的工具说明同步补三级披露一句（SkillManifestBuilder）
- AiToolRegistry 自动扫描注册，零 switch 改动

### D6：表结构与种子
- 存量库 upgrade SQL（幂等）：`ALTER TABLE agent_skill ADD COLUMN IF NOT EXISTS package_file_key VARCHAR(512)`、`package_file_name VARCHAR(255)`、`package_file_size BIGINT`；`schema-postgres.sql` 同步三列（新库直建）。实体加三字段即可，无新表
- 内置技能种子不变（三列为 NULL）；零新权限码、零新菜单 → DatabaseSeeder 无需改动

### D7：前端
- 技能页 toolbar：ElUpload（accept=".zip"、http-request、show-file-list=false）+「上传技能包」VbenButton（`v-access:code="'System:Skill:Edit'"`）
- 行操作加「文件」：无包禁用；弹窗用 useVbenModal（connectedComponent 模式，`skill-files.vue`），列表展示 path/size/下载按钮 + 原包下载；下载走 `requestClient` blob 或直链（带 token 的 GET，用 blob 响应触发 a[download]）
- locale：`skill.json` 中英同步新增包相关键

## Risks / Trade-offs

- [zip 解析内存占用] → 10MB/20MB 双上限 + 条目数上限，超限即断流失败
- [frontmatter 手写解析兼容性] → 社区包 frontmatter 基本是平铺 KV；解析失败给可读错误提示，管理员可回退手工创建
- [文件名编码] → 下载响应 `Content-Disposition` 用 RFC 5987 `filename*`，中文路径不乱码
- [存储清理孤儿] → 删除清理失败仅告警，孤儿包文件不影响功能；与既有 files 模块清理语义一致

## Migration Plan

1. 存量库执行 upgrade SQL（幂等可重跑）；新库随 schema 自动建列
2. 发版后管理员即可上传包；既有技能数据零迁移

## Open Questions

（无——MVP 范围已在提案确认：scripts 不执行、编辑不同步包、内置技能不改造）
