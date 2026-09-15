# system/dict Delta

## ADDED Requirements

### Requirement: 字典类型管理
系统 SHALL 提供字典类型管理接口：查询全部类型（System:Dict:List）、新增（System:Dict:Add）、修改（System:Dict:Edit）、删除（System:Dict:Delete）。dictType MUST 全局唯一；删除类型 MUST 同事务级联删除该类型全部字典数据。

#### Scenario: dictType 重复
- **WHEN** 新增类型时提交已存在的 dictType
- **THEN** 返回 400 校验错误，不创建

#### Scenario: 删除类型级联删数据
- **WHEN** 删除某字典类型
- **THEN** 该类型及其全部字典数据被删除

### Requirement: 字典数据管理
系统 SHALL 提供字典数据管理接口：按 dictType 分页查询（System:Dict:List）、新增（System:Dict:Add）、修改（System:Dict:Edit）、删除（System:Dict:Delete）。同一 dictType 下 dictValue MUST 唯一。

#### Scenario: 同类型下 value 重复
- **WHEN** 新增字典数据时同类型下已存在相同 dictValue
- **THEN** 返回 400 校验错误，不创建

### Requirement: 下拉选项查询
系统 SHALL 提供按 dictType 查询启用状态选项的接口（仅要求登录，不挂管理权限码），返回按 sort_num 升序的 label/value 列表。

#### Scenario: 业务表单取字典选项
- **WHEN** 已登录用户请求 options/{dictType}
- **THEN** 返回该类型下全部启用选项（label/value），停用项不返回

### Requirement: 前端字典管理页面
前端 SHALL 在「系统管理」下提供数据字典页面：类型列表 + 新增/编辑/删除 + 「数据」弹窗管理类型内字典数据（增删改），操作按钮分别使用权限码 `System:Dict:Add/Edit/Delete` 控制；并提供 `useDict()` hook（带缓存）供业务表单下拉取值。

#### Scenario: 权限按钮控制
- **WHEN** 无 `System:Dict:Delete` 权限的账号进入数据字典页
- **THEN** 页面不渲染删除按钮

## Purpose

让枚举类下拉选项（状态、类型、分类等）运营可配，替代前端硬编码，修改选项无需发版。
