package com.vben.service.module.ai.tool;

import com.vben.service.common.BizException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具注册表（第一版 6 个：用户/角色/部门 的查询 + 新增）。
 * 集中维护工具名、确认卡片标题、权限码、给模型的中文说明与参数 schema。
 */
public final class AiTools {

  public static final String QUERY_USERS = "query_users";
  public static final String QUERY_ROLES = "query_roles";
  public static final String QUERY_DEPTS = "query_depts";
  public static final String QUERY_MENUS = "query_menus";
  public static final String CREATE_USER = "create_user";
  public static final String CREATE_ROLE = "create_role";
  public static final String CREATE_DEPT = "create_dept";
  public static final String ASSIGN_ROLE_MENUS = "assign_role_menus";

  private static final List<AiToolDef> ALL = List.of(
      new AiToolDef(QUERY_USERS, "查询用户", AiToolKind.QUERY, "System:User:List",
          "查询系统用户列表。可按用户名或昵称模糊搜索；不传 keyword 时返回最近创建的 20 个用户。"
              + "结果包含 id、用户名、昵称、部门、角色、状态。用户问\"有哪些用户/有没有叫X的人\"时使用。",
          keywordParams()),

      new AiToolDef(QUERY_ROLES, "查询角色", AiToolKind.QUERY, "System:Role:List",
          "查询系统角色列表。可按角色名称或角色标识模糊搜索；不传时返回全部角色。"
              + "结果包含 id、角色标识、角色名称、数据范围、状态。",
          keywordParams()),

      new AiToolDef(QUERY_DEPTS, "查询部门", AiToolKind.QUERY, "System:Dept:List",
          "查询部门列表。可按部门名称模糊搜索；不传时返回全部部门。结果包含 id、部门名称、上级部门 id、状态。"
              + "用户问\"有哪些部门/有没有X部门\"或创建部门需要确认上级时使用。",
          keywordParams()),

      new AiToolDef(QUERY_MENUS, "查询菜单", AiToolKind.QUERY, "System:Menu:List",
          "查询系统全部菜单（目录/菜单/按钮）清单。结果包含 id、菜单名、类型（M目录/C菜单/F按钮）、上级名称、权限码、状态。"
              + "用户问\"有哪些菜单\"或准备给角色分配菜单授权前，先用本工具确认菜单的准确名称。",
          objectSchema(List.of(), List.of())),

      new AiToolDef(CREATE_USER, "创建用户", AiToolKind.CREATE, "System:User:Add",
          "创建一个系统用户。必须提供用户名、初始密码（至少 6 位）、昵称。"
              + "deptName 为部门中文名（可省略=不归属部门）；roleNames 为角色中文名或角色标识数组（可省略）。"
              + "用户说\"加个用户/建账号\"时使用，不要自行猜测部门或角色，信息不全时先向用户追问。",
          objectSchema(List.of(
              prop("username", "登录用户名，全局唯一", Map.of("type", "string")),
              prop("password", "初始密码，至少 6 位", Map.of("type", "string")),
              prop("nickname", "用户昵称/显示名", Map.of("type", "string")),
              prop("deptName", "所属部门中文名，如\"仓储部\"，可省略", Map.of("type", "string")),
              prop("roleNames", "角色名称或标识列表，如 [\"仓管员\"]，可省略",
                  Map.of("type", "array", "items", Map.of("type", "string")))),
              List.of("username", "password", "nickname"))),

      new AiToolDef(CREATE_ROLE, "创建角色", AiToolKind.CREATE, "System:Role:Add",
          "创建一个角色。roleKey 为英文角色标识（全局唯一，如 finance），roleName 为中文角色名。"
              + "dataScope 为数据范围：1全部数据 2自定义部门 3本部门 4本部门及以下 5仅本人（默认 5）。",
          objectSchema(List.of(
              prop("roleKey", "角色标识，英文且全局唯一，如 finance", Map.of("type", "string")),
              prop("roleName", "角色中文名，如 财务专员", Map.of("type", "string")),
              prop("dataScope", "数据范围 1全部 2自定义部门 3本部门 4本部门及以下 5仅本人，默认5",
                  Map.of("type", "string", "enum", List.of("1", "2", "3", "4", "5")))),
              List.of("roleKey", "roleName"))),

      new AiToolDef(CREATE_DEPT, "创建部门", AiToolKind.CREATE, "System:Dept:Add",
          "创建一个部门。deptName 为新部门名称；parentName 为上级部门中文名（省略=顶级部门）；"
              + "sortNum 为同级排序数字，可省略。上级部门不确定时先用查询部门工具确认。",
          objectSchema(List.of(
              prop("deptName", "新部门名称", Map.of("type", "string")),
              prop("parentName", "上级部门中文名，如\"总公司\"，省略表示顶级部门",
                  Map.of("type", "string")),
              prop("sortNum", "同级排序，整数，可省略", Map.of("type", "integer"))),
              List.of("deptName"))),

      new AiToolDef(ASSIGN_ROLE_MENUS, "角色授权菜单", AiToolKind.CREATE, "System:Role:Auth",
          "给角色分配菜单授权。注意：授权是全量替换，会清除该角色原有的全部菜单授权，"
              + "因此必须先与用户确认要分配的完整清单，菜单名称先用查询菜单工具核对。"
              + "roleName 为角色中文名或角色标识；menuNames 为菜单/目录/按钮的中文名数组："
              + "传目录名=该目录及全部后代（含按钮）；传菜单名=该菜单+全部上级目录+其下按钮；传按钮名=该按钮+上级目录链。",
          objectSchema(List.of(
              prop("roleName", "目标角色中文名或角色标识，如 库存管理员", Map.of("type", "string")),
              prop("menuNames", "要分配的菜单/目录/按钮中文名列表，如 [\"库存管理\"]",
                  Map.of("type", "array", "items", Map.of("type", "string")))),
              List.of("roleName", "menuNames"))));

  private AiTools() {
  }

  /** DeepSeek tools 数组 */
  public static List<Map<String, Object>> schemas() {
    return ALL.stream().map(AiToolDef::toSchema).toList();
  }

  public static List<AiToolDef> all() {
    return ALL;
  }

  public static AiToolDef require(String name) {
    return ALL.stream().filter(t -> t.name().equals(name)).findFirst()
        .orElseThrow(() -> BizException.badRequest("未知的 AI 工具：" + name));
  }

  // ------------------------------------------------------------------
  // JSON Schema 构造辅助
  // ------------------------------------------------------------------

  private static Map<String, Object> keywordParams() {
    return objectSchema(
        List.of(prop("keyword", "名称关键字，可省略", Map.of("type", "string"))),
        List.of());
  }

  private static Map<String, Object> prop(String name, String description, Map<String, Object> typeDef) {
    Map<String, Object> p = new LinkedHashMap<>(typeDef);
    p.put("description", description);
    // 单条目 map，objectSchema 中通过 putAll 合并进有序 properties
    return Map.of(name, p);
  }

  private static Map<String, Object> objectSchema(List<Map<String, Object>> props, List<String> required) {
    Map<String, Object> properties = new LinkedHashMap<>();
    for (Map<String, Object> p : props) {
      properties.putAll(p);
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }
}
