package com.edu.aitutor.framework.security;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.framework.config.AntiCrawlerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 反爬虫风控组件：Redis 滑动窗口限流、违规计数、临时封禁、登录失败锁定。
 *
 * <p>所有 Redis 操作都做了降级处理：Redis 不可用时“放行 + 告警”，
 * 避免风控组件本身导致业务整体不可用；封禁和限流状态均带 TTL，不会永久累积。</p>
 */
@Slf4j
@Component
public class AntiCrawlerGuard {

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
    private final AntiCrawlerProperties properties;

    public AntiCrawlerGuard(StringRedisTemplate redisTemplate, AntiCrawlerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    // ==================== 封禁 / 违规计数 ====================

    public boolean isBanned(String ip) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(String.format(RedisKeys.ANTI_BAN, ip)));
        } catch (Exception e) {
            log.warn("反爬封禁状态查询失败，降级放行 ip={} err={}", ip, e.getMessage());
            return false;
        }
    }

    public long getBanRemainingSeconds(String ip) {
        try {
            Long ttl = redisTemplate.getExpire(String.format(RedisKeys.ANTI_BAN, ip), TimeUnit.SECONDS);
            return ttl == null || ttl < 0 ? 0 : ttl;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 记录一次违规；达到阈值后自动临时封禁该 IP。
     */
    public void recordViolation(String ip, String reason) {
        AntiCrawlerProperties.Ban ban = properties.getBan();
        if (ban == null || !ban.isEnabled()) {
            return;
        }
        try {
            String key = String.format(RedisKeys.ANTI_VIOLATION, ip);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, ban.getViolationWindowSeconds())));
            }
            if (count >= Math.max(1, ban.getViolationThreshold())) {
                ban(ip, reason + ":count=" + count);
            }
        } catch (Exception e) {
            log.warn("反爬违规计数失败 ip={} err={}", ip, e.getMessage());
        }
    }

    /**
     * 立即临时封禁 IP，并清空违规计数。
     */
    public void ban(String ip, String reason) {
        AntiCrawlerProperties.Ban ban = properties.getBan();
        if (ban == null || !ban.isEnabled()) {
            return;
        }
        try {
            int seconds = Math.max(1, ban.getDurationSeconds());
            redisTemplate.opsForValue().set(
                    String.format(RedisKeys.ANTI_BAN, ip),
                    reason == null ? "blocked" : reason,
                    Duration.ofSeconds(seconds));
            redisTemplate.delete(String.format(RedisKeys.ANTI_VIOLATION, ip));
            log.warn("触发反爬封禁 ip={} reason={} duration={}s", ip, reason, seconds);
        } catch (Exception e) {
            log.warn("反爬封禁写入失败 ip={} err={}", ip, e.getMessage());
        }
    }

    // ==================== 接口限流 ====================

    /**
     * 滑动窗口限流：同一 IP 在窗口内超过规则上限则拒绝。
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult checkRateLimit(String ip, AntiCrawlerProperties.Rule rule) {
        int limit = Math.max(1, rule.getLimit());
        int windowSeconds = Math.max(1, rule.getWindowSeconds());
        String key = String.format(RedisKeys.ANTI_RATE_LIMIT, rule.getName(), ip);
        long now = System.currentTimeMillis();
        try {
            List<Long> result = (List<Long>) redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowSeconds * 1000L),
                    String.valueOf(limit),
                    now + ":" + UUID.randomUUID());
            if (result == null || result.size() < 3) {
                return RateLimitResult.allowed(limit);
            }
            boolean allowed = toLong(result.get(0)) == 1L;
            long remaining = Math.max(0, toLong(result.get(1)));
            long retryMillis = Math.max(0, toLong(result.get(2)));
            long retrySeconds = (retryMillis + 999) / 1000;
            return new RateLimitResult(allowed, remaining, retrySeconds);
        } catch (Exception e) {
            log.warn("接口限流执行失败，降级放行 ip={} rule={} err={}", ip, rule.getName(), e.getMessage());
            return RateLimitResult.allowed(limit);
        }
    }

    // ==================== 登录失败保护 ====================

    /**
     * 登录前检查账号 / IP 是否已被临时锁定，未锁定则放行。
     */
    public void assertLoginAllowed(String account, String ip) {
        AntiCrawlerProperties.Login login = properties.getLogin();
        if (login == null || !login.isEnabled()) {
            return;
        }
        try {
            long accountTtl = remainingSeconds(RedisKeys.LOGIN_LOCK_ACCOUNT, account);
            long ipTtl = remainingSeconds(RedisKeys.LOGIN_LOCK_IP, ip);
            long ttl = Math.max(accountTtl, ipTtl);
            if (ttl > 0) {
                throw new BusinessException(ErrorCode.TOO_FREQUENT,
                        "登录尝试过于频繁，请 " + humanDuration(ttl) + " 后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录风控检查失败，降级放行 account={} ip={} err={}", mask(account), ip, e.getMessage());
        }
    }

    /**
     * 记录一次登录失败，账号维度到达阈值后锁定；IP 维度阈值更高，避免 NAT 误伤。
     */
    public void recordLoginFailure(String account, String ip) {
        AntiCrawlerProperties.Login login = properties.getLogin();
        if (login == null || !login.isEnabled()) {
            return;
        }
        try {
            long accountCount = increment(RedisKeys.LOGIN_FAIL_ACCOUNT, account, login.getWindowSeconds());
            if (accountCount >= Math.max(1, login.getMaxFailures())) {
                lock(RedisKeys.LOGIN_LOCK_ACCOUNT, account, login.getLockSeconds());
                log.warn("账号登录失败次数超限，临时锁定 account={} ip={}", mask(account), ip);
            }

            long ipCount = increment(RedisKeys.LOGIN_FAIL_IP, ip, login.getWindowSeconds());
            if (ipCount >= Math.max(1, login.getMaxIpFailures())) {
                lock(RedisKeys.LOGIN_LOCK_IP, ip, login.getLockSeconds());
                log.warn("IP 登录失败次数超限，临时锁定 ip={}", ip);
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入失败 account={} ip={} err={}", mask(account), ip, e.getMessage());
        }
    }

    /**
     * 登录成功后清空该账号的失败计数和锁定标记（IP 维度保留，防止换账号继续爆破）。
     */
    public void clearLoginFailure(String account) {
        try {
            redisTemplate.delete(String.format(RedisKeys.LOGIN_FAIL_ACCOUNT, account));
            redisTemplate.delete(String.format(RedisKeys.LOGIN_LOCK_ACCOUNT, account));
        } catch (Exception e) {
            log.warn("登录失败计数清理失败 account={} err={}", mask(account), e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

    private long increment(String pattern, String id, int windowSeconds) {
        String key = String.format(pattern, id);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, windowSeconds)));
        }
        return value == null ? 0 : value;
    }

    private void lock(String pattern, String id, int lockSeconds) {
        redisTemplate.opsForValue().set(
                String.format(pattern, id),
                "1",
                Duration.ofSeconds(Math.max(1, lockSeconds)));
    }

    private long remainingSeconds(String pattern, String id) {
        Long ttl = redisTemplate.getExpire(String.format(pattern, id), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String humanDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        return ((seconds + 59) / 60) + " 分钟";
    }

    private static String mask(String account) {
        if (account == null || account.length() <= 4) {
            return "****";
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }

    /**
     * 限流结果：是否放行、剩余额度、建议重试秒数。
     */
    public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {

        public static RateLimitResult allowed(long remaining) {
            return new RateLimitResult(true, remaining, 0);
        }
    }
}