-- 知识库混合检索升级：kb_chunk 增加全文索引列（应用侧 bigram 分词，存量由应用启动回填）
-- 幂等：可重复执行；content_tsv 数据回填不在此处（SQL 初始化器不支持 dollar-quoted，见 ApplicationRunner KbTsvBackfill）

ALTER TABLE kb_chunk ADD COLUMN IF NOT EXISTS content_tsv tsvector NULL;

COMMENT ON COLUMN kb_chunk.content_tsv IS '全文索引(应用侧中文bigram+英文按词分词,to_tsvector(simple)入库,启动回填存量)';

CREATE INDEX IF NOT EXISTS idx_kb_chunk_fts ON kb_chunk USING gin (content_tsv);
