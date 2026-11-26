package com.edu.aitutor.module.auth.repository;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Refresh token 相关 Redis 操作：
 * <ul>
 *   <li>白名单 String：{@link RedisKeys#REFRESH_WHITELIST}，jti -> userId，刷新接口必校验；</li>
 *   <li>用户有效 jti 集合 Set：{@link RedisKeys#REFRESH_USER_SET}，支撑登出清理与全设备下线。</li>
 * </ul>
 */
@Repository
public class RefreshTokenRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入 refresh 白名单：jti -> userId，TTL 与 refresh 有效期一致。
     */
    public void saveWhitelist(String jti, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(whitelistKey(jti), userId.toString(), ttl);
    }

    /**
     * 读取白名单中记录的 userId，不存在返回 null。
     */
    public String findWhitelistUserId(String jti) {
        return redisTemplate.opsForValue().get(whitelistKey(jti));
    }

    /**
     * 删除 refresh 白名单。
     */
    public void deleteWhitelist(String jti) {
        redisTemplate.delete(whitelistKey(jti));
    }

    /**
     * 将 jti 加入用户登录集合，并刷新整个集合的 TTL。
     */
    public void addUserJti(Long userId, String jti, Duration ttl) {
        String key = userSetKey(userId);
        redisTemplate.opsForSet().add(key, jti);
        redisTemplate.expire(key, ttl);
    }

    /**
     * 从用户登录集合中移除指定 jti。
     */
    public void removeUserJti(Long userId, String jti) {
        redisTemplate.opsForSet().remove(userSetKey(userId), jti);
    }

    private String whitelistKey(String jti) {
        return String.format(RedisKeys.REFRESH_WHITELIST, jti);
    }

    private String userSetKey(Long userId) {
        return String.format(RedisKeys.REFRESH_USER_SET, userId);
    }
}
