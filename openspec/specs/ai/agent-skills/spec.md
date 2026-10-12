# ai/agent-skills Specification

## Purpose
提供 Claude 式 Agent Skills 能力包管理：把「指导模型如何做事」的方法论（操作手册、写作规范、排查套路）做成管理员可维护的数据化技能，经渐进式披露（清单常驻、正文按需加载）动态扩展 AI 助手能力而不改代码。

## Requirements

### Requirement: 技能管理

系统 SHALL 提供技能管理接口（`/system/skill`）：分页查询（System:Skill:List）、新增（System:Skill:Add，幂等 + 限流）、编辑（System:Skill:Edit）、删除（System:Skill:Delete，审计）、启停（System:Skill:Edit）。每条技能 SHALL 包含：name（全局唯一，小写字母/数字/连字符，如 `announcement-writer`）、title（中文能力名）、description（给模型的场景描述：什么需求该用本技能）、content（技能正文，Markdown 指令）、enabled 启停、builtIn 内置标记，以及可选的技能包文件三元组（package_file_key 存储键、package_file_name 原始 zip 文件名、package_file_size 字节数，为空表示无包的手工技能）。

约束 MUST 校验：name 格式与唯一性；description ≤300 字符；content ≤8000 字符；同时启用（enabled=1）的技能 MUST 不超过 20 条，超限拒绝启用操作。内置技能（builtIn=1）MUST 可编辑可停用但 MUST NOT 删除。删除带包技能时，系统 MUST 同步清理文件存储中的包文件（清理失败 MUST NOT 阻断删除，记录告警日志）。

#### Scenario: name 格式校验
- **WHEN** 新增技能时 name 提交为「写公告」（非小写连字符格式）
- **THEN** 返回格式错误业务提示，不创建

#### Scenario: 启用数量上限
- **WHEN** 已启用 20 条技能时再启用第 21 条
- **THEN** 返回「启用技能数量已达上限」业务错误，状态不变

#### Scenario: 内置技能禁删
- **WHEN** 删除 builtIn=1 的内置技能
- **THEN** 返回业务错误，技能保留；停用操作正常执行

#### Scenario: 删除带包技能清理存储
- **WHEN** 删除一条带技能包的技能
- **THEN** 技能记录删除，文件存储中的包文件被同步清理；清理异常时技能仍删除成功且不影响接口响应

### Requirement: 内置示范技能

系统 SHALL 随种子数据内置至少 2 个示范技能并默认启用，使概念开箱可验证：「公告写作助手」（按站内公告规范起草公告正文，起草后引导使用公告发布能力）与「故障排查手册」（连接拒绝/服务不可用类问题的标准排查步骤）。内置技能随 DatabaseSeeder（空库）与存量库 upgrade SQL 双通道写入，内容 MUST 与实现文档保持一致。

#### Scenario: 空库自带技能
- **WHEN** 全新环境执行种子初始化
- **THEN** AI 技能页可见内置技能且处于启用状态，AI 对话可立即加载使用

### Requirement: 前端技能管理页面

前端 SHALL 在「系统管理」下提供 AI 技能页面（`/system/skill`）：列表（名称/title/描述摘要/启停/内置标记/时间）+ 新增/编辑弹窗（name/title/description 用表单 schema，content 用多行文本编辑并显示字数）+ 启停开关 + 删除（二次确认，内置技能删除按钮禁用），操作按钮以 System:Skill:Add/Edit/Delete 权限码控制。页面 SHALL 另提供：工具栏「上传技能包」入口（System:Skill:Edit 权限，选择 .zip 文件上传，成功后列表刷新且新技能默认停用）；行操作「文件」弹窗（System:Skill:List 权限），展示该技能包内文件清单（路径/大小）并支持单文件下载与原包下载，无包技能该按钮禁用。

#### Scenario: 正文编辑字数提示
- **WHEN** 编辑技能正文输入超过 8000 字符
- **THEN** 表单校验拦截并提示上限，不提交

#### Scenario: 无权限按钮隐藏
- **WHEN** 无 System:Skill:Delete 权限的账号进入该页面
- **THEN** 不渲染删除按钮

#### Scenario: 上传技能包成功
- **WHEN** 管理员选择合法的技能包 zip 上传
- **THEN** 提示创建成功，列表出现新技能（默认停用、带包标记），无需手工填写表单

