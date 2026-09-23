package com.vben.service.module.auth;

import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.auth.dto.RecoveryRequests;
import com.vben.service.security.LoginUserHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "密码找回与安全邮箱")
@RestController
@RequiredArgsConstructor
public class PasswordRecoveryController {
  private final PasswordRecoveryService recovery;

  @GetMapping("/auth/recovery/options")
  public R<Map<String, Boolean>> options() { return R.ok(Map.of("enabled", recovery.configured())); }

  @PostMapping("/auth/recovery/code")
  @RateLimit(name = "recovery:send", limit = 5, windowSeconds = 60)
  @Idempotent(name = "recovery:send", intervalSeconds = 3)
  public R<Map<String, String>> send(@Valid @RequestBody RecoveryRequests.SendReset request) {
    return R.ok(Map.of("challengeId", recovery.sendReset(request)));
  }

  @PostMapping("/auth/recovery/reset")
  @RateLimit(name = "recovery:reset", limit = 10, windowSeconds = 60)
  @Idempotent(name = "recovery:reset", intervalSeconds = 3)
  public R<Void> reset(@Valid @RequestBody RecoveryRequests.Reset request) {
    recovery.reset(request);
    return R.ok();
  }

  @GetMapping("/user/recovery-email")
  public R<PasswordRecoveryService.BindingInfo> binding() {
    return R.ok(recovery.binding(LoginUserHolder.require().getUserId()));
  }

  @PostMapping("/user/recovery-email/code")
  @RateLimit(name = "recovery:bind:send", limit = 5, windowSeconds = 60, scope = RateLimit.Scope.USER)
  @Idempotent(name = "recovery:bind:send", intervalSeconds = 3)
  public R<Map<String, String>> sendBinding(@Valid @RequestBody RecoveryRequests.SendBinding request) {
    return R.ok(Map.of("challengeId", recovery.sendBinding(LoginUserHolder.require().getUserId(), request)));
  }

  @PostMapping("/user/recovery-email")
  @RateLimit(name = "recovery:bind", limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
  @Idempotent(name = "recovery:bind", intervalSeconds = 3)
  public R<Void> bind(@Valid @RequestBody RecoveryRequests.Bind request) {
    recovery.bind(LoginUserHolder.require().getUserId(), request);
    return R.ok();
  }
}
