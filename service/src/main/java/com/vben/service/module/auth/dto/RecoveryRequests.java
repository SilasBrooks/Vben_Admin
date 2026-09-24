package com.vben.service.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RecoveryRequests {
  private RecoveryRequests() {}

  public record SendReset(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 64) String captchaId,
      @NotBlank @Size(max = 4) String captchaCode) {
    @Override public String toString() { return "SendReset[redacted]"; }
  }

  public record Reset(
      @NotBlank @Pattern(regexp = "[a-f0-9]{32}") String challengeId,
      @NotBlank @Pattern(regexp = "[0-9]{6}", message = "{error.recovery.code}") String code,
      @NotBlank @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}",
          message = "{error.recovery.password}") String newPassword,
      @NotBlank @Size(max = 64) String confirmPassword) {
    @Override public String toString() { return "Reset[redacted]"; }
  }

  public record SendBinding(
      @NotBlank @Email(message = "{error.user.email.invalid}") @Size(max = 255) String email,
      @NotBlank @Size(max = 128) String password) {
    @Override public String toString() { return "SendBinding[redacted]"; }
  }

  public record Bind(
      @NotBlank @Pattern(regexp = "[a-f0-9]{32}") String challengeId,
      @NotBlank @Pattern(regexp = "[0-9]{6}", message = "{error.recovery.code}") String code,
      @NotBlank @Size(max = 128) String password) {
    @Override public String toString() { return "Bind[redacted]"; }
  }
}
