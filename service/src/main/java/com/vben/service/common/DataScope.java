package com.vben.service.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围过滤注解：标注在业务列表查询方法上，
 * 切面会按当前登录用户的角色数据范围（sys_role.data_scope）解析可见边界并放入 {@link DataScopeHolder}，
 * 方法内部构建查询条件时 MUST 读取 Holder 并追加过滤。
 *
 * <p>五档语义（对齐若依）：
 * 1=全部数据；2=自定义部门(sys_role_dept)；3=本部门；4=本部门及以下；5=仅本人。
 * 多角色取并集；任一角色为 1 直接放行；超级管理员恒为全部数据。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
}
