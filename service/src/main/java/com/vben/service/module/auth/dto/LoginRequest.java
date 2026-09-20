package com.vben.service.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求参数：必须携带服务端图形验证码（GET /auth/captcha 获取） */
@Data
public class LoginRequest {

  @NotBlank(message = "{error.login.credentials.blank}")
  private String username;

  @NotBlank(message = "{error.login.credentials.blank}")
  private String password;

  @NotBlank(message = "{error.login.captcha.blank}")
  private String captchaId;

  @NotBlank(message = "{error.login.captcha.blank}")
  private String captchaCode;
}
