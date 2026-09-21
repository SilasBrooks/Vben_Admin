-- ============================================================
-- Vben Admin 配套后端 RBAC 表结构（对齐若依 RuoYi-Vue 经典模型）
-- MySQL 8.x 版本。首次部署：先建库
--   CREATE DATABASE vben_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- 再执行本文件，然后将 application-prod.yml 中 spring.sql.init.mode 改为 always 跑一次，
-- 完成后改回 never。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(64)  NOT NULL,
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 哈希',
  nickname    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名(对应前端 realName)',
  home_path   VARCHAR(255) NULL COMMENT '登录后首页(可选)',
  dept_id     BIGINT       NULL COMMENT '所属部门id(sys_dept.id),NULL=未归属',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  KEY idx_user_dept (dept_id)
) ENGINE = InnoDB COMMENT ='用户表';

CREATE TABLE IF NOT EXISTS sys_dept (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门id,0=根部门',
  dept_name   VARCHAR(64)  NOT NULL COMMENT '部门名称',
  leader      VARCHAR(64)  NULL COMMENT '负责人姓名(仅存字段)',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  order_num   INT          NOT NULL DEFAULT 0,
  remark      VARCHAR(255) NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_dept_parent (parent_id)
) ENGINE = InnoDB COMMENT ='部门表';

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_key    VARCHAR(64) NOT NULL COMMENT '角色标识(对应前端 roles 数组元素)',
  role_name   VARCHAR(64) NOT NULL,
  sort_num    INT         NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 0,
  remark      VARCHAR(255) NULL,
  data_scope  CHAR(1)     NOT NULL DEFAULT '5' COMMENT '数据范围:1全部 2自定义部门 3本部门 4本部门及以下 5仅本人',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_key (role_key)
) ENGINE = InnoDB COMMENT ='角色表';

-- 数据范围-自定义部门关联表（仅 data_scope=2 时读写）
CREATE TABLE IF NOT EXISTS sys_role_dept (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL COMMENT '角色id',
  dept_id BIGINT NOT NULL COMMENT '部门id(含其子孙部门)',
  UNIQUE KEY uk_role_dept (role_id, dept_id)
) ENGINE = InnoDB COMMENT ='角色自定义数据部门关联表';

CREATE TABLE IF NOT EXISTS sys_menu (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id    BIGINT       NOT NULL DEFAULT 0,
  menu_name    VARCHAR(64)  NOT NULL COMMENT '路由 name(全局唯一)',
  menu_type    CHAR(1)      NOT NULL COMMENT 'M目录 C菜单 F按钮(不进路由树,仅权限码)',
  title        VARCHAR(128) NOT NULL COMMENT '显示名,支持 i18n key 如 page.dashboard.title',
  icon         VARCHAR(128) NULL,
  order_num    INT          NOT NULL DEFAULT 0,
  path         VARCHAR(255) NULL COMMENT '路由 path,以 / 开头',
  component    VARCHAR(255) NULL COMMENT '前端页面组件路径,如 /dashboard/analytics/index',
  redirect     VARCHAR(255) NULL,
  perm         VARCHAR(128) NULL COMMENT '权限码,如 System:User:List / AC_100010',
  visible      TINYINT      NOT NULL DEFAULT 0 COMMENT '0显示 1隐藏(hideInMenu)',
  keep_alive   TINYINT      NOT NULL DEFAULT 0,
  affix_tab    TINYINT      NOT NULL DEFAULT 0 COMMENT '固定标签页',
  authority    VARCHAR(255) NULL COMMENT '访问角色,逗号分隔,映射 meta.authority',
  extra_meta   VARCHAR(1024) NULL COMMENT 'vben meta 扩展 JSON,转换时合并,如 {"badge":"new"}',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_menu_name (menu_name),
  KEY idx_menu_parent (parent_id)
) ENGINE = InnoDB COMMENT ='菜单权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE = InnoDB COMMENT ='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE = InnoDB COMMENT ='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  oper_user_id    BIGINT        NULL COMMENT '操作人id(NULL=匿名)',
  oper_name       VARCHAR(64)   NULL COMMENT '操作人用户名',
  module          VARCHAR(64)   NOT NULL COMMENT '所属模块,如用户管理',
  description     VARCHAR(255)  NULL COMMENT '操作描述',
  method          VARCHAR(255)  NULL COMMENT '调用方法 类#方法',
  request_method  VARCHAR(16)   NULL COMMENT 'HTTP 方法',
  request_url     VARCHAR(255)  NULL,
  params          VARCHAR(2000) NULL COMMENT '入参摘要(脱敏+截断)',
  status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
  error_msg       VARCHAR(2000) NULL,
  ip              VARCHAR(64)   NULL,
  cost_ms         BIGINT        NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  oper_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_oper_log_time (oper_time)
) ENGINE = InnoDB COMMENT ='操作日志表';

CREATE TABLE IF NOT EXISTS sys_login_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(64)  NOT NULL COMMENT '尝试登录的用户名',
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
  message    VARCHAR(255) NULL COMMENT '结果消息',
  ip         VARCHAR(64)  NULL,
  user_agent VARCHAR(512) NULL,
  login_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_login_log_time (login_time)
) ENGINE = InnoDB COMMENT ='登录日志表';

