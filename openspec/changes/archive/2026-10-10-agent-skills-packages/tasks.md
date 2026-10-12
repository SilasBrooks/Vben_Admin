## Tasks

### 1. 后端：表结构与实体

- [x] 1.1 `schema-postgres.sql` agent_skill 增加三列（package_file_key / package_file_name / package_file_size），新建 `db/upgrade-20261010-skill-packages-postgres.sql`（幂等 ALTER ADD COLUMN IF NOT EXISTS）
- [x] 1.2 `AgentSkill` 实体新增三字段（注释说明：包存储键/原始文件名/字节数，NULL=无包手工技能）

### 2. 后端：技能包上传解析与文件服务

- [x] 2.1 新建 `SkillPackageParser`：zip 条目安全校验（路径穿越拒绝、条目数 ≤200、解压总量 ≤20MB）、SKILL.md 定位（包根或唯一一级目录）、frontmatter 轻量解析（name/description/正文），单测覆盖：标准包/缺 SKILL.md/穿越条目/超限/多处 SKILL.md/坏 frontmatter
- [x] 2.2 `AgentSkillService` 扩展：uploadPackage（校验复用 name/description/content 规则 + 重名拒绝，原包 StorageService.store，落库 enabled=0/builtIn=0，全有或全无）、listPackageFiles（ZipInputStream 清单：path/size/text 标记）、readPackageFile（精确匹配提取 + 文本截断 64KB）、openPackage（原包流）；delete 时 best-effort 清理包文件；单测 mock mapper + StorageService 覆盖成功/重名/穿越/超限/删除清理分支
- [x] 2.3 `SkillController` 新端点：POST `/system/skill/package`（multipart，System:Skill:Edit，@Idempotent + @RateLimit）、GET `/{id}/files`、GET `/{id}/files/content`、GET `/{id}/package`（均 System:Skill:List，下载 Content-Disposition RFC 5987）

### 3. 后端：AI 工具三级披露

- [x] 3.1 `AiSkillTools` 新增 `load_skill_file` QUERY 工具（name+path，启用校验、文本白名单读取 64KB 截断、二进制返回下载指引、错误如实告知），单测覆盖命中/停用/二进制/超长截断
- [x] 3.2 `activate_skill` 返回正文追加包内文件清单附注（无包不追加）；`SkillManifestBuilder` 系统提示词工具说明补三级披露描述，单测同步

### 4. 前端

- [x] 4.1 `api/system/skill.ts` 新增 uploadSkillPackageApi（FormData）/getSkillFilesApi/downloadSkillFileApi/downloadSkillPackageApi
- [x] 4.2 `views/system/skill/index.vue`：toolbar「上传技能包」（ElUpload http-request + System:Skill:Edit）+ 行操作「文件」（无包禁用）+ 新建 `skill-files.vue` 弹窗（文件清单/大小/单文件下载/原包下载）
- [x] 4.3 locales `skill.json` 中英同步新增键（上传/文件弹窗/错误提示），`pnpm check:type` 通过

### 5. 验证与收尾

- [x] 5.1 后端 `mvn -q compile` + `mvn test` 全绿；存量库 vben5 执行 upgrade SQL 并验证三列
- [x] 5.2 E2E：构造测试技能包上传 → 列表出现默认停用技能 → 文件弹窗清单/下载 → 启用后 AI 对话 activate_skill 返回附注 → load_skill_file 加载参考文档
- [x] 5.3 README「AI 智能助手」技能段落补包格式说明
- [x] 5.4 `openspec validate agent-skills-packages --strict` 通过 → `openspec archive agent-skills-packages --yes`（sync delta → archive）
