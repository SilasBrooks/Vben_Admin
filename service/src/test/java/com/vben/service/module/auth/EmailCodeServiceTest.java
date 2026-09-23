package com.vben.service.module.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class EmailCodeServiceTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final RecoveryMailSender mail = mock(RecoveryMailSender.class);
  private final EmailCodeService codes = new EmailCodeService(redis, new ObjectMapper(), mail);
  @BeforeEach void setup() { when(redis.opsForValue()).thenReturn(values); }

  @Test void failedDeliveryRemovesChallenge() {
    doThrow(BizException.badRequest("error.recovery.delivery")).when(mail).send(anyString(), anyString(), anyString());
    assertThatThrownBy(() -> codes.issue("reset", 12L, "test@example.invalid", 0)).isInstanceOf(BizException.class);
    verify(redis).delete(startsWith("vben:email:code:reset:"));
  }

  @Test void claimedCooldownAndIndeterminateRedisResultBothReject() {
    when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false, null);
    assertThatThrownBy(() -> codes.reserve("reset", "username")).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> codes.reserve("reset", "username")).isInstanceOf(BizException.class);
    verifyNoInteractions(mail);
  }

  @Test void challengeHasFiveMinuteTtlAndNoRawCodeInRedis() {
    String id = codes.issue("bind", 12L, "test@example.invalid", 7);
    var code = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mail).send(eq("test@example.invalid"), code.capture(), eq("bind"));
    var payload = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(values).set(eq("vben:email:code:bind:" + id), payload.capture(), eq(Duration.ofMinutes(5)));
    assertThat(code.getValue()).matches("[0-9]{6}");
    assertThat(payload.getValue()).doesNotContain(code.getValue());
  }
}
