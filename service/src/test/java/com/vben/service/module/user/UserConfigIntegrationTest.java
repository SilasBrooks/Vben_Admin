package com.vben.service.module.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.JwtTokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** 真实 HTTP、PostgreSQL、Redis 校验；仅创建独立测试用户，结束后清理测试数据。 */
@EnabledIfEnvironmentVariable(named = "USER_CONFIG_INTEGRATION_TEST", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class UserConfigIntegrationTest {
  @Autowired TestRestTemplate http;
  @Autowired SysUserMapper users;
  @Autowired JwtTokenService tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  private final List<Long> created = new ArrayList<>();
  private final List<Long> createdRoles = new ArrayList<>();
  private final List<Long> createdMenus = new ArrayList<>();

  private String account() {
    SysUser user = new SysUser();
    user.setUsername("columns_" + UUID.randomUUID().toString().substring(0, 12));
    user.setNickname("Column integration test");
    user.setPassword("unusable-test-password");
    user.setStatus(0);
    users.insert(user);
    created.add(user.getId());
    return tokens.generateAccessToken(user.getId(), user.getUsername(), List.of(), 0);
  }

  private ResponseEntity<JsonNode> request(HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept-Language", "en-US");
    if (token != null) headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private JsonNode ok(ResponseEntity<JsonNode> result) {
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().path("code").asInt()).isZero();
    return result.getBody().path("data");
  }

  @AfterEach
  void cleanup() {
    for (Long id : created) {
      jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
      jdbc.update("DELETE FROM sys_user_config WHERE user_id = ?", id);
      users.deleteById(id);
      redis.delete(List.of(RedisKeys.online(id), RedisKeys.tokenVer(id)));
    }
    for (Long id : createdRoles) {
      jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", id);
      jdbc.update("DELETE FROM sys_role WHERE id = ?", id);
    }
    for (Long id : createdMenus.reversed()) {
      jdbc.update("DELETE FROM sys_menu WHERE id = ?", id);
    }
  }

  @Test
  void menuReflectsRolePermissionsAndRejectsPersonalOverrides() {
    String a = account();
    String b = account();
    Long userId = created.get(0);
    String path = "/user-config?key=menu";
    assertThat(request(HttpMethod.GET, path, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    JsonNode empty = ok(request(HttpMethod.GET, path, a, null));
    assertThat(empty.isArray()).isTrue();
    assertThat(empty.isEmpty()).isTrue();

    Long roleId = jdbc.queryForObject(
        "INSERT INTO sys_role(role_key, role_name) VALUES (?, 'Menu integration test') RETURNING id",
        Long.class, "menu_test_" + UUID.randomUUID());
    createdRoles.add(roleId);
    jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
    Long rootId = createMenu(0L, "M", 0);
    Long pageId = createMenu(rootId, "C", 0);
    Long disabledId = createMenu(rootId, "C", 1);
    Long buttonId = createMenu(rootId, "F", 0);
    for (Long menuId : List.of(rootId, pageId, disabledId, buttonId)) {
      jdbc.update("INSERT INTO sys_role_menu(role_id, menu_id) VALUES (?, ?)", roleId, menuId);
    }
    jdbc.update("INSERT INTO sys_user_config(user_id, config_key, config_value) VALUES (?, 'menu', ?)",
        userId, "[{\"name\":\"Forged\"}]");

    JsonNode tree = ok(request(HttpMethod.GET, path, a, null));
    assertThat(tree.size()).isEqualTo(1);
    assertThat(tree.get(0).path("name").asText()).startsWith("menu_test_");
    assertThat(tree.get(0).path("children").size()).isEqualTo(1);
    assertThat(tree.get(0).path("children").get(0).path("component").asText()).isEqualTo("/system/user/index");
    assertThat(ok(request(HttpMethod.GET, path + "&userId=" + userId, b, null)).isEmpty()).isTrue();
    var readOnly = request(HttpMethod.POST, "/user-config/save", a, Map.of("key", "menu", "value", List.of()));
    assertThat(readOnly.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(readOnly.getBody().path("message").asText()).contains("read-only");
    assertThat(jdbc.queryForObject("SELECT config_value FROM sys_user_config WHERE user_id = ? AND config_key = 'menu'",
        String.class, userId)).isEqualTo("[{\"name\":\"Forged\"}]");

    jdbc.update("UPDATE sys_role SET status = 1 WHERE id = ?", roleId);
    assertThat(ok(request(HttpMethod.GET, path, a, null)).isEmpty()).isTrue();
    assertThat(request(HttpMethod.GET, "/menu/all", a, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private Long createMenu(long parentId, String type, int status) {
    Long id = jdbc.queryForObject("""
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, title, path, component, status)
        VALUES (?, ?, ?, 'page.test.menu', '/menu-test', '/system/user/index', ?) RETURNING id
        """, Long.class, parentId, "menu_test_" + UUID.randomUUID(), type, status);
    createdMenus.add(id);
    return id;
  }

  @Test
  void savesImmediatelyAndIsolatesUsersAndTables() {
    String a = account();
    String b = account();
    String path = "/user-config?key=table.test.user";
    assertThat(request(HttpMethod.GET, path, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ok(request(HttpMethod.GET, path, a, null)).isEmpty()).isTrue();
    var value = List.of(Map.of("field", "username", "visible", false, "width", 200, "fixed", "left"));
    ok(request(HttpMethod.POST, "/user-config/save", a, Map.of("key", "table.test.user", "value", value)));
    JsonNode saved = ok(request(HttpMethod.GET, path, a, null));
    assertThat(saved.get(0).path("visible").asBoolean()).isFalse();
    assertThat(saved.get(0).path("width").asInt()).isEqualTo(200);
    assertThat(ok(request(HttpMethod.GET, path, b, null)).isEmpty()).isTrue();
    assertThat(ok(request(HttpMethod.GET, "/user-config?key=table.test.role", a, null)).isEmpty()).isTrue();
    // 无十秒冷却：连续不同布局及重置都应立即成功，不产生 409。
    for (int i = 0; i < 3; i++) {
      ok(request(HttpMethod.POST, "/user-config/save", a,
          Map.of("key", "table.test.user", "value", List.of(Map.of("field", "username", "width", 220 + i)))));
    }
    assertThat(ok(request(HttpMethod.GET, path, a, null)).get(0).path("width").asInt()).isEqualTo(222);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_config WHERE user_id = ?", Long.class, created.get(0))).isEqualTo(1L);
    ok(request(HttpMethod.POST, "/user-config/save", a, Map.of("key", "table.test.user", "value", List.of())));
    assertThat(ok(request(HttpMethod.GET, path, a, null)).isEmpty()).isTrue();
    var invalid = request(HttpMethod.POST, "/user-config/save", a, Map.of("key", "table.test.user", "value", Map.of()));
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalid.getBody().path("message").asText()).contains("JSON array");
  }
}
