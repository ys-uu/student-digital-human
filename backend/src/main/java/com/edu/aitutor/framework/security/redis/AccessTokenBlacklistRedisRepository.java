package com.edu.aitutor.framework.security.redis;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Access token 黑名单 Redis 操作：key 为 {@link RedisKeys#TOKEN_BLACKLIST}，
 * 登出时写入，JWT 过滤器鉴权时校验，TTL 为 token 剩余有效期。
 */
@Repository
public class AccessTokenBlacklistRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public AccessTokenBlacklistRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将 access token 加入黑名单，TTL 为该 token 的剩余有效期。
     */
    public void blacklist(String accessToken, Duration ttl) {
        redisTemplate.opsForValue().set(blacklistKey(accessToken), "1", ttl);
    }

    /**
     * 判断 access token 是否在黑名单中。
     */
    public boolean existsBlacklist(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(accessToken)));
    }

    private String blacklistKey(String accessToken) {
        return String.format(RedisKeys.TOKEN_BLACKLIST, accessToken);
    }
}
