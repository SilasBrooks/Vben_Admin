package com.vben.service.common.websocket;

import com.vben.service.common.redis.RedisKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * WebSocket 跨实例广播装配：订阅 ws:push 频道。
 *
 * <p>项目无既有 Redis 配置类（StringRedisTemplate 走 Spring Boot 自动装配），
 * 此处补一个最小 listener container，仅挂 {@link WsRedisBroadcaster} 一个监听器。
 */
@Configuration
public class WebSocketBroadcastConfig {

  @Bean
  public RedisMessageListenerContainer wsRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, WsRedisBroadcaster broadcaster) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(broadcaster, new ChannelTopic(RedisKeys.wsPush()));
    return container;
  }
}
