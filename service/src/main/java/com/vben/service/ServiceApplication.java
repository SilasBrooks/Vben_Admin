package com.vben.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Vben Admin 5 配套后端服务
 *
 * <p>接口协议与前端 apps/backend-mock 完全一致，可直接替换 mock：
 * <ul>
 *   <li>POST /api/auth/login   登录，返回 accessToken + Set-Cookie(refreshToken)</li>
 *   <li>POST /api/auth/refresh 用 Cookie 中的 refreshToken 换新 accessToken</li>
 *   <li>POST /api/auth/logout  登出，清除 Cookie</li>
 *   <li>GET  /api/auth/codes   当前用户权限码（按钮级权限）</li>
 *   <li>GET  /api/user/info    当前用户信息（含角色）</li>
 *   <li>GET  /api/menu/all     当前用户动态路由菜单树</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.vben.service.module.**.mapper")
public class ServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ServiceApplication.class, args);
  }
}
