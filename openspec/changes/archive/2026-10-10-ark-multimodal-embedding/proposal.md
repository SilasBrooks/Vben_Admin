## Why

火山方舟的文本版 embedding 模型全线进入下线流程（240515/240715/240915/250515 均标注「即将下线」，实测开通不生效、API 返回 NotFound），方舟推荐的替代是 `doubao-embedding-vision` 系列——但该模型仅提供 `/embeddings/multimodal` 结构化协议，与本项目 EmbeddingClient 使用的标准 OpenAI `/embeddings` 协议不兼容。不做适配，方舟用户将没有任何可用的 embedding 选型。

## What Changes

- Embedding 配置新增「协议风格」字段（`protocol`：`standard` 标准 OpenAI / `ark-multimodal` 方舟多模态），标准协议行为完全不变
- `ark-multimodal` 协议：请求发往 `{baseUrl}/embeddings/multimodal`，纯文本输入包装为 `[{"type":"text","text":...}]` 结构；响应兼容 `data` 为对象或数组两种形态
- 新增/编辑表单提供协议风格选择（默认标准），连通测试、知识库摄取与检索自动沿用所选协议
- 存量库幂等 upgrade SQL（加列默认 `standard`）；零新权限码、零新菜单

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `system/embedding-config`：配置模型增加协议风格字段；协议范围从「仅 OpenAI 兼容」扩展为「标准 OpenAI 兼容 + 方舟多模态」两种；前端表单增加协议选择

## Impact

- 后端：`module/ai/emb/`（实体加列、EmbeddingClient 请求构造分支与响应解析兼容、Service 保存/校验透传）、`db/`（schema + 幂等 upgrade SQL）
- 前端：`views/system/embedding/embedding-form.vue`（协议下拉）、`api/system/embedding.ts`（类型）、locales 中英键
- 兼容性：存量配置 protocol 默认 `standard`，行为零变化；知识库摄取/检索无需感知协议差异
