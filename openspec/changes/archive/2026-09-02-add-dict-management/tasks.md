# Tasks: add-dict-management

## 1. 后端数据模型与种子

- [x] 1.1 `schema-h2.sql` / `schema-mysql.sql` 新增 `sys_dict_type`、`sys_dict_data` 两表
- [x] 1.2 `SysDictType` / `SysDictData` 实体 + Mapper
- [x] 1.3 `DatabaseSeeder` 加「数据字典」菜单（System 目录 order 5）+ System:Dict:List/Add/Edit/Delete 权限

## 2. 后端接口

- [x] 2.1 `SysDictAdminService`：类型 CRUD（dictType 唯一校验、级联删数据）、数据 CRUD（同类型 value 唯一）、options 查询
- [x] 2.2 `SystemDictController`：dict-type 与 dict-data 接口 + options 接口（不挂权限码），写接口标 `@OperLog`

## 3. 前端

- [x] 3.1 `api/system/dict.ts` + `hooks/use-dict.ts`（模块级缓存）
- [x] 3.2 `views/system/dict/index.vue` 类型列表页 + `dict-type-form.vue`
- [x] 3.3 `views/system/dict/dict-data-modal.vue` + `dict-data-form.vue`

## 4. 验收

- [x] 4.1 编译 + 重置 H2 重启后端
- [x] 4.2 curl：建类型/建数据 → options 返回启用项；重复 dictType/value → 400；删类型级联删数据；jack 调管理接口 → 403（options 仅登录可用）
- [x] 4.3 typecheck 通过
- [x] 4.4 浏览器：数据字典页类型/数据 CRUD、权限按钮控制正常（用户已验证）
