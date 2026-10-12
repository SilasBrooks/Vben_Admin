package com.vben.service.bootstrap;

import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleMenu;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.entity.SysUserRole;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysRoleMenuMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始数据装配：内置账号/角色/菜单（与前端 backend-mock 的 MOCK_USERS、
 * MOCK_MENUS、MOCK_CODES 完全对齐，保证替换 mock 后前端行为不变）。
 *
 * <p>
 * 仅当 sys_user 表为空时执行，重复启动不会重复插入。
 * 生产环境请登录后立即修改默认密码，或按需调整本类。
 *
 * <p>
 * 内置账号（密码均为 123456）：
 * <ul>
 * <li>vben / 123456 —— super 超级角色，全部菜单 + 全部权限码</li>
 * <li>admin / 123456 —— admin 管理角色，权限码 AC_100010/20/30 + System:User:List</li>
 * <li>jack / 123456 —— user 普通角色，权限码 AC_1000001 / AC_1000002</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements ApplicationRunner {

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysMenuMapper menuMapper;
  private final SysDeptMapper deptMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMenuMapper roleMenuMapper;
  private final com.vben.service.module.ai.skill.mapper.AgentSkillMapper agentSkillMapper;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userMapper.selectCount(null) > 0) {
      return;
    }
    log.info("检测到空库，开始初始化种子数据（3 账号 / 3 角色 / 菜单与权限码）...");
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ------------------------------------------------------------------
    // 1. 角色
    // ------------------------------------------------------------------
    Long superRoleId = insertRole("super", "超级管理员", 1, "1");
    Long adminRoleId = insertRole("admin", "管理员", 2, "1");
    Long userRoleId = insertRole("user", "普通用户", 3, "5");
    // 仓管员：本部门及以下（数据范围演示角色，挂给 stockAdmin）
    Long stockRoleId = insertRole("stock", "仓管员", 4, "4");

    // ------------------------------------------------------------------
    // 2. 菜单（结构对齐前端 mock：Dashboard + Demos）
    // ------------------------------------------------------------------
    // Dashboard 目录
    Long dashboardId = insertMenu(catalog("Dashboard", "page.dashboard.title", null, -1,
        "/dashboard", "/analytics", 0L, false, false));
    insertMenu(leaf("Analytics", "page.dashboard.analytics", null, 1, "/analytics",
        "/dashboard/analytics/index", dashboardId, false, true));
    insertMenu(leaf("Workspace", "page.dashboard.workspace", null, 2, "/workspace",
        "/dashboard/workspace/index", dashboardId, false, false));

    // 系统管理模块菜单（供前端系统管理页展示，component 对应 views/system/*）
    Long systemCatalogId = insertMenu(catalog("System", "page.system.title",
        "ic:baseline-settings", 9000, "/system", "/system/user", 0L, false, false));
    Long systemUserId = insertMenu(leaf("SystemUser", "page.system.user",
        "ant-design:user-outlined", 1, "/system/user", "/system/user/index", systemCatalogId,
        false, false).authority("super,admin"));
    Long systemRoleId = insertMenu(leaf("SystemRole", "page.system.role",
        "ant-design:user-switch-outlined", 2, "/system/role", "/system/role/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long systemMenuId = insertMenu(leaf("SystemMenu", "page.system.menu",
        "ant-design:menu-outlined", 3, "/system/menu", "/system/menu/index", systemCatalogId,
        false, false).authority("super,admin"));
    Long systemDeptId = insertMenu(leaf("SystemDept", "page.system.dept",
        "ant-design:apartment-outlined", 4, "/system/dept", "/system/dept/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long systemDictId = insertMenu(leaf("SystemDict", "page.system.dict",
        "ant-design:book-outlined", 5, "/system/dict", "/system/dict/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long systemFileId = insertMenu(leaf("SystemFile", "page.system.file",
        "ant-design:file-outlined", 6, "/system/file", "/system/file/index",
        systemCatalogId, false, false).authority("super,admin"));
    // 后端接口权限码示例（F 型挂在对应菜单下，不进路由树）
    Long systemUserListId = insertMenu(perm("System:User:List", systemUserId));
    Long userAdd = insertMenu(perm("System:User:Add", systemUserId));
    Long userEdit = insertMenu(perm("System:User:Edit", systemUserId));
    Long userDelete = insertMenu(perm("System:User:Delete", systemUserId));
    Long userResetPwd = insertMenu(perm("System:User:ResetPwd", systemUserId));
    // 角色管理按钮权限码（F 型，挂在角色管理菜单下，不进路由树）
    Long roleList = insertMenu(perm("System:Role:List", systemRoleId));
    Long roleAdd = insertMenu(perm("System:Role:Add", systemRoleId));
    Long roleEdit = insertMenu(perm("System:Role:Edit", systemRoleId));
    Long roleDelete = insertMenu(perm("System:Role:Delete", systemRoleId));
    Long roleAuth = insertMenu(perm("System:Role:Auth", systemRoleId));
    // 菜单管理按钮权限码（F 型，挂在菜单管理菜单下，不进路由树）
    Long menuList = insertMenu(perm("System:Menu:List", systemMenuId));
    Long menuAdd = insertMenu(perm("System:Menu:Add", systemMenuId));
    Long menuEdit = insertMenu(perm("System:Menu:Edit", systemMenuId));
    Long menuDelete = insertMenu(perm("System:Menu:Delete", systemMenuId));
    // 部门管理按钮权限码（F 型，挂在部门管理菜单下，不进路由树）
    Long deptList = insertMenu(perm("System:Dept:List", systemDeptId));
    Long deptAdd = insertMenu(perm("System:Dept:Add", systemDeptId));
    Long deptEdit = insertMenu(perm("System:Dept:Edit", systemDeptId));
    Long deptDelete = insertMenu(perm("System:Dept:Delete", systemDeptId));
    // 数据字典按钮权限码（F 型，挂在数据字典菜单下，不进路由树）
    Long dictList = insertMenu(perm("System:Dict:List", systemDictId));
    Long dictAdd = insertMenu(perm("System:Dict:Add", systemDictId));
    Long dictEdit = insertMenu(perm("System:Dict:Edit", systemDictId));
    Long dictDelete = insertMenu(perm("System:Dict:Delete", systemDictId));
    // 文件管理按钮权限码（F 型，挂在文件管理菜单下，不进路由树）
    Long fileList = insertMenu(perm("System:File:List", systemFileId));
    Long fileUpload = insertMenu(perm("System:File:Upload", systemFileId));
    Long fileDelete = insertMenu(perm("System:File:Delete", systemFileId));
    // 公告发布（系统管理 leaf，order 7）：super/admin 可见，复用站内通知通道
    Long systemAnnounceId = insertMenu(leaf("SystemAnnounce", "page.system.announce",
        "ant-design:notification-outlined", 7, "/system/announce", "/system/announce/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long announcePublish = insertMenu(perm("Notice:Announce:Publish", systemAnnounceId));

    // 模型配置（系统管理 leaf，order 8）：AI 助手底层 LLM 增删改查/激活/连通测试
    Long systemLlmId = insertMenu(leaf("SystemLlm", "page.system.llm",
        "ant-design:robot-outlined", 8, "/system/llm", "/system/llm/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long llmList = insertMenu(perm("System:Llm:List", systemLlmId));
    Long llmAdd = insertMenu(perm("System:Llm:Add", systemLlmId));
    Long llmEdit = insertMenu(perm("System:Llm:Edit", systemLlmId));
    Long llmDelete = insertMenu(perm("System:Llm:Delete", systemLlmId));
    Long llmActivate = insertMenu(perm("System:Llm:Activate", systemLlmId));

    // 嵌入配置（系统管理 leaf，order 9）：知识库向量化所用 Embedding 模型
    Long systemEmbId = insertMenu(leaf("SystemEmb", "page.system.embedding",
        "ant-design:api-outlined", 9, "/system/embedding", "/system/embedding/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long embList = insertMenu(perm("System:Emb:List", systemEmbId));
    Long embAdd = insertMenu(perm("System:Emb:Add", systemEmbId));
    Long embEdit = insertMenu(perm("System:Emb:Edit", systemEmbId));
    Long embDelete = insertMenu(perm("System:Emb:Delete", systemEmbId));
    Long embActivate = insertMenu(perm("System:Emb:Activate", systemEmbId));

    // 知识库（系统管理 leaf，order 10）：AI 助手 RAG 检索的文档底座
    Long systemKbId = insertMenu(leaf("SystemKb", "page.system.kb",
        "ant-design:database-outlined", 10, "/system/kb", "/system/kb/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long kbList = insertMenu(perm("Kb:Doc:List", systemKbId));
    Long kbUpload = insertMenu(perm("Kb:Doc:Upload", systemKbId));
    Long kbDelete = insertMenu(perm("Kb:Doc:Delete", systemKbId));

    // AI 技能（系统管理 leaf，order 11）：Claude 式能力包，渐进式披露扩展助手能力
    Long systemSkillId = insertMenu(leaf("SystemSkill", "page.system.skill",
        "ant-design:thunderbolt-outlined", 11, "/system/skill", "/system/skill/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long skillList = insertMenu(perm("System:Skill:List", systemSkillId));
    Long skillAdd = insertMenu(perm("System:Skill:Add", systemSkillId));
    Long skillEdit = insertMenu(perm("System:Skill:Edit", systemSkillId));
    Long skillDelete = insertMenu(perm("System:Skill:Delete", systemSkillId));

    // 系统监控模块（Monitor）：super/admin 可见，user 不可见
    Long monitorCatalogId = insertMenu(catalog("Monitor", "page.monitor.title",
        "ic:baseline-monitor", 9500, "/monitor", "/monitor/oper-log", 0L, false, false));
    Long monitorOperLogId = insertMenu(leaf("MonitorOperLog", "page.monitor.operLog",
        "ant-design:file-text-outlined", 1, "/monitor/oper-log", "/monitor/oper-log/index",
        monitorCatalogId, false, false).authority("super,admin"));
    Long monitorLoginLogId = insertMenu(leaf("MonitorLoginLog", "page.monitor.loginLog",
        "ant-design:login-outlined", 2, "/monitor/login-log", "/monitor/login-log/index",
        monitorCatalogId, false, false).authority("super,admin"));
    Long monitorOnlineId = insertMenu(leaf("MonitorOnline", "page.monitor.online",
        "ant-design:team-outlined", 3, "/monitor/online", "/monitor/online/index",
        monitorCatalogId, false, false).authority("super,admin"));
    Long operLogList = insertMenu(perm("Monitor:OperLog:List", monitorOperLogId));
    Long operLogDelete = insertMenu(perm("Monitor:OperLog:Delete", monitorOperLogId));
    Long loginLogList = insertMenu(perm("Monitor:LoginLog:List", monitorLoginLogId));
    Long loginLogDelete = insertMenu(perm("Monitor:LoginLog:Delete", monitorLoginLogId));
    Long onlineList = insertMenu(perm("Monitor:Online:List", monitorOnlineId));
    Long onlineKick = insertMenu(perm("Monitor:Online:Kick", monitorOnlineId));

    // 消息聊天（IM）：登录用户可见（单聊、落库可回溯，WS /ws/im 实时推送）
    insertMenu(leaf("ImChat", "page.im.chat", "ant-design:message-outlined", 8500, "/im",
        "/im/index", 0L, true, false));

    // system 资源（含库存模块）对 super/admin 可见，user 不可见
    java.util.Set<Long> systemSet = java.util.Set.of(systemCatalogId, systemUserId,
        systemRoleId, systemMenuId, systemDeptId, systemUserListId, userAdd, userEdit,
        userDelete, userResetPwd,
        roleList, roleAdd, roleEdit, roleDelete, roleAuth,
        menuList, menuAdd, menuEdit, menuDelete,
        deptList, deptAdd, deptEdit, deptDelete,
        dictList, dictAdd, dictEdit, dictDelete,
        systemFileId, fileList, fileUpload, fileDelete,
        systemAnnounceId, announcePublish,
        systemLlmId, llmList, llmAdd, llmEdit, llmDelete, llmActivate,
        systemEmbId, embList, embAdd, embEdit, embDelete, embActivate,
        systemKbId, kbList, kbUpload, kbDelete,
        systemSkillId, skillList, skillAdd, skillEdit, skillDelete,
        monitorCatalogId, monitorOperLogId, monitorLoginLogId, monitorOnlineId,
        operLogList, operLogDelete, loginLogList, loginLogDelete, onlineList, onlineKick);

    // ------------------------------------------------------------------
    // 3. 部门种子树：总公司 → 研发部/运营部/仓储部
    // ------------------------------------------------------------------
    Long hqId = insertDept(0L, "总公司", null, 1);
    insertDept(hqId, "研发部", null, 1);
    insertDept(hqId, "运营部", null, 2);
    Long warehouseId = insertDept(hqId, "仓储部", null, 3);

    // ------------------------------------------------------------------
    // 4. 用户（密码 123456）
    // ------------------------------------------------------------------
    Long vbenId = insertUser("vben", encoder.encode("123456"), "Vben", null, null);
    Long adminId = insertUser("admin", encoder.encode("123456"), "Admin", "/workspace", null);
    Long jackId = insertUser("jack", encoder.encode("123456"), "Jack", "/analytics", null);
    // 受限账号 stockAdmin：归属仓储部，stock 角色（数据范围=本部门及以下，系统管理不可见）
    Long stockAdminId = insertUser("stockAdmin", encoder.encode("123456"), "StockAdmin",
        null, warehouseId);
    insertUserRole(vbenId, superRoleId);
    insertUserRole(adminId, adminRoleId);
    insertUserRole(jackId, userRoleId);
    insertUserRole(stockAdminId, stockRoleId);

    // ------------------------------------------------------------------
    // 5. 角色-菜单授权
    // super：全部菜单与权限
    // admin：全部（含 system，因 authority 含 admin）
    // user / stock：仅公开资源（Dashboard，不含 systemSet）
    // ------------------------------------------------------------------
    for (SysMenu m : menuMapper.selectList(null)) {
      Long id = m.getId();
      // super：全部
      insertRoleMenu(superRoleId, id);
      // admin：全部
      insertRoleMenu(adminRoleId, id);
      // user / stock：仅非 systemSet 内资源
      if (!systemSet.contains(id)) {
        insertRoleMenu(userRoleId, id);
        insertRoleMenu(stockRoleId, id);
      }
    }
    // stock 额外授权：系统管理目录 + 用户管理菜单 + 查看权限码
    // （数据范围演示角色：能进用户列表页，但只能看到本部门及以下的数据）
    insertRoleMenu(stockRoleId, systemCatalogId);
    insertRoleMenu(stockRoleId, systemUserId);
    insertRoleMenu(stockRoleId, systemUserListId);

    // ------------------------------------------------------------------
    // 6. 数据字典种子：仅建表不预置业务字典，字典数据请登录后在数据字典页维护
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 7. 内置 Agent 技能（示范概念，开箱可演示；可编辑可停用不可删除）
    // ------------------------------------------------------------------
    insertSkill("announcement-writer", "公告写作助手",
        "需要起草站内公告、通知、通告类文案时使用：按公告规范产出结构完整、受众清晰的正文，可直接交给发布公告工具发布",
        """
        # 公告写作指引

        任务：为管理后台起草一条站内公告正文。

        ## 要求
        - 标题 ≤30 字，概括事件与影响（如「系统将于周六 22:00 升级维护」）
        - 正文结构：事由（1-2 句）→ 影响范围与时间 → 需要用户做什么 → 联系方式/备注
        - 语言简洁中性，不用感叹号，不堆形容词；时间写绝对时间（含时区语义的年月日时分）
        - 先向用户确认：公告主题、影响范围、生效时间；信息不全先追问，不要编造

        ## 产出后
        将草稿交给用户确认；用户确认后可提示「可以通过我直接发布公告」，确认发送范围后
        调用 publish_announcement 工具发布（该工具需要确认卡片二次确认）。
        """);
    insertSkill("connection-troubleshooter", "故障排查手册",
        "用户反馈系统连不上、接口报错、服务不可用（如数据库/Redis 连接拒绝）时使用：按标准链路逐层定位并给出处理建议",
        """
        # 连接类故障排查指引

        任务：帮助用户定位「服务不可用 / 连接拒绝」类问题。

        ## 排查链路（按序执行，每步给出结论再进下一步）
        1. 确认现象：哪个环境、什么接口/操作报错、报错文案是什么（贴原文）
        2. 依赖服务状态：数据库（PG 容器 vben5，127.0.0.1:5444）与 Redis（6379）是否存活；
           连接拒绝通常是容器没启动——提示用户先查 Docker Desktop 是否运行，再启动容器
        3. 端口与进程：确认端口被监听、无端口冲突；防火墙/代理可能拦截本机回环以外的地址
        4. 应用侧：后端日志若有 Unresolved compilation problems，执行 mvn -q clean compile 后重启
        5. 给出结论：根因 + 已执行的处置 + 剩余建议；查不到的事实不要编造

        ## 边界
        你只能提供排查思路与系统内数据（如通过工具查询到的状态），不能代替用户执行命令。
        """);
    insertSkill("meeting-minutes-formatter", "会议纪要整理",
        "用户粘贴会议记录/聊天摘录希望整理成结构化纪要时使用：产出决议清晰、待办可跟踪的标准纪要",
        """
        # 会议纪要整理指引

        任务：把用户提供的原始记录整理为结构化会议纪要。

        ## 输出结构
        - 结论先行：一段话概括本次会议最重要的决议
        - 决议列表：每条一行，标注达成方式（共识/拍板/待定）
        - 待办清单：事项 | 负责人 | 截止时间；缺失的负责人/时间向用户追问或标注「待定」，不要编造
        - 遗留问题：未达成一致的分歧点

        ## 规则
        - 忠于原文：只重组不改写事实；原文没有的信息用「待定」占位并提示用户补充
        - 人名保留原样；时间统一为「M月d日 HH:mm」格式
        """);

    log.info("种子数据初始化完成。账号：vben/123456(super)、admin/123456(admin)、"
        + "jack/123456(user)、stockAdmin/123456(stock,仓储部,数据范围=本部门及以下)");
  }

  // ------------------------------------------------------------------
  // 菜单构造 helpers（链式补充特殊字段）
  // ------------------------------------------------------------------

  private record MenuSpec(SysMenu menu) {
    MenuSpec authority(String roles) {
      menu().setAuthority(roles);
      return this;
    }

    MenuSpec extraMeta(String json) {
      menu().setExtraMeta(json);
      return this;
    }
  }

  /** 目录（M）：无 component，靠 children 渲染 */
  private MenuSpec catalog(String name, String title, String icon, int order, String path,
      String redirect, Long parentId, boolean keepAlive, boolean affixTab) {
    SysMenu m = base(name, "M", title, icon, order, parentId);
    m.setPath(path);
    m.setRedirect(redirect);
    m.setKeepAlive(keepAlive ? 1 : 0);
    m.setAffixTab(affixTab ? 1 : 0);
    return new MenuSpec(m);
  }

  /** 菜单（C）：对应前端一个页面 */
  private MenuSpec leaf(String name, String title, String icon, int order, String path,
      String component, Long parentId, boolean keepAlive, boolean affixTab) {
    SysMenu m = base(name, "C", title, icon, order, parentId);
    m.setPath(path);
    m.setComponent(component);
    m.setKeepAlive(keepAlive ? 1 : 0);
    m.setAffixTab(affixTab ? 1 : 0);
    return new MenuSpec(m);
  }

  /** 按钮权限码（F）：不进路由树 */
  private MenuSpec perm(String code, Long parentId) {
    SysMenu m = base("Perm_" + code.replace(':', '_'), "F", code, null, 99, parentId);
    m.setPerm(code);
    return new MenuSpec(m);
  }

  private SysMenu base(String name, String type, String title, String icon, int order,
      Long parentId) {
    SysMenu m = new SysMenu();
    m.setMenuName(name);
    m.setMenuType(type);
    m.setTitle(title);
    m.setIcon(icon);
    m.setOrderNum(order);
    m.setParentId(parentId);
    m.setVisible(0);
    m.setKeepAlive(0);
    m.setAffixTab(0);
    m.setStatus(0);
    return m;
  }

  private Long insertMenu(MenuSpec spec) {
    menuMapper.insert(spec.menu());
    return spec.menu().getId();
  }

  /** 内置 Agent 技能（builtIn=1，默认启用） */
  private void insertSkill(String name, String title, String description, String content) {
    com.vben.service.module.ai.skill.AgentSkill skill =
        new com.vben.service.module.ai.skill.AgentSkill();
    skill.setName(name);
    skill.setTitle(title);
    skill.setDescription(description);
    skill.setContent(content.strip());
    skill.setEnabled(1);
    skill.setBuiltIn(1);
    agentSkillMapper.insert(skill);
  }

  // ------------------------------------------------------------------
  // 其他 helpers
  // ------------------------------------------------------------------

  private Long insertRole(String key, String name, int sort, String dataScope) {
    SysRole r = new SysRole();
    r.setRoleKey(key);
    r.setRoleName(name);
    r.setSortNum(sort);
    r.setStatus(0);
    r.setDataScope(dataScope);
    roleMapper.insert(r);
    return r.getId();
  }

  private Long insertUser(String username, String passwordHash, String nickname,
      String homePath, Long deptId) {
    SysUser u = new SysUser();
    u.setUsername(username);
    u.setPassword(passwordHash);
    u.setNickname(nickname);
    u.setHomePath(homePath);
    u.setDeptId(deptId);
    u.setStatus(0);
    userMapper.insert(u);
    return u.getId();
  }

  private Long insertDept(Long parentId, String deptName, String leader, int orderNum) {
    SysDept d = new SysDept();
    d.setParentId(parentId);
    d.setDeptName(deptName);
    d.setLeader(leader);
    d.setStatus(0);
    d.setOrderNum(orderNum);
    deptMapper.insert(d);
    return d.getId();
  }

  private void insertUserRole(Long userId, Long roleId) {
    SysUserRole ur = new SysUserRole();
    ur.setUserId(userId);
    ur.setRoleId(roleId);
    userRoleMapper.insert(ur);
  }

  private void insertRoleMenu(Long roleId, Long menuId) {
    SysRoleMenu rm = new SysRoleMenu();
    rm.setRoleId(roleId);
    rm.setMenuId(menuId);
    roleMenuMapper.insert(rm);
  }
}
