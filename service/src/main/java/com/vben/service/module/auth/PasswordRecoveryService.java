package com.vben.service.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.module.auth.dto.RecoveryRequests;
import com.vben.service.module.auth.entity.RecoveryEmail;
import com.vben.service.module.auth.mapper.RecoveryEmailMapper;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.TokenVersionService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
  private final SysUserMapper users;
  private final RecoveryEmailMapper bindings;
  private final CaptchaService captcha;
  private final EmailCodeService codes;
  private final RecoveryMailSender mail;
  private final TokenVersionService versions;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public boolean configured() { return mail.configured(); }

  public BindingInfo binding(Long userId) {
    RecoveryEmail binding = bindings.selectById(userId);
    return new BindingInfo(configured(), binding == null ? "" : binding.getEmail());
  }

  public String sendBinding(Long userId, RecoveryRequests.SendBinding request) {
    mail.requireConfigured();
    requirePassword(users.selectById(userId), request.password());
    codes.reserve("bind", userId.toString());
    return codes.issue("bind", userId, request.email(), versions.current(userId));
  }

  @Transactional
  public void bind(Long userId, RecoveryRequests.Bind request) {
    requirePassword(bindings.lockUser(userId), request.password());
    EmailCodeService.Challenge challenge = codes.consume("bind", request.challengeId(), request.code());
    if (!challenge.userId().equals(userId.toString()) || challenge.version() != versions.current(userId)) {
      throw BizException.badRequest("error.recovery.code");
    }
    RecoveryEmail binding = new RecoveryEmail();
    binding.setUserId(userId);
    binding.setEmail(challenge.email());
    binding.setVerifiedAt(LocalDateTime.now());
    if (bindings.selectById(userId) == null) bindings.insert(binding);
    else bindings.updateById(binding);
  }

  public String sendReset(RecoveryRequests.SendReset request) {
    mail.requireConfigured();
    if (!captcha.verify(request.captchaId(), request.captchaCode())) {
      throw BizException.badRequest("error.auth.captcha");
    }
    codes.reserve("reset", request.username());
    SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, request.username()).eq(SysUser::getStatus, 0));
    RecoveryEmail binding = user == null ? null : bindings.selectById(user.getId());
    if (binding == null) return EmailCodeService.newId();
    return codes.issue("reset", user.getId(), binding.getEmail(), versions.current(user.getId()));
  }

  @Transactional
  public void reset(RecoveryRequests.Reset request) {
    if (!request.newPassword().equals(request.confirmPassword())) {
      throw BizException.badRequest("error.recovery.mismatch");
    }
    EmailCodeService.Challenge challenge = codes.consume("reset", request.challengeId(), request.code());
    Long userId = Long.valueOf(challenge.userId());
    SysUser user = bindings.lockUser(userId);
    RecoveryEmail binding = bindings.selectById(userId);
    if (!active(user) || binding == null || !binding.getEmail().equals(challenge.email())
        || challenge.version() != versions.current(userId)) {
      throw BizException.badRequest("error.recovery.code");
    }
    SysUser patch = new SysUser();
    patch.setId(userId);
    patch.setPassword(passwords.encode(request.newPassword()));
    if (users.updateById(patch) != 1) throw BizException.badRequest("error.recovery.code");
    versions.bump(userId);
  }

  private boolean active(SysUser user) { return user != null && Objects.equals(user.getStatus(), 0); }

  private void requirePassword(SysUser user, String password) {
    if (!active(user) || !passwords.matches(password, user.getPassword())) {
      throw BizException.badRequest("error.auth.oldPassword");
    }
  }

  public record BindingInfo(boolean enabled, String email) {}
}
