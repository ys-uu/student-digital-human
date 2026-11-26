package com.edu.aitutor.framework.security;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.framework.config.AntiCrawlerProperties;
import com.edu.aitutor.framework.security.redis.AntiCrawlerRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 反爬虫风控组件：Redis 滑动窗口限流、违规计数、临时封禁、登录失败锁定。
 *
 * <p>所有 Redis 操作都做了降级处理：Redis 不可用时“放行 + 告警”，
 * 避免风控组件本身导致业务整体不可用；封禁和限流状态均带 TTL，不会永久累积。</p>
 */
@Slf4j
@Component
public class AntiCrawlerGuard {

    private final AntiCrawlerRedisRepository antiCrawlerRedisRepository;
    private final AntiCrawlerProperties properties;

    public AntiCrawlerGuard(AntiCrawlerRedisRepository antiCrawlerRedisRepository,
                            AntiCrawlerProperties properties) {
        this.antiCrawlerRedisRepository = antiCrawlerRedisRepository;
        this.properties = properties;
    }

    // ==================== 封禁 / 违规计数 ====================

    public boolean isBanned(String ip) {
        try {
            return antiCrawlerRedisRepository.banExists(ip);
        } catch (Exception e) {
            log.warn("反爬封禁状态查询失败，降级放行 ip={} err={}", ip, e.getMessage());
            return false;
        }
    }

    public long getBanRemainingSeconds(String ip) {
        try {
            return antiCrawlerRedisRepository.getBanTtlSeconds(ip);
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
            long count = antiCrawlerRedisRepository.incrementViolation(ip);
            if (count == 0) {
                return;
            }
            if (count == 1L) {
                antiCrawlerRedisRepository.expireViolation(
                        ip, Duration.ofSeconds(Math.max(1, ban.getViolationWindowSeconds())));
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
            antiCrawlerRedisRepository.saveBan(
                    ip, reason == null ? "blocked" : reason, Duration.ofSeconds(seconds));
            antiCrawlerRedisRepository.deleteViolation(ip);
            log.warn("触发反爬封禁 ip={} reason={} duration={}s", ip, reason, seconds);
        } catch (Exception e) {
            log.warn("反爬封禁写入失败 ip={} err={}", ip, e.getMessage());
        }
    }

    // ==================== 接口限流 ====================

    /**
     * 滑动窗口限流：同一 IP 在窗口内超过规则上限则拒绝。
     */
    public RateLimitResult checkRateLimit(String ip, AntiCrawlerProperties.Rule rule) {
        int limit = Math.max(1, rule.getLimit());
        int windowSeconds = Math.max(1, rule.getWindowSeconds());
        try {
            List<Long> result = antiCrawlerRedisRepository.executeSlidingWindow(
                    rule.getName(), ip, limit, windowSeconds);
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
            long accountTtl = antiCrawlerRedisRepository.getTtlSeconds(
                    RedisKeys.LOGIN_LOCK_ACCOUNT, account);
            long ipTtl = antiCrawlerRedisRepository.getTtlSeconds(
                    RedisKeys.LOGIN_LOCK_IP, ip);
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
            long accountCount = antiCrawlerRedisRepository.incrementAndExpire(
                    RedisKeys.LOGIN_FAIL_ACCOUNT, account,
                    Duration.ofSeconds(Math.max(1, login.getWindowSeconds())));
            if (accountCount >= Math.max(1, login.getMaxFailures())) {
                antiCrawlerRedisRepository.setMarker(
                        RedisKeys.LOGIN_LOCK_ACCOUNT, account,
                        Duration.ofSeconds(Math.max(1, login.getLockSeconds())));
                log.warn("账号登录失败次数超限，临时锁定 account={} ip={}", mask(account), ip);
            }

            long ipCount = antiCrawlerRedisRepository.incrementAndExpire(
                    RedisKeys.LOGIN_FAIL_IP, ip,
                    Duration.ofSeconds(Math.max(1, login.getWindowSeconds())));
            if (ipCount >= Math.max(1, login.getMaxIpFailures())) {
                antiCrawlerRedisRepository.setMarker(
                        RedisKeys.LOGIN_LOCK_IP, ip,
                        Duration.ofSeconds(Math.max(1, login.getLockSeconds())));
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
            antiCrawlerRedisRepository.delete(RedisKeys.LOGIN_FAIL_ACCOUNT, account);
            antiCrawlerRedisRepository.delete(RedisKeys.LOGIN_LOCK_ACCOUNT, account);
        } catch (Exception e) {
            log.warn("登录失败计数清理失败 account={} err={}", mask(account), e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

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
