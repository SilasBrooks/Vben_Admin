package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单权限表（M 目录 / C 菜单 / F 按钮）
 *
 * <p>目录与菜单参与前端动态路由生成；按钮不进路由树，其 perm 汇总为
 * /auth/codes 返回的权限码，用于前端 v-access 指令与后端 @RequirePermission 校验。
 */
@Data
@TableName("sys_menu")
public class SysMenu {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long parentId;

  /** 路由 name，全局唯一（如 Dashboard / SystemMenu） */
  private String menuName;

  /** M 目录 C 菜单 F 按钮 */
  private String menuType;

  /** 显示名，支持 i18n key（如 page.dashboard.title） */
  private String title;

  private String icon;

  private Integer orderNum;

  /** 路由 path，以 / 开头（如 /analytics） */
  private String path;

  /** 前端组件路径（如 /dashboard/analytics/index），menu_type=C 时必有 */
  private String component;

  private String redirect;

  /** 权限码（如 System:User:List / AC_100010） */
  private String perm;

  /** 0 显示 1 隐藏(hideInMenu) */
  private Integer visible;

  private Integer keepAlive;

  /** 是否固定标签页 affixTab */
  private Integer affixTab;

  /** 访问角色，逗号分隔（映射 meta.authority，如 super,admin） */
  private String authority;

  /** vben meta 扩展 JSON（如 {"badge":"new","menuVisibleWithForbidden":true}） */
  private String extraMeta;

  /** 0 正常 1 停用 */
  private Integer status;

  private LocalDateTime createTime;

  /** 子树（非表字段，仅用于树形接口返回） */
  @TableField(exist = false)
  private List<SysMenu> children;
}
