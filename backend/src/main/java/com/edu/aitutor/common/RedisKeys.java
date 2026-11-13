package com.edu.aitutor.common;

/**
 * Redis key 统一规范，所有 key 以 ai_tutor: 开头。
 * 使用方式：String.format(RedisKeys.QR_LOGIN, qrToken)。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 扫码登录会话，value 为 QrSession JSON，TTL 120s */
    public static final String QR_LOGIN = "ai_tutor:qr:login:%s";

    /** 短信发送频控，60s 内只允许发送一次 */
    public static final String SMS_LIMIT = "ai_tutor:sms:limit:%s";

    /** 退出登录的 JWT 黑名单，TTL 为该 token 剩余有效期 */
    public static final String TOKEN_BLACKLIST = "ai_tutor:auth:blacklist:%s";

    /** 用户权限码集合缓存，value 为权限码 JSON 数组，TTL 24h 兜底，角色/权限变更时按 userId 主动清除 */
    public static final String USER_PERMS = "ai_tutor:rbac:perms:%s";

    // ==================== 反爬虫 / 风控 ====================

    /** 接口滑动窗口限流，参数为规则名 + 客户端 IP，value 为 ZSET */
    public static final String ANTI_RATE_LIMIT = "ai_tutor:anti:rate:%s:%s";

    /** 触发反爬规则后的临时封禁标记，value 为封禁原因，TTL 即封禁时长 */
    public static final String ANTI_BAN = "ai_tutor:anti:ban:%s";

    /** 违规次数累加器，TTL 为违规统计窗口 */
    public static final String ANTI_VIOLATION = "ai_tutor:anti:violation:%s";

    /** 账号维度登录失败次数，TTL 为统计窗口 */
    public static final String LOGIN_FAIL_ACCOUNT = "ai_tutor:auth:login:fail:account:%s";

    /** IP 维度登录失败次数，TTL 为统计窗口 */
    public static final String LOGIN_FAIL_IP = "ai_tutor:auth:login:fail:ip:%s";

    /** 账号维度登录锁定标记，TTL 即锁定时长 */
    public static final String LOGIN_LOCK_ACCOUNT = "ai_tutor:auth:login:lock:account:%s";

    /** IP 维度登录锁定标记，TTL 即锁定时长 */
    public static final String LOGIN_LOCK_IP = "ai_tutor:auth:login:lock:ip:%s";
}
