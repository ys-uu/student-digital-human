package com.edu.aitutor.module.privatechat.repository.redis;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 私信 Redis Pub/Sub 频道操作：向 {@link RedisKeys#WS_PRIVATE_CHANNEL}
 * 广播跨实例投递报文。
 */
@Repository
public class PrivateChannelRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public PrivateChannelRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 向私信广播频道发布报文。
     */
    public void publish(String payload) {
        redisTemplate.convertAndSend(RedisKeys.WS_PRIVATE_CHANNEL, payload);
    }
}
