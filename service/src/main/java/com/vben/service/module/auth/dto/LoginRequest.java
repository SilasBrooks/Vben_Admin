package com.vben.service.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求参数：必须携带服务端图形验证码（GET /auth/captcha 获取） */
@Data
public class LoginRequest {

  @NotBlank(message = "Username and password are required")
  private String username;

  @NotBlank(message = "Username and password are required")
  private String password;

  @NotBlank(message = "验证码不能为空")
  private String captchaId;

  @NotBlank(message = "验证码不能为空")
  private String captchaCode;
}
