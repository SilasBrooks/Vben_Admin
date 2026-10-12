## Tasks

### 1. 后端：协议字段与客户端适配

- [x] 1.1 实体 `EmbeddingConfig` 加 `protocol` 字段；`schema-postgres.sql` 加列；新建 `db/upgrade-20261010-emb-protocol-postgres.sql`（幂等 ADD COLUMN DEFAULT 'standard'）
- [x] 1.2 `EmbeddingConfigService`：保存/校验透传 protocol（白名单 standard/ark-multimodal，非法拒绝）；ping/测试回填维度逻辑不变；单测补协议校验与透传用例
- [x] 1.3 `EmbeddingClient`：按 protocol 分支请求路径（/embeddings vs /embeddings/multimodal）与 input 结构（文本数组 vs `[{"type":"text","text":...}]`）；响应解析兼容 data 数组/对象两形态；404 错误文案携带实际请求路径；单测补多模态请求体/两形态解析/路径拼接用例

### 2. 前端

- [x] 2.1 `api/system/embedding.ts` 类型加 protocol；`embedding-form.vue` 协议下拉（默认标准 + 说明文案）；locales `embedding.json` 中英同步
- [x] 2.2 `pnpm check:type` 通过

### 3. 验证与收尾

- [x] 3.1 `mvn -q compile` + `mvn test` 全绿；存量库执行 upgrade SQL 并验证默认值
- [x] 3.2 全链路：编辑 doubao 配置（模型名 `doubao-embedding-vision-251215` + ark-multimodal）→ 连通测试回填维度 → 激活 → 删除旧文档重传 → 摄取完成 → 检索测试命中 → AI 对话引用出处
- [x] 3.3 README 注意事项补一句方舟 vision 协议选型说明
- [x] 3.4 `openspec validate ark-multimodal-embedding --strict` 通过 → `openspec archive ark-multimodal-embedding --yes`
