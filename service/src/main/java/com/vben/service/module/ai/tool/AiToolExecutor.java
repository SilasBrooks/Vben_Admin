package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysDeptAdminService;
import com.vben.service.module.system.service.SysRoleAdminService;
import com.vben.service.module.system.service.SysUserAdminService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 工具执行器：参数解析 → 登录用户权限校验 → 名称解析为 id → 复用现有 Admin Service 落库/查询。
 *
 * <p>两种调用姿态：
 * <ul>
 *   <li>{@link #runStrict}：用户确认后的写操作入口，业务错误直接抛异常走全局异常处理</li>
 *   <li>{@link #runQuiet}：编排循环内自动执行查询，业务错误转为失败结果回喂模型追问/解释</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AiToolExecutor {

  private static final int QUERY_LIMIT = 20;
  private static final Map<String, String> DATA_SCOPE_LABELS = Map.of(
      "1", "全部数据", "2", "自定义部门", "3", "本部门", "4", "本部门及以下", "5", "仅本人");

  /**
   * i18n key → 中文名。菜单标题（M/C 型）在 sys_menu.title 里存的是 vue-i18n key
   * （与前端 langs/{lang}/page.json 同构，见 AGENTS.md），而 AI 对话是纯中文场景：
   * 工具结果展示与菜单名匹配都需要把 key 还原为中文。新增带 key 的菜单后必须同步本表；
   * 未登记的 key 原样展示，不报错。
   */
  private static final Map<String, String> MENU_TITLE_ZH = Map.ofEntries(
      Map.entry("page.dashboard.title", "概览"),
      Map.entry("page.dashboard.analytics", "分析页"),
      Map.entry("page.dashboard.workspace", "工作台"),
      Map.entry("page.system.title", "系统管理"),
      Map.entry("page.system.user", "用户管理"),
      Map.entry("page.system.role", "角色管理"),
      Map.entry("page.system.menu", "菜单管理"),
      Map.entry("page.system.dept", "部门管理"),
      Map.entry("page.system.dict", "数据字典"),
      Map.entry("page.system.file", "文件管理"),
      Map.entry("page.system.announce", "公告发布"),
      Map.entry("page.wsm.title", "库存管理"),
      Map.entry("page.wsm.store", "库存"),
      Map.entry("page.monitor.title", "系统监控"),
      Map.entry("page.monitor.operLog", "操作日志"),
      Map.entry("page.monitor.loginLog", "登录日志"),
      Map.entry("page.monitor.online", "在线用户"),
      Map.entry("page.im.chat", "消息聊天"));

  private final SysUserAdminService userService;
  private final SysRoleAdminService roleService;
  private final SysDeptAdminService deptService;
  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysDeptMapper deptMapper;
  private final SysMenuMapper menuMapper;
  private final ObjectMapper objectMapper;

  /** 确认执行（写操作）：权限/参数错误抛 BizException，由全局异常处理返回前端 */
  public AiToolResult runStrict(String toolName, String argsJson) {
    return dispatch(toolName, argsJson);
  }

  /** 自动执行（查询工具）：业务错误转为失败结果回喂模型，不中断整轮对话 */
  public AiToolResult runQuiet(String toolName, String argsJson) {
    try {
      return dispatch(toolName, argsJson);
    } catch (BizException e) {
      return AiToolResult.fail(e.getMessage());
    }
  }

  private AiToolResult dispatch(String toolName, String argsJson) {
    AiToolDef def = AiTools.require(toolName);
    LoginUser loginUser = LoginUserHolder.get();
    if (loginUser == null) {
      throw BizException.unauthorized("未登录或登录已过期");
    }
    if (!loginUser.hasPermission(def.permission())) {
      throw BizException.forbidden("当前账号没有「" + def.title() + "」的权限");
    }
    JsonNode args = parseArgs(argsJson);
    return switch (toolName) {
      case AiTools.QUERY_USERS -> queryUsers(text(args, "keyword"));
      case AiTools.QUERY_ROLES -> queryRoles(text(args, "keyword"));
      case AiTools.QUERY_DEPTS -> queryDepts(text(args, "keyword"));
      case AiTools.QUERY_MENUS -> queryMenus();
      case AiTools.CREATE_USER -> createUser(args);
      case AiTools.CREATE_ROLE -> createRole(args);
      case AiTools.CREATE_DEPT -> createDept(args);
      case AiTools.ASSIGN_ROLE_MENUS -> assignRoleMenus(args);
      default -> throw BizException.badRequest("未知的 AI 工具：" + toolName);
    };
  }

  // ------------------------------------------------------------------
  // 查询工具（结果字段裁剪，绝不返回密码）
  // ------------------------------------------------------------------

  private AiToolResult queryUsers(String keyword) {
    // 走 Admin Service：@DataScope 切面生效，用户只能看到数据范围内的人
    IPage<SysUser> page = userService.page(1, QUERY_LIMIT, keyword, null);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysUser u : page.getRecords()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", u.getId());
      m.put("username", u.getUsername());
      m.put("nickname", u.getNickname());
      m.put("deptName", u.getDeptName());
      m.put("roles", roleNamesOf(u.getId()));
      m.put("status", u.getStatus() != null && u.getStatus() == 1 ? "停用" : "正常");
      items.add(m);
    }
    return AiToolResult.success(json(Map.of(
        "total", page.getTotal(),
        "users", items)));
  }

  private AiToolResult queryRoles(String keyword) {
    LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<SysRole>()
        .orderByAsc(SysRole::getSortNum)
        .last("limit " + QUERY_LIMIT);
    if (keyword != null && !keyword.isBlank()) {
      q.and(w -> w.like(SysRole::getRoleName, keyword).or().like(SysRole::getRoleKey, keyword));
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysRole r : roleMapper.selectList(q)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", r.getId());
      m.put("roleKey", r.getRoleKey());
      m.put("roleName", r.getRoleName());
      m.put("dataScope", DATA_SCOPE_LABELS.getOrDefault(r.getDataScope(), "仅本人"));
      m.put("status", r.getStatus() != null && r.getStatus() == 1 ? "停用" : "正常");
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("roles", items, "total", items.size())));
  }

  private AiToolResult queryDepts(String keyword) {
    LambdaQueryWrapper<SysDept> q = new LambdaQueryWrapper<SysDept>()
        .orderByAsc(SysDept::getParentId)
        .orderByAsc(SysDept::getOrderNum)
        .last("limit 50");
    if (keyword != null && !keyword.isBlank()) {
      q.like(SysDept::getDeptName, keyword);
    }
    List<SysDept> depts = deptMapper.selectList(q);
    // id -> 名称，用于回填上级部门名
    Map<Long, String> nameById = new LinkedHashMap<>();
    for (SysDept d : deptService.list()) {
      nameById.put(d.getId(), d.getDeptName());
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysDept d : depts) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", d.getId());
      m.put("deptName", d.getDeptName());
      m.put("parentName", d.getParentId() == null || d.getParentId() == 0
          ? "（顶级）" : nameById.getOrDefault(d.getParentId(), "未知"));
      m.put("status", d.getStatus() != null && d.getStatus() == 1 ? "停用" : "正常");
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("depts", items, "total", items.size())));
  }

  private AiToolResult queryMenus() {
    List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
        .orderByAsc(SysMenu::getParentId)
        .orderByAsc(SysMenu::getOrderNum));
    Map<Long, String> nameById = new LinkedHashMap<>();
    for (SysMenu m : menus) {
      nameById.put(m.getId(), displayTitle(m));
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysMenu m : menus) {
      Map<String, Object> n = new LinkedHashMap<>();
      n.put("id", m.getId());
      n.put("menuName", m.getMenuName());
      n.put("title", displayTitle(m));
      n.put("menuType", m.getMenuType());
      n.put("parentName", m.getParentId() == null || m.getParentId() == 0
          ? "（顶级）" : nameById.getOrDefault(m.getParentId(), "未知"));
      n.put("perm", m.getPerm());
      n.put("status", m.getStatus() != null && m.getStatus() == 1 ? "停用" : "正常");
      items.add(n);
    }
    return AiToolResult.success(json(Map.of("menus", items, "total", items.size())));
  }

  // ------------------------------------------------------------------
  // 新增工具（复用 Admin Service，含其全部校验）
  // ------------------------------------------------------------------

  private AiToolResult createUser(JsonNode args) {
    String username = requireText(args, "username");
    String password = requireText(args, "password");
    if (password.length() < 6) {
      throw BizException.badRequest("初始密码至少 6 位，请向用户补充确认");
    }
    String nickname = requireText(args, "nickname");

    Long deptId = null;
    String deptName = text(args, "deptName");
    if (deptName != null && !deptName.isBlank()) {
      deptId = resolveDeptId(deptName);
    }

    List<Long> roleIds = new ArrayList<>();
    List<String> roleNames = textList(args, "roleNames");
    for (String rn : roleNames) {
      roleIds.add(resolveRoleId(rn));
    }

    SysUser user = new SysUser();
    user.setUsername(username);
    user.setPassword(password);
    user.setNickname(nickname);
    user.setDeptId(deptId);
    user.setRoleIds(roleIds);
    user.setStatus(0);
    userService.saveUser(user);

    String summary = "已创建用户「" + nickname + "（" + username + "）」（id=" + user.getId() + "，初始密码：" + password + "）"
        + (deptName != null ? "，部门：" + deptName : "")
        + (!roleNames.isEmpty() ? "，角色：" + String.join("、", roleNames) : "");
    return AiToolResult.created(json(Map.of(
        "id", user.getId(), "username", username, "nickname", nickname)), summary);
  }

  private AiToolResult createRole(JsonNode args) {
    String roleKey = requireText(args, "roleKey");
    String roleName = requireText(args, "roleName");
    String dataScope = text(args, "dataScope");
    if (dataScope == null || dataScope.isBlank()) {
      dataScope = "5";
    }
    if (!DATA_SCOPE_LABELS.containsKey(dataScope)) {
      throw BizException.badRequest("数据范围取值非法，应为 1-5 之一");
    }
    long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
        .eq(SysRole::getRoleKey, roleKey));
    if (exists > 0) {
      throw BizException.badRequest("角色标识「" + roleKey + "」已存在，请换一个");
    }

    SysRole role = new SysRole();
    role.setRoleKey(roleKey);
    role.setRoleName(roleName);
    role.setDataScope(dataScope);
    role.setStatus(0);
    role.setSortNum(1);
    Long id = roleService.saveRole(role);

    String summary = "已创建角色「" + roleName + "」（标识 " + roleKey + "，id=" + id
        + "，数据范围：" + DATA_SCOPE_LABELS.get(dataScope) + "）";
    return AiToolResult.created(
        json(Map.of("id", id, "roleKey", roleKey, "roleName", roleName)), summary);
  }

  private AiToolResult createDept(JsonNode args) {
    String deptName = requireText(args, "deptName");
    String parentName = text(args, "parentName");
    Long parentId = 0L;
    if (parentName != null && !parentName.isBlank()) {
      parentId = resolveDeptId(parentName);
    }

    SysDept dept = new SysDept();
    dept.setDeptName(deptName);
    dept.setParentId(parentId);
    dept.setStatus(0);
    if (args.hasNonNull("sortNum")) {
      dept.setOrderNum(args.get("sortNum").asInt());
    }
    deptService.saveDept(dept);

    String summary = "已在" + (parentId == 0 ? "顶级" : "「" + parentName + "」")
        + "下创建部门「" + deptName + "」（id=" + dept.getId() + "）";
    return AiToolResult.created(
        json(Map.of("id", dept.getId(), "deptName", deptName, "parentId", parentId)), summary);
  }

  private AiToolResult assignRoleMenus(JsonNode args) {
    String roleName = requireText(args, "roleName");
    Long roleId = resolveRoleId(roleName);
    List<String> menuNames = textList(args, "menuNames");
    if (menuNames.isEmpty()) {
      throw BizException.badRequest("menuNames 不能为空，请向用户确认要分配的菜单清单");
    }

    SysRole role = roleMapper.selectById(roleId);
    Set<Long> menuIds = resolveMenuIds(menuNames);
    // 全量替换：与角色管理页"授权"走同一条代码路径
    roleService.assignMenus(roleId, new ArrayList<>(menuIds));

    String summary = "已为角色「" + role.getRoleName() + "」重新分配 " + menuIds.size()
        + " 项菜单（全量替换，原授权已清除）：" + String.join("、", menuNames);
    return AiToolResult.created(
        json(Map.of("roleId", roleId, "roleName", role.getRoleName(), "menuCount", menuIds.size())),
        summary);
  }

  // ------------------------------------------------------------------
  // 名称 → id 解析（不猜测；0 个或多个都报错让模型追问）
  // ------------------------------------------------------------------

  private Long resolveDeptId(String deptName) {
    List<SysDept> matches = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
        .eq(SysDept::getDeptName, deptName.trim()));
    if (matches.isEmpty()) {
      throw BizException.badRequest("未找到名为「" + deptName + "」的部门，请先用查询部门工具确认准确名称");
    }
    if (matches.size() > 1) {
      throw BizException.badRequest("存在多个名为「" + deptName + "」的部门，请让用户提供更明确的上级路径");
    }
    return matches.get(0).getId();
  }

  private Long resolveRoleId(String roleNameOrKey) {
    String key = roleNameOrKey.trim();
    List<SysRole> matches = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
        .eq(SysRole::getRoleKey, key).or().eq(SysRole::getRoleName, key));
    if (matches.isEmpty()) {
      throw BizException.badRequest("未找到角色「" + roleNameOrKey + "」，可先用查询角色工具确认");
    }
    if (matches.size() > 1) {
      throw BizException.badRequest("角色名称「" + roleNameOrKey + "」匹配到多个角色，请改用角色标识");
    }
    return matches.get(0).getId();
  }

  /**
   * 菜单名 → id 集合（title 或 menuName 精确匹配），并按粒度自动补全：
   * 目录→该目录及全部后代；菜单→该菜单+全部祖先目录+其直属按钮；按钮→该按钮+祖先目录链。
   * 0 个或多个匹配都报错，不猜测。
   */
  private Set<Long> resolveMenuIds(List<String> menuNames) {
    List<SysMenu> all = menuMapper.selectList(null);
    Map<Long, SysMenu> byId = new LinkedHashMap<>();
    Map<Long, List<SysMenu>> childrenByParent = new LinkedHashMap<>();
    Map<String, List<SysMenu>> byName = new LinkedHashMap<>();
    for (SysMenu m : all) {
      byId.put(m.getId(), m);
      childrenByParent.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
          k -> new ArrayList<>()).add(m);
      // 匹配名三种来源：展示中文名（i18n key 还原）、原始 title（key 或明文）、menuName；
      // 相同名字只登记一次，避免同一节点重复计入歧义
      Set<String> names = new LinkedHashSet<>();
      if (m.getTitle() != null && !m.getTitle().isBlank()) {
        names.add(m.getTitle().trim());
        String zh = MENU_TITLE_ZH.get(m.getTitle().trim());
        if (zh != null) {
          names.add(zh);
        }
      }
      if (m.getMenuName() != null && !m.getMenuName().isBlank()) {
        names.add(m.getMenuName().trim());
      }
      for (String name : names) {
        byName.computeIfAbsent(name, k -> new ArrayList<>()).add(m);
      }
    }

    Set<Long> result = new LinkedHashSet<>();
    for (String name : menuNames) {
      List<SysMenu> matches = byName.get(name);
      if (matches == null || matches.isEmpty()) {
        throw BizException.badRequest("未找到名为「" + name + "」的菜单，请先用查询菜单工具确认准确名称");
      }
      if (matches.size() > 1) {
        String paths = matches.stream().map(m -> parentPath(m, byId))
            .collect(Collectors.joining("；"));
        throw BizException.badRequest("存在多个名为「" + name + "」的菜单（" + paths + "），请提供更明确的名称");
      }
      SysMenu hit = matches.get(0);
      switch (hit.getMenuType()) {
        case "M" -> collectSubTree(hit, childrenByParent, result);
        case "C" -> {
          result.add(hit.getId());
          collectAncestors(hit, byId, result);
          for (SysMenu child : childrenByParent.getOrDefault(hit.getId(), List.of())) {
            if ("F".equals(child.getMenuType())) {
              result.add(child.getId());
            }
          }
        }
        default -> {
          // F 按钮（或未知类型）：按钮自身 + 祖先目录链
          result.add(hit.getId());
          collectAncestors(hit, byId, result);
        }
      }
    }
    return result;
  }

  /** 目录：收录自身及全部后代（子目录/菜单/按钮） */
  private void collectSubTree(SysMenu node, Map<Long, List<SysMenu>> childrenByParent,
      Set<Long> out) {
    out.add(node.getId());
    for (SysMenu child : childrenByParent.getOrDefault(node.getId(), List.of())) {
      collectSubTree(child, childrenByParent, out);
    }
  }

  /** 收录全部祖先目录链（不含自身） */
  private void collectAncestors(SysMenu node, Map<Long, SysMenu> byId, Set<Long> out) {
    Long parentId = node.getParentId();
    int guard = 0;
    while (parentId != null && parentId != 0 && guard++ < 20) {
      SysMenu parent = byId.get(parentId);
      if (parent == null) {
        break;
      }
      out.add(parent.getId());
      parentId = parent.getParentId();
    }
  }

  /** 菜单标题展示名：M/C 型 title 为 i18n key，还原为中文；未登记的 key 或明文标题原样返回 */
  private static String displayTitle(SysMenu m) {
    String t = m.getTitle();
    return t == null ? null : MENU_TITLE_ZH.getOrDefault(t.trim(), t);
  }

  /** 如 "系统管理 / 用户管理"，用于同名歧义报错提示 */
  private String parentPath(SysMenu node, Map<Long, SysMenu> byId) {
    List<String> parts = new ArrayList<>();
    parts.add(displayTitle(node));
    Long parentId = node.getParentId();
    int guard = 0;
    while (parentId != null && parentId != 0 && guard++ < 20) {
      SysMenu parent = byId.get(parentId);
      if (parent == null) {
        break;
      }
      parts.add(0, displayTitle(parent));
      parentId = parent.getParentId();
    }
    return String.join(" / ", parts);
  }

  /** 用户已分配角色的中文名（查询结果展示） */
  private List<String> roleNamesOf(Long userId) {
    List<Long> ids = userMapper.selectRoleIdsByUserId(userId);
    if (ids.isEmpty()) {
      return List.of();
    }
    return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, ids))
        .stream().map(SysRole::getRoleName).toList();
  }

  // ------------------------------------------------------------------
  // 参数解析辅助
  // ------------------------------------------------------------------

  private JsonNode parseArgs(String argsJson) {
    try {
      if (argsJson == null || argsJson.isBlank()) {
        return objectMapper.createObjectNode();
      }
      return objectMapper.readTree(argsJson);
    } catch (Exception e) {
      throw BizException.badRequest("工具参数不是合法 JSON");
    }
  }

  private String text(JsonNode args, String field) {
    JsonNode node = args.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    String v = node.asText();
    return v == null || v.isBlank() ? null : v.trim();
  }

  private String requireText(JsonNode args, String field) {
    String v = text(args, field);
    if (v == null) {
      throw BizException.badRequest("缺少必填参数「" + field + "」，请向用户补充确认");
    }
    return v;
  }

  private List<String> textList(JsonNode args, String field) {
    JsonNode node = args.get(field);
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    node.forEach(n -> {
      String v = n.asText();
      if (v != null && !v.isBlank()) {
        result.add(v.trim());
      }
    });
    return result;
  }

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
