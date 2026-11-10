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
}
