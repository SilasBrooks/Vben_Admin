## 1. 后端：数据模型与种子

- [x] 1.1 新建 `SysDept` 实体 + Mapper（id、parentId、deptName、leader、status、orderNum、remark、通用字段）
- [x] 1.2 `SysUser` 实体增加 `deptId` 字段（H2 schema 同步加列）
- [x] 1.3 `DatabaseSeeder`：写入部门种子树（总公司→研发部/运营部/仓储部）；stockAdmin 归属仓储部
- [x] 1.4 `DatabaseSeeder`：写「部门管理」菜单 + 4 个按钮权限 + super 角色授权关联

## 2. 后端：部门接口

- [x] 2.1 `SysDeptAdminService`：树查询（递归组装 children、排序）、新增（同级重名校验、父部门校验）、修改（防环校验 + 重名校验）、删除（子部门/用户占用校验）
- [x] 2.2 `SystemDeptController`：list / save / update / delete 四个接口，`@RequirePermission` 挂 `System:Dept:List/Add/Edit/Delete`
- [x] 2.3 编译通过（`mvn compile`）

## 3. 后端：用户联动

- [x] 3.1 用户保存/修改支持 deptId；详情与分页列表填充 deptName（批量查部门名）
- [x] 3.2 编译通过

## 4. 前端：部门管理页面

- [x] 4.1 `api/system/dept.ts`：树查询/保存/更新/删除接口定义
- [x] 4.2 `views/system/dept/index.vue`：useVbenVxeGrid 树形表格 + 工具栏/行操作按钮挂 `v-access:code`（`System:Dept:Add/Edit/Delete`）
- [x] 4.3 `views/system/dept/dept-form.vue`：useVbenModal + useVbenForm（父部门 TreeSelect 排除自身及子孙）
- [x] 4.4 typecheck 通过（`vue-tsc --noEmit`）

## 5. 前端：用户管理联动

- [x] 5.1 `user-form.vue` 增加部门 TreeSelect（可选字段）
- [x] 5.2 用户列表增加「所属部门」列
- [x] 5.3 typecheck 通过

## 6. 验收（对照 specs 场景）

- [x] 6.1 重置 H2 数据库文件并重启后端，种子部门树正确显示
- [x] 6.2 vben 账号：部门增删改查全流程 + 同级重名/防环/占用删除三个 400 场景
- [ ] 6.3 stockAdmin 账号：不可见部门管理菜单，直接访问路由返回布局内 404/无权限
- [ ] 6.4 用户管理：设置/变更部门、列表展示部门名
