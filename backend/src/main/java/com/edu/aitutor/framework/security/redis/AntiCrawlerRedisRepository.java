package com.edu.aitutor.framework.security.redis;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 反爬风控 Redis 操作：滑动窗口限流 Lua、违规计数、临时封禁、登录失败锁定。
 * <p>本类只执行命令并抛出底层异常，降级策略由 AntiCrawlerGuard 决定。
 */
@Repository
public class AntiCrawlerRedisRepository {

    /**
     * 滑动窗口限流脚本：清理窗口外的记录，未超限则写入当前请求并返回剩余额度。
     * 返回 {是否放行(1/0), 剩余额度, 建议重试毫秒数}。
     */
    private static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local count = redis.call('ZCARD', key)

            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, window)
                return {1, limit - count - 1, 0}
            end

            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local retry = window
            if oldest[2] then
                retry = window - (now - tonumber(oldest[2]))
                if retry < 1 then
                    retry = 1
                end
            end
            return {0, 0, retry}
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(SLIDING_WINDOW_LUA, List.class);

    private final StringRedisTemplate redisTemplate;

    public AntiCrawlerRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 封禁 / 违规计数 ====================

    /**
     * 封禁标记是否存在。
     */
    public boolean banExists(String ip) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(String.format(RedisKeys.ANTI_BAN, ip)));
    }

    /**
     * 查询封禁剩余秒数，无 TTL 或不存在时返回 0。
     */
    public long getBanTtlSeconds(String ip) {
        Long ttl = redisTemplate.getExpire(
                String.format(RedisKeys.ANTI_BAN, ip), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /**
     * 写入临时封禁标记。
     */
    public void saveBan(String ip, String reason, Duration ttl) {
        redisTemplate.opsForValue().set(
                String.format(RedisKeys.ANTI_BAN, ip),
                reason == null ? "blocked" : reason,
                ttl);
    }

    /**
     * 违规计数 +1，返回递增后的值。
     */
    public long incrementViolation(String ip) {
        Long count = redisTemplate.opsForValue()
                .increment(String.format(RedisKeys.ANTI_VIOLATION, ip));
        return count == null ? 0 : count;
    }

    /**
     * 设置违规计数窗口 TTL。
     */
    public void expireViolation(String ip, Duration ttl) {
        redisTemplate.expire(String.format(RedisKeys.ANTI_VIOLATION, ip), ttl);
    }

    /**
     * 清除违规计数。
     */
    public void deleteViolation(String ip) {
        redisTemplate.delete(String.format(RedisKeys.ANTI_VIOLATION, ip));
    }

    // ==================== 接口限流 ====================

    /**
     * 执行滑动窗口限流脚本，返回原始结果 {是否放行, 剩余额度, 建议重试毫秒数}。
     */
    @SuppressWarnings("unchecked")
    public List<Long> executeSlidingWindow(String ruleName, String ip, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        return (List<Long>) redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(String.format(RedisKeys.ANTI_RATE_LIMIT, ruleName, ip)),
                String.valueOf(now),
                String.valueOf(windowSeconds * 1000L),
                String.valueOf(limit),
                now + ":" + UUID.randomUUID());
    }

    // ==================== 登录失败 / 锁定（按 key 模板通用操作） ====================

    /**
     * 计数 +1，仅在首次（值为 1）时设置窗口 TTL。
     */
    public long incrementAndExpire(String keyPattern, String id, Duration ttl) {
        String key = String.format(keyPattern, id);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    /**
     * 写入标记位（如锁定标记）。
     */
    public void setMarker(String keyPattern, String id, Duration ttl) {
        redisTemplate.opsForValue().set(String.format(keyPattern, id), "1", ttl);
    }

    /**
     * 查询 key 剩余秒数，无 TTL 或不存在时返回 0。
     */
    public long getTtlSeconds(String keyPattern, String id) {
        Long ttl = redisTemplate.getExpire(String.format(keyPattern, id), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /**
     * 删除指定 key。
     */
    public void delete(String keyPattern, String id) {
        redisTemplate.delete(String.format(keyPattern, id));
    }
}
