package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
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
 * 系统管理类 AI 工具（用户/角色/部门/菜单）：从旧硬编码执行器迁移而来，
 * 全部通过 {@link AiAgentTool} 自动注册。名称→id 解析坚持「不猜测」原则：
 * 0 个或多个匹配都抛业务错误回喂模型追问。
 */
@Component
@RequiredArgsConstructor
public class AiSystemTools {

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
      Map.entry("page.system.llm", "模型配置"),
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

  // ------------------------------------------------------------------
  // 查询工具（结果字段裁剪，绝不返回密码）
  // ------------------------------------------------------------------

  @AiAgentTool(name = "query_users", title = "查询用户", kind = AiToolKind.QUERY,
      permission = "System:User:List",
      description = "查询系统用户列表。可按用户名或昵称模糊搜索；不传 keyword 时返回最近创建的 20 个用户。"
          + "结果包含 id、用户名、昵称、部门、角色、状态。用户问\"有哪些用户/有没有叫X的人\"时使用。")
  public AiToolResult queryUsers(
      @AiToolParam("用户名或昵称关键字，可省略") String keyword) {
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
    return AiToolResult.success(json(Map.of("total", page.getTotal(), "users", items)));
  }

  @AiAgentTool(name = "query_roles", title = "查询角色", kind = AiToolKind.QUERY,
      permission = "System:Role:List",
      description = "查询系统角色列表。可按角色名称或角色标识模糊搜索；不传时返回全部角色。"
          + "结果包含 id、角色标识、角色名称、数据范围、状态。")
  public AiToolResult queryRoles(
      @AiToolParam("角色名称或标识关键字，可省略") String keyword) {
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

  @AiAgentTool(name = "query_depts", title = "查询部门", kind = AiToolKind.QUERY,
      permission = "System:Dept:List",
      description = "查询部门列表。可按部门名称模糊搜索；不传时返回全部部门。结果包含 id、部门名称、上级部门名、状态。"
          + "用户问\"有哪些部门/有没有X部门\"或创建部门需要确认上级时使用。")
  public AiToolResult queryDepts(
      @AiToolParam("部门名称关键字，可省略") String keyword) {
    LambdaQueryWrapper<SysDept> q = new LambdaQueryWrapper<SysDept>()
        .orderByAsc(SysDept::getParentId)
        .orderByAsc(SysDept::getOrderNum)
        .last("limit 50");
    if (keyword != null && !keyword.isBlank()) {
      q.like(SysDept::getDeptName, keyword);
    }
    List<SysDept> depts = deptMapper.selectList(q);
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

  @AiAgentTool(name = "query_menus", title = "查询菜单", kind = AiToolKind.QUERY,
      permission = "System:Menu:List",
      description = "查询系统全部菜单（目录/菜单/按钮）清单。结果包含 id、菜单标识 menuName、中文标题 title、类型（M目录/C菜单/F按钮）、上级名称、权限码、状态。"
          + "用户问\"有哪些菜单\"或准备给角色分配菜单授权前，先用本工具确认菜单的准确名称。")
  public AiToolResult queryMenus() {
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
  // 写入工具（复用 Admin Service，含其全部校验）
  // ------------------------------------------------------------------

  @AiAgentTool(name = "create_user", title = "创建用户", kind = AiToolKind.WRITE,
      permission = "System:User:Add",
      description = "创建一个系统用户。必须提供用户名、初始密码（至少 6 位）、昵称。"
          + "deptName 为部门中文名（可省略=不归属部门）；roleNames 为角色中文名或角色标识数组（可省略）。"
          + "用户说\"加个用户/建账号\"时使用，不要自行猜测部门或角色，信息不全时先向用户追问。")
  public AiToolResult createUser(
      @AiToolParam(value = "登录用户名，全局唯一", required = true) String username,
      @AiToolParam(value = "初始密码，至少 6 位", required = true) String password,
      @AiToolParam(value = "用户昵称/显示名", required = true) String nickname,
      @AiToolParam("所属部门中文名，如\"仓储部\"，可省略") String deptName,
      @AiToolParam("角色名称或标识列表，如 [\"仓管员\"]，可省略") List<String> roleNames) {
    if (password.length() < 6) {
      throw BizException.badRequest("error.ai.password.tooShort");
    }

    Long deptId = null;
    if (deptName != null && !deptName.isBlank()) {
      deptId = resolveDeptId(deptName);
    }

    List<Long> roleIds = new ArrayList<>();
    if (roleNames != null) {
      for (String rn : roleNames) {
        roleIds.add(resolveRoleId(rn));
      }
    }

    SysUser user = new SysUser();
    user.setUsername(username);
    user.setPassword(password);
    user.setNickname(nickname);
    user.setDeptId(deptId);
    user.setRoleIds(roleIds);
    user.setStatus(0);
    userService.saveUser(user);

    String summary = "已创建用户「" + nickname + "（" + username + "）」（id=" + user.getId()
        + "，初始密码：" + password + "）"
        + (deptName != null ? "，部门：" + deptName : "")
        + (roleIds != null && !roleIds.isEmpty() ? "，角色：" + String.join("、", roleNames) : "");
    return AiToolResult.created(json(Map.of(
        "id", user.getId(), "username", username, "nickname", nickname)), summary);
  }

  @AiAgentTool(name = "create_role", title = "创建角色", kind = AiToolKind.WRITE,
      permission = "System:Role:Add",
      description = "创建一个角色。roleKey 为英文角色标识（全局唯一，如 finance），roleName 为中文角色名。"
          + "dataScope 为数据范围：1全部数据 2自定义部门 3本部门 4本部门及以下 5仅本人（默认 5）。")
  public AiToolResult createRole(
      @AiToolParam(value = "角色标识，英文且全局唯一，如 finance", required = true) String roleKey,
      @AiToolParam(value = "角色中文名，如 财务专员", required = true) String roleName,
      @AiToolParam("数据范围 1全部 2自定义部门 3本部门 4本部门及以下 5仅本人，默认5") String dataScope) {
    if (dataScope == null || dataScope.isBlank()) {
      dataScope = "5";
    }
    if (!DATA_SCOPE_LABELS.containsKey(dataScope)) {
      throw BizException.badRequest("error.ai.dataScope.invalid");
    }
    long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
        .eq(SysRole::getRoleKey, roleKey));
    if (exists > 0) {
      throw BizException.badRequest("error.ai.roleKey.exists", roleKey);
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

  @AiAgentTool(name = "create_dept", title = "创建部门", kind = AiToolKind.WRITE,
      permission = "System:Dept:Add",
      description = "创建一个部门。deptName 为新部门名称；parentName 为上级部门中文名（省略=顶级部门）；"
          + "sortNum 为同级排序数字，可省略。上级部门不确定时先用查询部门工具确认。")
  public AiToolResult createDept(
      @AiToolParam(value = "新部门名称", required = true) String deptName,
      @AiToolParam("上级部门中文名，如\"总公司\"，省略表示顶级部门") String parentName,
      @AiToolParam("同级排序整数，可省略") Integer sortNum) {
    Long parentId = 0L;
    if (parentName != null && !parentName.isBlank()) {
      parentId = resolveDeptId(parentName);
    }

    SysDept dept = new SysDept();
    dept.setDeptName(deptName);
    dept.setParentId(parentId);
    dept.setStatus(0);
    if (sortNum != null) {
      dept.setOrderNum(sortNum);
    }
    deptService.saveDept(dept);

    String summary = "已在" + (parentId == 0 ? "顶级" : "「" + parentName + "」")
        + "下创建部门「" + deptName + "」（id=" + dept.getId() + "）";
    return AiToolResult.created(
        json(Map.of("id", dept.getId(), "deptName", deptName, "parentId", parentId)), summary);
  }

  @AiAgentTool(name = "assign_role_menus", title = "角色授权菜单", kind = AiToolKind.WRITE,
      permission = "System:Role:Auth", danger = true,
      description = "给角色分配菜单授权。注意：授权是全量替换，会清除该角色原有的全部菜单授权，"
          + "因此必须先与用户确认要分配的完整清单，菜单名称先用查询菜单工具核对。"
          + "roleName 为角色中文名或角色标识；menuNames 为菜单/目录/按钮的中文名数组："
          + "传目录名=该目录及全部后代（含按钮）；传菜单名=该菜单+全部上级目录+其下按钮；传按钮名=该按钮+上级目录链。")
  public AiToolResult assignRoleMenus(
      @AiToolParam(value = "目标角色中文名或角色标识，如 库存管理员", required = true) String roleName,
      @AiToolParam(value = "要分配的菜单/目录/按钮中文名列表（也可用 menuName 标识，如 SystemUser），如 [\"库存管理\"]", required = true)
      List<String> menuNames) {
    Long roleId = resolveRoleId(roleName);
    if (menuNames == null || menuNames.isEmpty()) {
      throw BizException.badRequest("error.ai.menuNames.required");
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
  // 高危写入工具（计划执行到这些步骤会二次确认）
  // ------------------------------------------------------------------

  @AiAgentTool(name = "delete_user", title = "删除用户", kind = AiToolKind.WRITE,
      permission = "System:User:Delete", danger = true,
      description = "按登录用户名删除一个系统用户（高危，不可恢复）。会同步清理其角色关联；"
          + "禁止删除自己、禁止删除最后一个超级管理员，这些情况服务端会拒绝。"
          + "执行前必须与用户确认准确用户名。")
  public AiToolResult deleteUser(
      @AiToolParam(value = "要删除的登录用户名（精确）", required = true) String username) {
    Long userId = resolveUserId(username);
    userService.remove(userId);
    String summary = "已删除用户「" + username + "」（id=" + userId + "）";
    return AiToolResult.created(json(Map.of("id", userId, "username", username)), summary);
  }

  @AiAgentTool(name = "delete_role", title = "删除角色", kind = AiToolKind.WRITE,
      permission = "System:Role:Delete", danger = true,
      description = "按角色中文名或角色标识删除角色（高危，不可恢复）。超级管理员角色、"
          + "仍有用户使用的角色会被服务端拒绝。执行前必须与用户确认。")
  public AiToolResult deleteRole(
      @AiToolParam(value = "要删除的角色中文名或角色标识", required = true) String roleName) {
    Long roleId = resolveRoleId(roleName);
    SysRole role = roleMapper.selectById(roleId);
    roleService.remove(roleId);
    String summary = "已删除角色「" + role.getRoleName() + "」（id=" + roleId + "）";
    return AiToolResult.created(json(Map.of("id", roleId, "roleName", role.getRoleName())),
        summary);
  }

  @AiAgentTool(name = "delete_dept", title = "删除部门", kind = AiToolKind.WRITE,
      permission = "System:Dept:Delete", danger = true,
      description = "按部门中文名删除部门（高危，不可恢复）。存在子部门或仍有用户归属时服务端会拒绝。"
          + "执行前必须与用户确认准确部门名。")
  public AiToolResult deleteDept(
      @AiToolParam(value = "要删除的部门中文名（精确）", required = true) String deptName) {
    Long deptId = resolveDeptId(deptName);
    deptService.remove(deptId);
    String summary = "已删除部门「" + deptName + "」（id=" + deptId + "）";
    return AiToolResult.created(json(Map.of("id", deptId, "deptName", deptName)), summary);
  }

  @AiAgentTool(name = "reset_user_password", title = "重置用户密码", kind = AiToolKind.WRITE,
      permission = "System:User:ResetPwd", danger = true,
      description = "按登录用户名重置某用户的密码（高危）。新密码至少 6 位；重置后该用户已登录的"
          + "会话立即失效并收到通知。执行前必须与用户确认用户名和新密码。")
  public AiToolResult resetUserPassword(
      @AiToolParam(value = "要重置密码的登录用户名（精确）", required = true) String username,
      @AiToolParam(value = "新密码，至少 6 位", required = true) String newPassword) {
    if (newPassword.length() < 6) {
      throw BizException.badRequest("error.ai.password.tooShort");
    }
    Long userId = resolveUserId(username);
    userService.resetPassword(userId, newPassword);
    String summary = "已将用户「" + username + "」的密码重置为指定新密码，其旧会话已失效";
    return AiToolResult.created(json(Map.of("id", userId, "username", username)), summary);
  }

  // ------------------------------------------------------------------
  // 名称 → id 解析（不猜测；0 个或多个都报错让模型追问）
  // ------------------------------------------------------------------

  /** 登录用户名（唯一）→ id；不存在即报错 */
  private Long resolveUserId(String username) {
    SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, username.trim()));
    if (user == null) {
      throw BizException.badRequest("error.ai.user.notFound", username);
    }
    return user.getId();
  }

  private Long resolveDeptId(String deptName) {
    List<SysDept> matches = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
        .eq(SysDept::getDeptName, deptName.trim()));
    if (matches.isEmpty()) {
      throw BizException.badRequest("error.ai.dept.notFound", deptName);
    }
    if (matches.size() > 1) {
      throw BizException.badRequest("error.ai.dept.ambiguous", deptName);
    }
    return matches.get(0).getId();
  }

  private Long resolveRoleId(String roleNameOrKey) {
    String key = roleNameOrKey.trim();
    List<SysRole> matches = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
        .eq(SysRole::getRoleKey, key).or().eq(SysRole::getRoleName, key));
    if (matches.isEmpty()) {
      throw BizException.badRequest("error.ai.role.notFound", roleNameOrKey);
    }
    if (matches.size() > 1) {
      throw BizException.badRequest("error.ai.role.ambiguous", roleNameOrKey);
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
        throw BizException.badRequest("error.ai.menu.notFound", name);
      }
      if (matches.size() > 1) {
        String paths = matches.stream().map(m -> parentPath(m, byId))
            .collect(Collectors.joining("；"));
        throw BizException.badRequest("error.ai.menu.ambiguous", name, paths);
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

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
