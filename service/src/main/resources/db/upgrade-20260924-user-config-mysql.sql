-- 个人列表配置；可在存量 MySQL 库重复执行。
CREATE TABLE IF NOT EXISTS sys_user_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  config_key   VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  config_value TEXT         NOT NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_config_key (user_id, config_key)
) ENGINE = InnoDB COMMENT ='用户个人配置(JSON数组)';
