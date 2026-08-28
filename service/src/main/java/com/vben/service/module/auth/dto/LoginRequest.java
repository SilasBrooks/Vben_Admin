package com.vben.service.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求参数 */
@Data
public class LoginRequest {

  @NotBlank(message = "Username and password are required")
  private String username;

  @NotBlank(message = "Username and password are required")
  private String password;
}
