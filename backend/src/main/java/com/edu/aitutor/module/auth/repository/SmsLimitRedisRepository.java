package com.edu.aitutor.module.auth.repository;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 短信发送频控 Redis 操作：key 为 {@link RedisKeys#SMS_LIMIT}，60 秒内只允许发送一次。
 */
@Repository
public class SmsLimitRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public SmsLimitRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取短信发送锁：key 不存在时写入成功并返回 true，已存在则返回 false。
     */
    public boolean tryAcquireSendLock(String phone, Duration ttl) {
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(
                String.format(RedisKeys.SMS_LIMIT, phone), "1", ttl);
        return Boolean.TRUE.equals(allowed);
    }
}
