package com.vben.service.module.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vben.service.common.BizException;
import com.vben.service.module.auth.dto.RecoveryRequests;
import com.vben.service.module.auth.entity.RecoveryEmail;
import com.vben.service.module.auth.mapper.RecoveryEmailMapper;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.TokenVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordRecoveryServiceTest {
  private final SysUserMapper users = mock(SysUserMapper.class);
  private final RecoveryEmailMapper bindings = mock(RecoveryEmailMapper.class);
  private final CaptchaService captcha = mock(CaptchaService.class);
  private final EmailCodeService codes = mock(EmailCodeService.class);
  private final RecoveryMailSender mail = mock(RecoveryMailSender.class);
  private final TokenVersionService versions = mock(TokenVersionService.class);
  private final PasswordRecoveryService service = new PasswordRecoveryService(users, bindings, captcha, codes, mail, versions);
  private SysUser user;
  private RecoveryEmail binding;
  private final String id = "c".repeat(32);

  @BeforeEach
  void setup() {
    user = new SysUser(); user.setId(42L); user.setStatus(0); user.setUsername("recoveryuser");
    user.setPassword(new BCryptPasswordEncoder().encode("OldPass123!"));
    user.setEmail("unverified@example.invalid");
    binding = new RecoveryEmail(); binding.setUserId(42L); binding.setEmail("verified@example.invalid");
    when(users.selectById(42L)).thenReturn(user);
    when(bindings.lockUser(42L)).thenReturn(user);
    when(captcha.verify("captcha", "abcd")).thenReturn(true);
    when(codes.consume(anyString(), eq(id), eq("123456"))).thenReturn(
        new EmailCodeService.Challenge("42", binding.getEmail(), 0, "digest", 0));
  }

  private RecoveryRequests.SendReset sendRequest() { return new RecoveryRequests.SendReset("recoveryuser", "captcha", "abcd"); }
  private RecoveryRequests.Reset resetRequest() { return new RecoveryRequests.Reset(id, "123456", "NewPass123!", "NewPass123!"); }

  @Test void captchaFailurePreventsAccountLookupAndDelivery() {
    when(captcha.verify(anyString(), anyString())).thenReturn(false);
    assertThatThrownBy(() -> service.sendReset(sendRequest())).isInstanceOf(BizException.class).hasMessage("error.auth.captcha");
    verifyNoInteractions(users, bindings, codes);
  }

  @Test void nonexistentAndUnboundAccountsReturnOpaqueChallengesWithoutSending() {
    assertThat(service.sendReset(sendRequest())).matches("[a-f0-9]{32}");
    when(users.selectOne(any())).thenReturn(user);
    assertThat(service.sendReset(sendRequest())).matches("[a-f0-9]{32}");
    verify(codes, never()).issue(anyString(), anyLong(), anyString(), anyLong());
  }

  @Test void resetDeliveryUsesVerifiedEmailInsteadOfContactEmail() {
    when(users.selectOne(any())).thenReturn(user);
    when(bindings.selectById(42L)).thenReturn(binding);
    when(codes.issue("reset", 42L, binding.getEmail(), 0)).thenReturn(id);
    assertThat(service.sendReset(sendRequest())).isEqualTo(id);
    verify(codes).issue("reset", 42L, "verified@example.invalid", 0);
  }

  @Test void bindingRequiresCurrentPasswordBeforeSendingOrConsumingCode() {
    assertThatThrownBy(() -> service.sendBinding(42L, new RecoveryRequests.SendBinding("new@example.invalid", "wrong"))).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.bind(42L, new RecoveryRequests.Bind(id, "123456", "wrong"))).isInstanceOf(BizException.class);
    verifyNoInteractions(codes);
  }

  @Test void bindingStoresOnlyVerifiedChallengeAddress() {
    service.bind(42L, new RecoveryRequests.Bind(id, "123456", "OldPass123!"));
    ArgumentCaptor<RecoveryEmail> saved = ArgumentCaptor.forClass(RecoveryEmail.class);
    verify(bindings).insert(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(42L);
    assertThat(saved.getValue().getEmail()).isEqualTo(binding.getEmail());
    assertThat(saved.getValue().getVerifiedAt()).isNotNull();
    verify(users, never()).updateById(any(SysUser.class));
  }

  @Test void anotherUsersBindingChallengeCannotBeClaimed() {
    when(codes.consume("bind", id, "123456")).thenReturn(new EmailCodeService.Challenge("99", "other@example.invalid", 0, "digest", 0));
    assertThatThrownBy(() -> service.bind(42L, new RecoveryRequests.Bind(id, "123456", "OldPass123!"))).isInstanceOf(BizException.class);
    verify(bindings, never()).insert(any(RecoveryEmail.class));
  }

  @Test void passwordChangeInvalidatesBindingChallenge() {
    when(versions.current(42L)).thenReturn(1L);
    assertThatThrownBy(() -> service.bind(42L, new RecoveryRequests.Bind(id, "123456", "OldPass123!"))).isInstanceOf(BizException.class);
    verify(bindings, never()).insert(any(RecoveryEmail.class));
  }

  @Test void resetStoresHashAndInvalidatesExistingTokens() {
    when(bindings.selectById(42L)).thenReturn(binding);
    when(users.updateById(any(SysUser.class))).thenReturn(1);
    service.reset(resetRequest());
    ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
    verify(users).updateById(saved.capture());
    assertThat(new BCryptPasswordEncoder().matches("NewPass123!", saved.getValue().getPassword())).isTrue();
    verify(versions).bump(42L);
  }

  @ParameterizedTest @ValueSource(strings = {"disabled", "deleted", "emailChanged", "unbound", "versionChanged"})
  void changesAfterDeliveryPreventReset(String change) {
    when(bindings.selectById(42L)).thenReturn(binding);
    switch (change) {
      case "disabled" -> user.setStatus(1);
      case "deleted" -> when(bindings.lockUser(42L)).thenReturn(null);
      case "emailChanged" -> binding.setEmail("changed@example.invalid");
      case "unbound" -> when(bindings.selectById(42L)).thenReturn(null);
      case "versionChanged" -> when(versions.current(42L)).thenReturn(1L);
      default -> throw new IllegalArgumentException();
    }
    assertThatThrownBy(() -> service.reset(resetRequest())).isInstanceOf(BizException.class);
    verify(users, never()).updateById(any(SysUser.class)); verify(versions, never()).bump(anyLong());
  }

  @Test void mismatchedPasswordsDoNotConsumeEmailCode() {
    assertThatThrownBy(() -> service.reset(new RecoveryRequests.Reset(id, "123456", "NewPass123!", "Different123!"))).hasMessage("error.recovery.mismatch");
    verifyNoInteractions(codes);
  }

  @Test void redisFailureCannotUpdatePassword() {
    when(codes.consume("reset", id, "123456")).thenThrow(new RedisConnectionFailureException("unavailable"));
    assertThatThrownBy(() -> service.reset(resetRequest())).isInstanceOf(RedisConnectionFailureException.class);
    verify(users, never()).updateById(any(SysUser.class));
  }

  @Test void requestToStringNeverLeaksCredentials() {
    assertThat(resetRequest().toString()).doesNotContain("NewPass", "123456");
    assertThat(new RecoveryRequests.SendBinding("test@example.invalid", "OldPass123!").toString()).doesNotContain("OldPass");
  }
}
