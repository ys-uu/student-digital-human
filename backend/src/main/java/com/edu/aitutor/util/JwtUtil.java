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
    // 密钥字符串，至少32字符，自己替换成别的随机串
    private static final String SECRET_STR = "aitutor-secret-key-minimum32chars-long-123456";
    private final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STR.getBytes(StandardCharsets.UTF_8));

    // 过期时间：2小时，单位毫秒
    private final long EXPIRATION = 2 * 60 * 60 * 1000;

    // 生成token，携带用户名、角色
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRATION);
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // 解析token，获取载荷
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 判断token是否过期
    public boolean isExpired(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration().before(new Date());
    }

    /**
     * 校验token是否合法（格式正确、签名正确、未过期）
     * @param token jwt字符串
     * @return true=合法；false=非法/过期/篡改
     */
    public boolean verifyToken(String token) {
        try {
            Claims claims = parseToken(token);
            // 解析成功并且未过期
            return !isExpired(token);
        } catch (Exception e) {
            // 任何异常：签名错误、过期、格式错误、null都返回false
            return false;
        }
    }

    /**
     * 从token中获取userId
     * 注意：你现在generateToken里subject是username，我新增重载方案：
     * 方案A：修改生成token，把userId放到claim，推荐（适合你们系统）
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        // 读取claim中存入的userId
        return Long.valueOf(claims.get("userId").toString());
    }

}
