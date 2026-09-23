package com.vben.service.module.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.common.redis.RedisKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 显式启用的本地 Redis 集成验证；仅操作随机挑战键，不依赖或清空现有业务状态。 */
@EnabledIfEnvironmentVariable(named = "RECOVERY_INTEGRATION_TEST", matches = "true")
class EmailCodeRedisTest {
  private LettuceConnectionFactory connection;
  private StringRedisTemplate redis;
  private EmailCodeService codes;
  private RecoveryMailSender mail;
  private final AtomicReference<String> delivered = new AtomicReference<>();
  private final List<String> keys = new ArrayList<>();

  @BeforeEach void setup() {
    connection = new LettuceConnectionFactory("127.0.0.1", 6379);
    connection.afterPropertiesSet(); connection.start();
    redis = new StringRedisTemplate(connection);
    mail = mock(RecoveryMailSender.class);
    doAnswer(call -> { delivered.set(call.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), anyString());
    codes = new EmailCodeService(redis, new ObjectMapper(), mail);
  }
  @AfterEach void cleanup() { if (redis != null) redis.delete(keys); if (connection != null) connection.destroy(); }
  private String issue() {
    String id = codes.issue("reset", 123L, "test@example.invalid", 0);
    keys.add(RedisKeys.emailCode("reset", id)); return id;
  }

  @Test void codeIsHashedExpiresAndCannotBeReplayedOrUsedForBinding() {
    String id = issue(); String code = delivered.get();
    assertThat(redis.opsForValue().get(keys.getFirst())).doesNotContain(code);
    assertThat(redis.getExpire(keys.getFirst())).isBetween(295L, 300L);
    assertThatThrownBy(() -> codes.consume("bind", id, code)).isInstanceOf(BizException.class);
    assertThat(codes.consume("reset", id, code).userId()).isEqualTo("123");
    assertThatThrownBy(() -> codes.consume("reset", id, code)).isInstanceOf(BizException.class);
  }

  @Test void fiveWrongAttemptsInvalidateCodeWithoutExtendingExpiry() {
    String id = issue(); String correct = delivered.get(); String wrong = correct.equals("000000") ? "999999" : "000000";
    for (int i = 0; i < 5; i++) assertThatThrownBy(() -> codes.consume("reset", id, wrong)).isInstanceOf(BizException.class);
    assertThat(redis.hasKey(keys.getFirst())).isFalse();
    assertThatThrownBy(() -> codes.consume("reset", id, correct)).isInstanceOf(BizException.class);
  }

  @Test void concurrentConsumptionHasOnlyOneWinner() throws Exception {
    String id = issue(); String code = delivered.get();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var action = (java.util.concurrent.Callable<Boolean>) () -> {
        try { codes.consume("reset", id, code); return true; } catch (BizException e) { return false; }
      };
      var results = pool.invokeAll(List.of(action, action));
      assertThat(results.get(0).get()).isNotEqualTo(results.get(1).get());
    }
  }

  @Test void expiredCodeIsRejected() {
    String id = issue(); redis.delete(keys.getFirst());
    assertThatThrownBy(() -> codes.consume("reset", id, delivered.get())).isInstanceOf(BizException.class);
  }
}
