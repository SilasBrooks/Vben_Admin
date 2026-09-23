-- 存量 MySQL：仅新增安全邮箱表，不将未验证的联系邮箱迁移为安全邮箱。
CREATE TABLE IF NOT EXISTS sys_user_recovery_email (
  user_id BIGINT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  verified_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recovery_email_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='已验证的安全邮箱';
