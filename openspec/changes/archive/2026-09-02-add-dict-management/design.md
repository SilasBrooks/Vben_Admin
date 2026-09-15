## Context

复用既有模式：纯 MyBatis-Plus + `XxxAdminService` + `@RequirePermission` + vben 列表页/弹窗表单。字典数据量小（每类型几十条），类型列表不做分页。

## Goals / Non-Goals

- Goals：类型与数据两级管理；下拉选项接口供前端统一取值；useDict hook 带模块级缓存
- Non-Goals：不做字典内置标记（禁止删除系统内置项）；不替换现有页面硬编码下拉；不做租户级字典

## Decisions

### D1：两表结构
- `sys_dict_type`：id、dict_name、dict_type（唯一）、status(0正常1停用)、remark、create/update_time
- `sys_dict_data`：id、dict_type（逻辑外键，无 FK 约束）、dict_label、dict_value、sort_num、status、remark、create_time

### D2：校验规则
- 新增/改类型：dictType 唯一（改时排除自身）
- 新增/改数据：同 dictType 下 dictValue 唯一（改时排除自身）
- 删除类型：同事务级联删除其全部数据项（业务级联，不建外键）

### D3：接口与权限
- `GET /system/dict-type/list`（System:Dict:List）、save（Add）、update（Edit）、DELETE /{id}（Delete，级联删数据）
- `GET /system/dict-data/list?dictType=`（System:Dict:List，分页）、save（Add）、update（Edit）、DELETE /{id}（Delete）
- `GET /system/dict-data/options/{dictType}`：**不挂权限码**（登录即可），只返回 status=0 的选项，sort_num 升序——业务下拉不能要求人人有管理权限

### D4：前端结构
- `dict/index.vue` 类型列表（不分页）+ 新增/编辑/删除 + 「数据」按钮打开 DictDataModal
- `dict-data-modal.vue`：弹窗内嵌 useVbenVxeGrid 分页表格 + 数据项增删改（子表单弹窗 dict-data-form.vue）
- `hooks/use-dict.ts`：`const { options } = await useDict('wsm_stock_type')`，模块级 Map 缓存避免重复请求

## Risks / Trade-offs

- options 接口仅登录即可——字典内容不含敏感信息，可接受
- 删除类型级联删数据不可恢复——前端二次确认文案中明确提示

## Migration Plan

1. schema 两表 → 实体/Mapper/Service/Controller（含 @OperLog）→ 种子菜单
2. 前端 api + 3 个组件 + hook → typecheck
3. 重置 H2 重启 → curl 验收（含 403、级联删除、options）

## Open Questions

- 无
