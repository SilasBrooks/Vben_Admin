-- 存量库升级：sys_notice 增加发布人快照列（ PostgreSQL 版）
-- 执行目标：所有已初始化过的存量库（新库由 schema-postgres.sql 自动建列，无需执行）
ALTER TABLE sys_notice ADD COLUMN IF NOT EXISTS publisher VARCHAR(64) NULL;
COMMENT ON COLUMN sys_notice.publisher IS '发布人登录名快照(公告广播时固化,安全通知为null)';
