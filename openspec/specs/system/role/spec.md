# system/role Specification

## Purpose
TBD - created by archiving change add-data-permission-scope. Update Purpose after archive.

## Requirements

### Requirement: 角色数据范围管理
角色管理 SHALL 支持查看与设置角色的数据范围（dataScope）；新建与编辑角色表单 MUST 包含数据范围选择；当选择"自定义部门"时表单 MUST 提供部门树多选并要求至少选择一个部门；角色列表 SHALL 展示数据范围名称。

#### Scenario: 表单展示数据范围
- **WHEN** 管理员打开角色新建/编辑表单
- **THEN** 可见数据范围下拉（5 个选项），编辑时回显当前角色的 dataScope 与自定义部门集合

#### Scenario: 自定义部门级联必填
- **WHEN** 管理员选择数据范围为"自定义部门"且未勾选任何部门即提交
- **THEN** 表单校验失败并提示需选择部门

#### Scenario: 角色列表展示
- **WHEN** 管理员查看角色列表
- **THEN** 数据范围列显示对应中文名称（全部数据/自定义部门/本部门/本部门及以下/仅本人）

#### Scenario: 更新自定义部门集合
- **WHEN** 管理员将某角色数据范围改为"自定义部门"并勾选部门后保存
- **THEN** 保存成功，该角色下的用户下次查询即按新部门集合过滤
