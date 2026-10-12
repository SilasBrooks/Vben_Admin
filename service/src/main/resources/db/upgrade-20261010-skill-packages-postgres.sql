-- ============================================================
-- 技能包（agent-skills-packages）：agent_skill 增加技能包文件三元组
-- 幂等：可重复执行；新库由 schema-postgres.sql 直建无需执行
-- ============================================================
ALTER TABLE agent_skill ADD COLUMN IF NOT EXISTS package_file_key  VARCHAR(512);
ALTER TABLE agent_skill ADD COLUMN IF NOT EXISTS package_file_name VARCHAR(255);
ALTER TABLE agent_skill ADD COLUMN IF NOT EXISTS package_file_size BIGINT;

COMMENT ON COLUMN agent_skill.package_file_key  IS '技能包zip存储键(NULL=无包的手工技能)';
COMMENT ON COLUMN agent_skill.package_file_name IS '技能包原始zip文件名';
COMMENT ON COLUMN agent_skill.package_file_size IS '技能包字节数';
