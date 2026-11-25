package com.edu.aitutor.module.privatechat.config;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.privatechat.service.PrivatePushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 私信 Redis Pub/Sub 订阅配置。
 * <p>
 * 订阅 {@link RedisKeys#WS_PRIVATE_CHANNEL}，用于跨实例投递：
 * A 连在实例1、B 连在实例2 时，实例1 发布、实例2 收到后推给本机上的 B。
 * <p>
 * 单实例部署也保持订阅开启：本实例会收到自己发布的消息，
 * 由 {@link PrivatePushService#onMessage} 按 originId 去重，不会重复推送。
 */
@Configuration
public class PrivatePubSubConfig {

    @Bean
    public RedisMessageListenerContainer privateMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PrivatePushService privatePushService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(privatePushService, new ChannelTopic(RedisKeys.WS_PRIVATE_CHANNEL));
        return container;
    }
}