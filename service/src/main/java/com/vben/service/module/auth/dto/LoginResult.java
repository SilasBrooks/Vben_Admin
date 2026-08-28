package com.vben.service.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应（R.data），字段与前端 backend-mock 对齐：
 * mock 直接展开用户对象 + accessToken。不返回 password。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResult {

  private Long id;

  private String username;

  /** mock 字段名 realName */
  private String realName;

  private List<String> roles;

  private String homePath;

  private String accessToken;
}
