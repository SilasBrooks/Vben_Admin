# delta: platform/docker-deploy

## MODIFIED Requirements

### Requirement: 前端网关能力

前端容器 SHALL 通过 nginx 提供静态资源托管与 SPA 路由回退，并 MUST 满足：`/api` 反代透传 Cookie 与 Authorization 头；请求体上限不小于 10MB（支持文件上传）；AI 助手 SSE 流式响应不被缓冲（逐步可见而非一次性返回）。生产构建产物 MUST 附带预压缩文件（gzip），nginx MUST 优先直接发送预压缩文件（`gzip_static`）而非每请求实时压缩，并 MUST 响应 `Vary: Accept-Encoding` 以避免代理缓存错配。

#### Scenario: 头像直链可显示

- **WHEN** 用户在容器化环境中上传头像后刷新页面
- **THEN** 头像图片经 `/api` 反代正常显示（GET /file/*/content 的 Cookie 回退认证可用）

#### Scenario: 文件上传不受体积拦截

- **WHEN** 上传一个 8MB 的合法文件
- **THEN** 上传成功，nginx 网关不因默认 1MB 限制返回 413

#### Scenario: AI 回复流式输出

- **WHEN** 用户在 AI 助手中提问且已配置大模型 Key
- **THEN** 回复内容逐步流式呈现，而非长时间空白后一次性出现

#### Scenario: 预压缩静态托管

- **WHEN** 浏览器（Accept-Encoding 含 gzip）请求生产环境的 JS/CSS 静态资源
- **THEN** nginx 直接发送构建时预压缩的 `.gz` 文件（不进行每请求实时压缩），响应头含 `Content-Encoding: gzip` 与 `Vary: Accept-Encoding`
