package com.vben.service.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限码校验注解
 *
 * <pre>{@code
 * @RequirePermission("System:User:List")
 * @GetMapping("/system/user/list")
 * public R<...> list() { ... }
 * }</pre>
 *
 * 权限码来源于 sys_menu.perm（按钮型菜单），由角色分配。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

  /** 满足其一即可通过（or 语义） */
  String[] value();
}
