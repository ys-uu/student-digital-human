package com.edu.aitutor.module.rbac.repository;

import com.edu.aitutor.common.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 用户权限码缓存 Redis 操作：key 为 {@link RedisKeys#USER_PERMS}，
 * value 为权限码 JSON 数组，角色/权限变更时按 userId 主动清除。
 */
@Repository
public class PermissionCacheRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheRedisRepository.class);

    private static final TypeReference<List<String>> PERM_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionCacheRedisRepository(StringRedisTemplate redisTemplate,
                                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取权限码缓存；未命中或缓存反序列化失败时返回 null，由调用方回源 MySQL。
     */
    public List<String> find(Long userId) {
        String cached = redisTemplate.opsForValue().get(cacheKey(userId));
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, PERM_LIST_TYPE);
        } catch (Exception e) {
            log.warn("权限缓存反序列化失败，回源查询 userId={}", userId);
            return null;
        }
    }

    /**
     * 写入权限码缓存；写入失败只记录日志，不影响主流程。
     */
    public void save(Long userId, List<String> permCodes, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(userId), objectMapper.writeValueAsString(permCodes), ttl);
        } catch (Exception e) {
            log.warn("权限缓存写入失败 userId={} msg={}", userId, e.getMessage());
        }
    }

    /**
     * 按 userId 清除权限缓存。
     */
    public void evict(Long userId) {
        redisTemplate.delete(cacheKey(userId));
    }

    private String cacheKey(Long userId) {
        return String.format(RedisKeys.USER_PERMS, userId);
    }
}
