## Purpose

提供部门（组织机构）管理能力：维护树形部门结构，并让用户归属到部门，为后续按部门的数据权限和部门级授权打基础。

## ADDED Requirements

### Requirement: 部门树查询
系统 SHALL 提供部门树查询接口，返回完整的部门树（嵌套 children 结构，含 id、parentId、部门名称、负责人、状态、排序），并按排序号升序排列。接口 SHALL 要求 `System:Dept:List` 权限码。

#### Scenario: super 用户查询部门树
- **WHEN** 具备 `System:Dept:List` 权限的用户请求部门树
- **THEN** 返回嵌套树结构的全部部门，父部门包含其所有子孙节点

#### Scenario: 无权限用户查询
- **WHEN** 不具备 `System:Dept:List` 权限的用户请求部门树
- **THEN** 返回 403 拒绝

### Requirement: 新增部门
系统 SHALL 提供新增部门接口（需 `System:Dept:Add` 权限码）。新增时 MUST 校验：部门名称在同父级下唯一；父部门存在且状态正常（根部门 parentId 为 0）。

#### Scenario: 合法新增
- **WHEN** 提交名称、parentId、排序均合法的新部门
- **THEN** 部门创建成功，出现在部门树的对应父节点下

#### Scenario: 同级重名
- **WHEN** 提交的部门名称与其兄弟部门重名
- **THEN** 返回 400 校验错误，不创建

### Requirement: 修改部门
系统 SHALL 提供修改部门接口（需 `System:Dept:Edit` 权限码）。系统 MUST 校验：不允许把部门移动到自身或自己的子孙部门下（防环）。

#### Scenario: 移动到自身子孙节点
- **WHEN** 把部门 A 的 parentId 改为 A 的子部门 B
- **THEN** 返回 400 校验错误，不修改

### Requirement: 删除部门
系统 SHALL 提供删除部门接口（需 `System:Dept:Delete` 权限码）。删除前 MUST 校验：该部门无子部门，且没有用户关联到该部门。

#### Scenario: 删除有子部门的部门
- **WHEN** 删除存在子部门的部门
- **THEN** 返回 400「存在子部门，无法删除」

#### Scenario: 删除被用户占用的部门
- **WHEN** 删除存在关联用户的部门
- **THEN** 返回 400「部门下存在用户，无法删除」

### Requirement: 用户关联部门
用户 MUST 可归属一个部门（可选）。用户管理接口 SHALL 支持设置/变更 deptId，部门树查询接口返回的用户计数用于删除校验。用户列表 SHALL 展示部门名称。

#### Scenario: 给用户设置部门
- **WHEN** 编辑用户时选择部门并保存
- **THEN** 该用户关联到所选部门，列表展示部门名称

### Requirement: 前端部门管理页面
前端 SHALL 在「系统管理」下提供部门管理页面：树形表格展示部门、新增根部门/子部门、编辑、删除，操作按钮分别使用权限码 `System:Dept:Add/Edit/Delete` 控制可见性；页面 MUST 使用项目 vben 组件体系（useVbenVxeGrid / useVbenModal / useVbenForm）实现。

#### Scenario: 权限按钮控制
- **WHEN** 无 `System:Dept:Delete` 权限的账号进入部门管理页
- **THEN** 页面不渲染删除按钮，但可见列表（List 权限）
