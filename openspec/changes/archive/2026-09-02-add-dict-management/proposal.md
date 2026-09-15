## Why

系统下拉选项（状态、类型等）目前全部硬编码在前端，修改一个选项要发版。数据字典让枚举值运营可配，是通用后台的必备基础能力（roadmap P2-2.1，规划内最后一项底座功能）。

## What Changes

- 新增 `sys_dict_type`（字典类型：dictName + 唯一 dictType）与 `sys_dict_data`（字典数据：label/value/sort，按 dictType 归组）两表
- 后端：类型管理（list/save/update/delete，删除类型级联删其数据项）+ 数据管理（按类型分页 list/save/update/delete）+ `options/{dictType}` 下拉选项接口（仅要求登录，供表单下拉取值）
- 校验：dictType 全局唯一；同一类型下 dictValue 唯一
- 前端：`views/system/dict/` 类型列表页 + 「数据管理」弹窗（类型内数据 CRUD）；`useDict()` hook（模块级缓存，供下拉取值）
- 菜单种子：「数据字典」挂在系统管理下（order 5）+ 4 个按钮权限，super/admin 可见
- 写接口标注 `@OperLog`（模块「字典管理」）

## Capabilities

### New Capabilities

- `system/dict`: 数据字典能力——字典类型/字典数据的管理与下拉选项查询，权限码 `System:Dict:List/Add/Edit/Delete`

## Impact

- **后端**：`module/system/` 新增 SysDictType/SysDictData 实体、Mapper、SysDictAdminService、SystemDictController；schema 两表；DatabaseSeeder 加菜单
- **前端**：`api/system/dict.ts`、`views/system/dict/*`、`hooks/use-dict.ts`
- **数据**：H2 重建即可，无线上迁移
- **不改动**：现有页面的硬编码下拉本次不强制替换，后续按需迁移
