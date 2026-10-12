-- ============================================================
-- 2026-10-10 Embedding 协议风格字段（方舟 vision 多模态协议适配）
-- 幂等：可重复执行
-- ============================================================
ALTER TABLE sys_embedding_config ADD COLUMN IF NOT EXISTS protocol VARCHAR(32) NOT NULL DEFAULT 'standard';
COMMENT ON COLUMN sys_embedding_config.protocol IS '协议风格:standard=OpenAI兼容/embeddings,ark-multimodal=方舟/embeddings/multimodal';
