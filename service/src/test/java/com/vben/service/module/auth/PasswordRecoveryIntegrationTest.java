package com.vben.service.module.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 真实 HTTP + PostgreSQL + Redis；只替换邮件投递器，不向外部发信，测试账号最终删除。 */
@EnabledIfEnvironmentVariable(named = "RECOVERY_INTEGRATION_TEST", matches = "true")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "vben.captcha.echo-enabled=true",
    "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class PasswordRecoveryIntegrationTest {
  @org.junit.jupiter.api.AfterAll
  static void restoreStandaloneMessages() {
    // 容器初始化了全局消息源；归还给其余无容器单测的初始状态。
    org.springframework.test.util.ReflectionTestUtils.setField(com.vben.service.common.I18nMessage.class, "messageSource", null);
  }
  @Autowired TestRestTemplate http;
  @Autowired SysUserMapper users;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @MockitoBean RecoveryMailSender mail;
  private String token;
  private final AtomicReference<String> delivered = new AtomicReference<>();

  private ResponseEntity<JsonNode> request(HttpMethod method, String path, Map<String, ?> body, boolean authenticated) {
    HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Forwarded-For", "198.51.100.137"); headers.set("Accept-Language", "en-US");
    if (authenticated) headers.setBearerAuth(token);
    // TestRestTemplate 的根地址已经包含 server.servlet.context-path。
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }
  private JsonNode ok(ResponseEntity<JsonNode> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().path("code").asInt()).isZero();
    return response.getBody().path("data");
  }
  private JsonNode captcha() { return ok(request(HttpMethod.GET, "/auth/captcha", null, false)); }
  private JsonNode login(String username, String password) {
    JsonNode captcha = captcha();
    return ok(request(HttpMethod.POST, "/auth/login", Map.of("username", username, "password", password,
        "captchaId", captcha.path("captchaId").asText(), "captchaCode", captcha.path("devCode").asText()), false));
  }

  @Test void verifiedMailboxCanRecoverPasswordAndOldTokensBecomeInvalid() {
    when(mail.configured()).thenReturn(true);
    doAnswer(call -> { delivered.set(call.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), anyString());
    String username = "recovery_it_" + UUID.randomUUID().toString().substring(0, 8);
    SysUser user = new SysUser(); user.setUsername(username); user.setNickname(username); user.setStatus(0);
    user.setHomePath("/profile"); user.setPassword(new BCryptPasswordEncoder().encode("OldPass123!"));
    user.setEmail("unverified@example.invalid"); users.insert(user);
    try {
      assertThat(request(HttpMethod.POST, "/auth/register", Map.of(), false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(request(HttpMethod.GET, "/user/recovery-email", null, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      ResponseEntity<JsonNode> docsResponse = request(HttpMethod.GET, "/v3/api-docs", null, false);
      assertThat(docsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode docs = docsResponse.getBody();
      assertThat(docs.path("paths").has("/auth/register")).isFalse();
      token = login(username, "OldPass123!").path("accessToken").asText();
      assertThat(ok(request(HttpMethod.GET, "/user/recovery-email", null, true)).path("email").asText()).isEmpty();
      String bindId = ok(request(HttpMethod.POST, "/user/recovery-email/code",
          Map.of("email", "verified@example.invalid", "password", "OldPass123!"), true)).path("challengeId").asText();
      verify(mail).send(eq("verified@example.invalid"), anyString(), eq("bind"));
      ok(request(HttpMethod.POST, "/user/recovery-email",
          Map.of("challengeId", bindId, "code", delivered.get(), "password", "OldPass123!"), true));
      assertThat(ok(request(HttpMethod.GET, "/user/recovery-email", null, true)).path("email").asText()).isEqualTo("verified@example.invalid");
      // 普通联系资料更新不能替换找回邮箱。
      ok(request(HttpMethod.PATCH, "/user/profile", Map.of("nickname", username, "email", "changed@example.invalid"), true));
      assertThat(ok(request(HttpMethod.GET, "/user/recovery-email", null, true)).path("email").asText()).isEqualTo("verified@example.invalid");

      JsonNode captcha = captcha();
      String resetId = ok(request(HttpMethod.POST, "/auth/recovery/code", Map.of("username", username,
          "captchaId", captcha.path("captchaId").asText(), "captchaCode", captcha.path("devCode").asText()), false)).path("challengeId").asText();
      verify(mail).send(eq("verified@example.invalid"), anyString(), eq("reset"));
      String code = delivered.get();
      assertThat(request(HttpMethod.POST, "/auth/recovery/reset", Map.of("challengeId", resetId, "code", code,
          "newPassword", "weak", "confirmPassword", "weak"), false).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      ok(request(HttpMethod.POST, "/auth/recovery/reset", Map.of("challengeId", resetId, "code", code,
          "newPassword", "NewPass123!", "confirmPassword", "NewPass123!"), false));
      assertThat(request(HttpMethod.GET, "/user/info", null, true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(login(username, "NewPass123!").path("accessToken").asText()).isNotBlank();
      assertThat(new BCryptPasswordEncoder().matches("OldPass123!", users.selectById(user.getId()).getPassword())).isFalse();
    } finally {
      jdbc.update("DELETE FROM sys_login_log WHERE username = ?", username);
      users.deleteById(user.getId());
      redis.delete(java.util.List.of(RedisKeys.online(user.getId()), RedisKeys.tokenVer(user.getId())));
    }
  }
}
