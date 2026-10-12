## Context

实测结论（2026-10-10）：方舟文本版 embedding 全线即将下线且开通不生效（`doubao-embedding-large-text-250515` 等 4 个变体在标准与 multimodal 两个端点均返回 `InvalidEndpointOrModel.NotFound`），而已开通的 `doubao-embedding-vision-251215` 经 Model ID 直调 `/embeddings/multimodal` 秒通。vision 接口与标准 OpenAI 协议差异仅在请求路径与 input 结构，响应形态仍为 `data`（需兼容对象/数组两种形态）。

## Goals / Non-Goals

**Goals**
- 方舟 vision 系列开箱可用：配置选 `ark-multimodal` 协议即可连通测试、摄取知识库、AI 检索引用
- 标准协议零影响：存量配置默认 `standard`，请求路径与报文不变

**Non-Goals**
- 不支持 vision 的图片输入（知识库场景纯文本，input 仅构造 text 类型）
- 不做多 embedding 模型混存兼容（既有约定：换维度需重建知识库）

## Decisions

### D1：显式 `protocol` 列，不做模型名嗅探
`sys_embedding_config` 新增 `protocol VARCHAR(32) NOT NULL DEFAULT 'standard'`，合法值 `standard` / `ark-multimodal`，Service 保存时校验。理由：用户偏好「每行代码有明确业务含义，不搞魔法」——模型名包含 "vision" 即切换协议属于隐式魔法，且方舟未来可能出现非 vision 命名的多模态模型。表单下拉默认「标准 OpenAI 协议」，旁边附说明文案（何时选方舟多模态）。

### D2：协议差异收敛在 EmbeddingClient 一处
- 请求路径：standard → `{baseUrl}/embeddings`（沿用既有归一化逻辑）；ark-multimodal → `{baseUrl}/embeddings/multimodal`（baseUrl 归一化剥掉末尾斜杠后直接拼接）
- 请求体：standard → `{"model":..., "input":["文本"]}`；ark-multimodal → `{"model":..., "input":[{"type":"text","text":"文本"}]}`
- 响应解析：统一容忍 `data` 为数组（standard 形态）或单对象（ark-multimodal 实测形态，`data.embedding` 直接取值）；按 index 排序逻辑仅数组形态需要
- ping/test、KbIngest、KbSearch 全部经由 EmbeddingClient.embed，上层零改动

### D3：错误信息区分协议路径提示
上游 404 时错误文案带上实际请求路径（如 `.../embeddings/multimodal`），用户配错协议风格时能一眼看出打到了哪个端点。

## Risks / Trade-offs

- [ark-multimodal 响应形态未来变化] → 解析同时兼容数组/对象两形态，方舟改回数组也无需改代码
- [协议字段写错] → 连通测试即报错（路径 404 文案含端点提示），且测试不回填维度不会污染配置

## Migration Plan

1. 存量库执行 `upgrade-20261010-emb-protocol-postgres.sql`（幂等加列，默认 standard）
2. 将 doubao 配置的模型名改为 `doubao-embedding-vision-251215`、protocol 改为 `ark-multimodal`（管理页编辑即可）
3. 连通测试 → 激活 → 删除旧 mock 维度文档重新上传 → 全链路验证
