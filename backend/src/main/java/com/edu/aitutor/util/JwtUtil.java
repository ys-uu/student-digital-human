package com.edu.aitutor.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /** HS256 要求密钥至少 32 字节（256 bit） */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * 签名密钥，从配置项 jwt.secret 注入。
     * 该配置在 yaml 中通过占位符读取环境变量 JWT_SECRET，容器/生产环境由环境变量注入，
     * 本地开发未设置环境变量时回退到默认值；Java 与 Python 必须使用同一密钥。
     */
    @Value("${jwt.secret}")
    private String secretString;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥长度不足：HS256 要求至少 " + MIN_SECRET_BYTES
                            + " 字节，当前为 " + keyBytes.length
                            + " 字节，请通过环境变量 JWT_SECRET 配置足够长的密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

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
