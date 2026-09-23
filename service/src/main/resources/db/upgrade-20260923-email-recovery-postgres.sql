-- 存量 PostgreSQL：仅新增安全邮箱表，不将未验证的联系邮箱迁移为安全邮箱。
CREATE TABLE IF NOT EXISTS sys_user_recovery_email (
  user_id BIGINT PRIMARY KEY REFERENCES sys_user(id) ON DELETE CASCADE,
  email VARCHAR(255) NOT NULL,
  verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