CREATE TABLE IF NOT EXISTS sys_dict_type (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_name   VARCHAR(64)  NOT NULL COMMENT '字典名称,如状态类型',
  dict_type   VARCHAR(64)  NOT NULL COMMENT '字典类型键,全局唯一,如 biz_status',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  remark      VARCHAR(255) NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dict_type (dict_type)
) ENGINE = InnoDB COMMENT ='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_data (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type   VARCHAR(64)  NOT NULL COMMENT '所属字典类型键(逻辑外键)',
  dict_label  VARCHAR(64)  NOT NULL COMMENT '显示名',
  dict_value  VARCHAR(64)  NOT NULL COMMENT '值',
  sort_num    INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  remark      VARCHAR(255) NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dict_value (dict_type, dict_value),
  INDEX idx_dict_data_type (dict_type)
) ENGINE = InnoDB COMMENT ='字典数据表';

CREATE TABLE IF NOT EXISTS sys_file (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  storage_key   VARCHAR(255) NOT NULL COMMENT '存储键(存储实现内部标识,如日期分桶+UUID)',
  size          BIGINT       NOT NULL COMMENT '文件大小(字节)',
  content_type  VARCHAR(128) NOT NULL COMMENT '内容类型(上传时由原始名推导)',
  biz_type      VARCHAR(32)  NOT NULL DEFAULT 'general' COMMENT '业务类型:avatar头像 general通用',
  uploader_id   BIGINT       NOT NULL COMMENT '上传人id(sys_user.id)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_file_key (storage_key),
  INDEX idx_file_uploader (uploader_id)
) ENGINE = InnoDB COMMENT ='文件记录表';

-- 头像列（幂等添加：MySQL 不支持 ADD COLUMN IF NOT EXISTS，用动态 SQL 判断）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(64) NULL COMMENT ''头像文件id(sys_file.id),NULL=未设置''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 个人简介列（幂等添加：MySQL 不支持 ADD COLUMN IF NOT EXISTS，用动态 SQL 判断）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'introduction');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN introduction VARCHAR(255) NULL COMMENT ''个人简介,NULL=未填写''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 邮箱列（幂等添加，同上动态 SQL 判断）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN email VARCHAR(255) NULL COMMENT ''邮箱,NULL=未填写''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 站内通知（落库 + WebSocket 实时推送）
-- update_time 由应用层 MyBatis-Plus MetaObjectHandler 填充（见文件头说明）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_notice (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT        NOT NULL COMMENT '接收人id(sys_user.id)',
  title       VARCHAR(100)  NOT NULL COMMENT '通知标题',
  content     VARCHAR(500)  NULL COMMENT '通知内容',
  msg_type    VARCHAR(20)   NOT NULL DEFAULT 'security' COMMENT '消息类型,本期仅security安全提醒',
  read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_time   DATETIME      NULL COMMENT '阅读时间(NULL=未读)',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sys_notice_user (user_id, read_flag)
) ENGINE = InnoDB COMMENT ='站内通知表';

-- ------------------------------------------------------------
-- IM 单聊消息表（sys_message）
-- update_time 由应用层 MyBatis-Plus MetaObjectHandler 填充（见文件头说明）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_message (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_id        BIGINT        NOT NULL COMMENT '发送人id(sys_user.id)',
  receiver_id      BIGINT        NOT NULL COMMENT '接收人id(sys_user.id)',
  content          VARCHAR(2000) NOT NULL COMMENT '消息内容',
  read_flag        TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_time        DATETIME      NULL COMMENT '阅读时间(NULL=未读)',
  sender_deleted   TINYINT       NOT NULL DEFAULT 0 COMMENT '发送人侧删除标记0否1已删',
  receiver_deleted TINYINT       NOT NULL DEFAULT 0 COMMENT '接收人侧删除标记0否1已删',
  quote_id         BIGINT        NULL COMMENT '被引用消息id(NULL=非引用)',
  quote_content    VARCHAR(2000) NULL COMMENT '被引用消息内容快照',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sys_message_pair (sender_id, receiver_id, id),
  INDEX idx_sys_message_receiver (receiver_id, read_flag)
) ENGINE = InnoDB COMMENT ='IM单聊消息表';

-- ============================================================
-- LLM 模型配置（AI 助手底层模型，OpenAI 兼容协议）
-- update_time 由应用层 MyBatis-Plus MetaObjectHandler 填充（见文件头说明）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_llm_config (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(64)   NOT NULL COMMENT '显示名(唯一)',
  base_url        VARCHAR(255)  NOT NULL COMMENT '接口根地址(OpenAI兼容,不带末尾斜杠)',
  api_key         VARCHAR(255)  NOT NULL DEFAULT '' COMMENT 'API Key(明文入库,接口回显脱敏)',
  model           VARCHAR(64)   NOT NULL COMMENT '模型名,如 deepseek-chat/qwen-plus',
  temperature     DECIMAL(3,1)  NULL COMMENT '采样温度,NULL=不下发由上游默认',
  max_tokens      INT           NULL COMMENT '最大生成token数,NULL=不下发',
  timeout_seconds INT           NOT NULL DEFAULT 60 COMMENT '单次上游请求超时(秒)',
  enabled         TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用(停用不可激活)',
  is_active       TINYINT       NOT NULL DEFAULT 0 COMMENT '1=当前激活模型(全局唯一)',
  remark          VARCHAR(255)  NULL COMMENT '备注',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_llm_name (name)
) ENGINE = InnoDB COMMENT ='LLM模型配置表(OpenAI兼容协议,全局唯一激活)';
