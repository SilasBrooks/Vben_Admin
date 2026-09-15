package com.vben.service.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具：优先取反向代理透传的 X-Forwarded-For 首段，降级 remoteAddr
 */
public final class IpUtil {

  private IpUtil() {}

  public static String getClientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      // 多级代理时第一个为客户端真实 IP
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
