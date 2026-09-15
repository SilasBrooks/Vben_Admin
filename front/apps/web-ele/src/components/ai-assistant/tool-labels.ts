/** 确认卡片参数中文标签（按工具名） */
const ARG_LABELS: Record<string, Record<string, string>> = {
  create_dept: {
    deptName: '部门名称',
    parentName: '上级部门',
    sortNum: '排序',
  },
  create_role: {
    dataScope: '数据范围',
    roleKey: '角色标识',
    roleName: '角色名称',
  },
  create_user: {
    deptName: '所属部门',
    nickname: '昵称',
    password: '初始密码',
    roleNames: '角色',
    username: '用户名',
  },
  assign_role_menus: {
    menuNames: '菜单（全量替换）',
    roleName: '目标角色',
  },
};

const DATA_SCOPE_LABELS: Record<string, string> = {
  '1': '全部数据',
  '2': '自定义部门',
  '3': '本部门',
  '4': '本部门及以下',
  '5': '仅本人',
};

/** 将工具参数对象转为 [中文标签, 展示值] 列表，空值过滤 */
export function formatCardArgs(
  toolName: string,
  args: Record<string, any>,
): Array<[string, string]> {
  const labels = ARG_LABELS[toolName] ?? {};
  return Object.entries(args)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => {
      let text = Array.isArray(value)
        ? value.join('、')
        : typeof value === 'object'
          ? JSON.stringify(value)
          : String(value);
      if (key === 'dataScope') {
        text = DATA_SCOPE_LABELS[text] ?? text;
      }
      return [labels[key] ?? key, text];
    });
}
