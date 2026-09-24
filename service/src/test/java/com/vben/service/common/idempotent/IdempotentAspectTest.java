package com.vben.service.common.idempotent;

import com.vben.service.common.BizException;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 防重复提交切面单测：mock Redis 占位结果，验证放行 / 拒绝 / 未登录降级 IP 三分支。
 */
class IdempotentAspectTest {

  private StringRedisTemplate redis;
  private IdempotentAspect aspect;
  private ProceedingJoinPoint pjp;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    aspect = new IdempotentAspect(redis);
    pjp = mock(ProceedingJoinPoint.class);
  }

  @AfterEach
  void tearDown() {
    LoginUserHolder.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  private Idempotent annotation() {
    // 注解实例直接以匿名实现模拟（@Around("@annotation(...)") 绑定的是参数实例）
    return new Idempotent() {
      @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return Idempotent.class;
      }
      @Override public String name() { return "user:profile"; }
      @Override public String key() { return ""; }
      @Override public boolean releaseAfterCompletion() { return false; }
      @Override public int intervalSeconds() { return 10; }
      @Override public String message() { return "请勿重复提交"; }
    };
  }

  @Test
  void firstRequestClaimsAndProceeds() throws Throwable {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
    LoginUserHolder.set(new LoginUser(1L, "vben", List.of("super"), null, 0));

    aspect.around(pjp, annotation());

    verify(pjp).proceed();
    // 键 = vben:idempotent:user:profile:u1，TTL = 10s
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass((Class) List.class);
    verify(redis).execute(any(DefaultRedisScript.class), keys.capture(), anyString(), anyString());
    assertThat(keys.getValue()).containsExactly("vben:idempotent:user:profile:u1");
    verify(redis).execute(any(DefaultRedisScript.class), anyList(), org.mockito.ArgumentMatchers.eq("10"), anyString());
  }

  @Test
  void duplicateRequestRejectedWith409() throws Throwable {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L);
    LoginUserHolder.set(new LoginUser(1L, "vben", List.of("super"), null, 0));

    assertThatThrownBy(() -> aspect.around(pjp, annotation()))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getStatus())
        .isEqualTo(409);
    verify(pjp, never()).proceed();
  }

  @Test
  void anonymousFallsBackToClientIp() throws Throwable {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.7");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    aspect.around(pjp, annotation());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass((Class) List.class);
    verify(redis).execute(any(DefaultRedisScript.class), keys.capture(), anyString(), anyString());
    assertThat(keys.getValue()).containsExactly("vben:idempotent:user:profile:203.0.113.7");
  }

  @Test
  void configKeyIsolatedAndOwnTokenReleasedAfterSuccessOrFailure() throws Throwable {
    var annotation = com.vben.service.module.user.UserConfigController.class
        .getMethod("save", com.vben.service.module.user.UserConfigController.SaveRequest.class)
        .getAnnotation(Idempotent.class);
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
    LoginUserHolder.set(new LoginUser(1L, "vben", List.of("super"), null, 0));
    when(pjp.getArgs()).thenReturn(new Object[] {
        new com.vben.service.module.user.UserConfigController.SaveRequest("table.user", null)
    });
    aspect.around(pjp, annotation);
    when(pjp.getArgs()).thenReturn(new Object[] {
        new com.vben.service.module.user.UserConfigController.SaveRequest("table.role", null)
    });
    when(pjp.proceed()).thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> aspect.around(pjp, annotation)).isInstanceOf(IllegalStateException.class);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> claims = ArgumentCaptor.forClass((Class) List.class);
    ArgumentCaptor<String> claimTokens = ArgumentCaptor.forClass(String.class);
    verify(redis, org.mockito.Mockito.times(2)).execute(any(DefaultRedisScript.class), claims.capture(),
        org.mockito.ArgumentMatchers.eq("10"), claimTokens.capture());
    assertThat(claims.getAllValues().get(0)).isNotEqualTo(claims.getAllValues().get(1));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> releases = ArgumentCaptor.forClass((Class) List.class);
    ArgumentCaptor<String> releasedTokens = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<DefaultRedisScript> releaseScripts = ArgumentCaptor.forClass(DefaultRedisScript.class);
    verify(redis, org.mockito.Mockito.times(2)).execute(releaseScripts.capture(), releases.capture(), releasedTokens.capture());
    assertThat(releases.getAllValues()).isEqualTo(claims.getAllValues());
    assertThat(releasedTokens.getAllValues()).isEqualTo(claimTokens.getAllValues());
    assertThat(releaseScripts.getValue().getScriptAsString()).contains("redis.call('GET', KEYS[1]) == ARGV[1]");
  }
}
