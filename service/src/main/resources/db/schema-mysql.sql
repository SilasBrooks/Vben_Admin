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
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_key    VARCHAR(64) NOT NULL COMMENT '角色标识(对应前端 roles 数组元素)',
  role_name   VARCHAR(64) NOT NULL,
  sort_num    INT         NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 0,
  remark      VARCHAR(255) NULL,
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_key (role_key)
) ENGINE = InnoDB COMMENT ='角色表';

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
