# 任务清单：add-data-permission-scope

## 1. 数据库与实体

- [x] 1.1 `sys_role` 增加 `data_scope` 列、新建 `sys_role_dept` 表：同步追加到 `db/schema-postgres.sql` 与 `db/schema-mysql.sql`（幂等），启动后端验证建表成功
- [x] 1.2 `SysRole` 实体加 `dataScope` 字段、新建 `SysRoleDept` 实体与 Mapper，编译通过

## 2. 数据范围核心能力

- [x] 2.1 新建 `common/DataScope.java` 注解与 `common/DataScopeHolder`（ThreadLocal 过滤上下文：deptIds 集合/includeUnassigned/all 标记/仅本人 userId），验证类结构编译通过
- [x] 2.2 新建 `DataScopeService.resolve(user)`：按 5 档语义解析可见部门集合（范围 4/2 递归含子孙部门，多角色并集，任一为 1 直接放行，超管恒放行）
- [x] 2.3 新建 `DataScopeAspect`（@Around 拦截 @DataScope 注解，方法前解析放入 Holder、finally 清理）

## 3. 接入用户列表

- [x] 3.1 `SysUserAdminService` 用户列表方法标注 `@DataScope` 并按 Holder 追加 Wrapper 条件（`deptId IN (...) OR deptId IS NULL` / 仅本人 `id=当前用户`），编译通过
- [x] 3.2 curl 验收：stockAdmin（dataScope=4 仓储部）查用户列表只见仓储部用户+未归属+本人；vben（超管）见全部；user（dataScope=5）仅见本人——三条请求逐一核对返回数量与边界
  （实测：vben=4 条全部；stockAdmin=4 条（仓储部本人+3 个未归属用户，符合"含未归属"设计）；jack 无 System:User:List 权限 403（功能权限拦截符合预期）；SELF 档改用 stock 角色切换验证，见 4.2）

## 4. 角色管理接口

- [x] 4.1 角色详情返回 `dataScope` 与 `deptIds`（仅范围 2 非空）；新增 `PUT /system/role/dataScope`（`System:Role:Auth` 权限码 + `@OperLog`）保存 dataScope 与自定义部门集合（范围≠2 时清空关联表记录）
- [x] 4.2 curl 验收：设置某角色为自定义部门→回读 detail 字段一致；范围切回 3→关联表记录清空；无 `System:Role:Auth` 权限的用户调接口 403
  （实测 7 项全过：初始回读 4/[]、设为 2+deptIds[4] 后 stockAdmin 查 4 条、回读 2/[4]、切 3 后 total=4 且 deptIds=[]、切 5 后仅见 stockAdmin 本人、恢复 4、jack 调更新接口 403）

## 5. 前端角色管理

- [x] 5.1 `api/system/role.ts` 类型补 `dataScope`/`deptIds` 与更新接口；`role/form.vue` 加"数据范围"RadioGroup（5 档中文），选"自定义部门"时展示部门树多选（必填校验），编辑回显
- [x] 5.2 `role/index.vue` 加"数据范围"列（前端常量映射中文名），`vue-tsc --noEmit` 通过
- [x] 5.3 浏览器验收：编辑 stockAdmin 所属角色改数据范围→用 stockAdmin 重新登录查用户列表，可见范围随配置变化；角色列表列显示正确
  （实测 3 轮浏览器验收通过：列表"数据范围"列中文显示正确；stock 表单 5 档 RadioGroup、自定义部门树多选勾选"仓储部"保存成功、重新编辑回显正确；super 表单数据范围与角色标识均禁用且恒为全部数据）

## 6. 收尾

- [x] 6.1 `docs/tech-overview.md` 数据库表清单补 `sys_role_dept`；roadmap.md 勾选 3.1
- [x] 6.2 全量回归：用户/角色/部门增删改查、日志、字典各跑一遍核心接口，无回归问题
  （实测 14 项接口全过：用户/角色/部门 CRUD、字典类型/数据/下拉选项、菜单列表、角色菜单 ids、操作/登录日志分页）
