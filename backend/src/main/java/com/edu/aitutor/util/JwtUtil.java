package com.edu.aitutor.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 密钥至少32字符，生产环境请替换
    private static final String SECRET_STR = "aitutor-secret-key-minimum32chars-long-123456";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET_STR.getBytes(StandardCharsets.UTF_8));

    /** access token 有效期：2小时 */
    private static final long ACCESS_EXPIRATION = 2 * 60 * 60 * 1000L;
    /** refresh token 有效期：7天 */
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /**
     * 兼容旧调用，生成 access token
     */
    public String generateToken(String username, String role, Long userId) {
        return buildToken(userId, username, role, TYPE_ACCESS, ACCESS_EXPIRATION);
    }

    public String generateAccessToken(Long userId, String userNo, Integer role) {
        return buildToken(userId, userNo, String.valueOf(role), TYPE_ACCESS, ACCESS_EXPIRATION);
    }

    public String generateRefreshToken(Long userId, String userNo, Integer role) {
        return buildToken(userId, userNo, String.valueOf(role), TYPE_REFRESH, REFRESH_EXPIRATION);
    }

    private String buildToken(Long userId, String subject, String role, String tokenType, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .claim("role", role)
                .claim("userId", userId)
                .claim("tokenType", tokenType)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 token 格式、签名与有效期
     */
    public boolean verifyToken(String token) {
        try {
            return parseToken(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).get("userId").toString());
    }

    /**
     * token 剩余有效期（毫秒），用于退出登录黑名单 TTL
     */
    public long getRemainingExpirationMillis(String token) {
        return parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
    }

    public long getAccessExpirationSeconds() {
        return ACCESS_EXPIRATION / 1000;
    }
}