#### Scenario: 无包技能文件入口禁用
- **WHEN** 查看手工创建（无包）技能的行操作
- **THEN**「文件」按钮禁用，不可打开文件清单弹窗

### Requirement: 技能包上传与解析

系统 SHALL 支持上传符合 Claude Agent Skills 目录包结构的 zip 压缩包（`skill-name/SKILL.md` + 可选 `references/`、`assets/`、`scripts/`）创建技能：接口挂 System:Skill:Edit 权限码并施加幂等与限流；包大小 MUST ≤10MB、zip 条目数 MUST ≤200、解压后总大小 MUST ≤20MB，超限拒绝；SKILL.md MUST 位于包根或唯一一级目录下，缺失或多处存在时拒绝；解析 SKILL.md YAML frontmatter 取 name 与 description、正文作为 content，name/description/content 沿用技能管理同等的格式与长度校验，name 已存在时拒绝并提示；创建的技能 MUST 默认停用（enabled=0）且 builtIn=0。MUST 拒绝包含路径穿越条目（`../` 前缀、绝对路径）的 zip。scripts 类文件仅随包存储与展示，系统 MUST NOT 提供 Agent 执行脚本的任何途径。

#### Scenario: 上传标准技能包
- **WHEN** 上传包含 `code-reviewer/SKILL.md`（frontmatter 含 name 与 description）与 `references/spec.md` 的 zip
- **THEN** 创建技能 `code-reviewer`（description 取 frontmatter，content 取正文），默认停用，包内文件可浏览下载

#### Scenario: 缺少 SKILL.md
- **WHEN** 上传不含 SKILL.md 的 zip
- **THEN** 返回业务错误「包内缺少 SKILL.md」，不创建技能

#### Scenario: name 冲突拒绝
- **WHEN** 上传的技能包 frontmatter name 与既有技能重名
- **THEN** 返回重名业务错误，既有技能与存储文件均不变

#### Scenario: 路径穿越拒绝
- **WHEN** zip 内存在 `../evil.sh` 或 `/etc/passwd` 形式的条目
- **THEN** 拒绝整个包并返回安全校验错误，任何条目不落库不落存储

### Requirement: 包文件浏览与下载

系统 SHALL 提供带包技能的文件浏览与下载接口：文件清单（System:Skill:List）返回包内全部条目的路径、大小与是否文本文件；单文件下载（System:Skill:List）按包内路径精确匹配提取返回，路径不存在返回 404；原包下载（System:Skill:List）返回上传时的原始 zip。文件读取 MUST 为只读提取，MUST NOT 修改包内容；文本文件用于内容预览与 AI 工具加载，二进制文件仅提供下载。

#### Scenario: 查看文件清单
- **WHEN** 打开带包技能的文件弹窗
- **THEN** 展示包内全部文件（含 references/assets/scripts 各目录条目）的相对路径与大小

#### Scenario: 下载单个参考文档
- **WHEN** 在文件清单中下载 `references/api-spec.md`
- **THEN** 返回该条目的文件内容，文件名取路径末段

#### Scenario: 路径不存在
- **WHEN** 请求包内不存在的路径
- **THEN** 返回 404，不泄露包外任何文件

### Requirement: load_skill_file 渐进式披露第三级

AI 对话 SHALL 新增 `load_skill_file` 查询工具（无权限码，登录即可调用，与 activate_skill 同等可见性）：按技能 name 与包内路径加载文本文件内容；仅启用技能可加载，停用或不存在时如实告知；文本文件内容超过上限（64KB）时截断并提示；二进制文件返回「请提示管理员在技能管理页下载」的指引而非文件内容。activate_skill MUST 在返回技能正文时附带该技能包内可用文件清单（无包则不附），引导模型按需调用 load_skill_file 深入阅读。系统提示词的技能工具说明 MUST 向模型说明三级披露用法（清单 → 正文 → 参考文件）。

#### Scenario: 加载参考文档
- **WHEN** 模型调用 load_skill_file(skillName="code-reviewer", path="references/spec.md")
- **THEN** 返回该文件文本内容，模型据此执行任务

#### Scenario: 停用技能不可加载
- **WHEN** 模型调用 load_skill_file 指向已停用技能
- **THEN** 返回「技能不存在或已停用」的如实说明，不返回文件内容

#### Scenario: 二进制文件指引
- **WHEN** 模型调用 load_skill_file 加载 `assets/logo.png`
- **THEN** 返回二进制文件的下载指引文案，不返回二进制内容
