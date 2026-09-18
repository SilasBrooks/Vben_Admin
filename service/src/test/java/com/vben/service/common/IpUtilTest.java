package com.vben.service.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端 IP 提取单测：XFF 单值 / 多级代理链 / 无 XFF 回退 remoteAddr / 空请求。
 */
class IpUtilTest {

  @Test
  void returnsFirstXffEntry() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.7");
    request.setRemoteAddr("172.18.0.5");
    assertThat(IpUtil.getClientIp(request)).isEqualTo("203.0.113.7");
  }

  @Test
  void returnsFirstOfChainedXff() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2, 10.0.0.3");
    request.setRemoteAddr("172.18.0.5");
    assertThat(IpUtil.getClientIp(request)).isEqualTo("203.0.113.7");
  }

  @Test
  void fallsBackToRemoteAddrWithoutXff() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.1.9");
    assertThat(IpUtil.getClientIp(request)).isEqualTo("192.168.1.9");
  }

  @Test
  void blankXffFallsBackToRemoteAddr() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "  ");
    request.setRemoteAddr("192.168.1.9");
    assertThat(IpUtil.getClientIp(request)).isEqualTo("192.168.1.9");
  }

  @Test
  void nullRequestReturnsNull() {
    assertThat(IpUtil.getClientIp(null)).isNull();
  }
}
