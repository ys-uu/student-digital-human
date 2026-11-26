package com.edu.aitutor.module.privatechat.repository.redis;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 私信在线状态 Redis 操作：每个 WebSocket 连接对应一个独立 key
 * （{@link RedisKeys#WS_ONLINE} 后追加 sessionId），单独 TTL，心跳续期。
 */
@Repository
public class OnlineStateRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public OnlineStateRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入/续期单个连接的在线标记。
     */
    public void saveSession(Long userId, String sessionId, Duration ttl) {
        redisTemplate.opsForValue().set(sessionKey(userId, sessionId), sessionId, ttl);
    }

    /**
     * 清除单个连接的在线标记。
     */
    public void deleteSession(Long userId, String sessionId) {
        redisTemplate.delete(sessionKey(userId, sessionId));
    }

    /**
     * 判断该用户是否存在任意在线连接（SCAN，不阻塞）。
     */
    public boolean existsAnySession(Long userId) {
        Boolean exists = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(userPattern(userId))
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                return cursor.hasNext();
            }
        });
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 判断指定连接的在线标记是否仍存在。
     */
    public boolean hasSession(Long userId, String sessionId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(sessionKey(userId, sessionId)));
    }

    private String sessionKey(Long userId, String sessionId) {
        return String.format(RedisKeys.WS_ONLINE, userId) + ":" + sessionId;
    }

    private String userPattern(Long userId) {
        return String.format(RedisKeys.WS_ONLINE, userId) + ":*";
    }
}
