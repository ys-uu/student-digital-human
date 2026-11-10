package com.edu.aitutor.interceptor;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 已停用：全局 JWT 鉴权已迁移至 security.JwtAuthenticationFilter 与 SecurityConfig。
 * 保留本类仅作旧逻辑参考与回滚用途，不再注册为 Spring Bean，也不再被 WebConfig 引用。
 */
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    /** 免鉴权路径：登录注册、短信、扫码接口（对齐接口文档第 3 章） */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/sms/code",
            "/api/v1/auth/sms/login",
            "/api/v1/auth/refresh",
            "/api/v1/user/login",
            "/qr/create",
            "/qr/status",
            "/qr/scan",
            "/qr/confirm",
            "/qr/cancel",
            "/qr/mock-users",
            "/api/v1/health",
            "/error"
    );

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (WHITE_LIST.contains(request.getRequestURI())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            // 退出登录的 token 黑名单
            Boolean blacklisted = redisTemplate.hasKey(String.format(RedisKeys.TOKEN_BLACKLIST, token));
            if (Boolean.TRUE.equals(blacklisted)) {
                log.info("token 已被加入黑名单");
                response.setStatus(401);
                return false;
            }
            request.setAttribute("userId", Long.valueOf(claims.get("userId").toString()));
            request.setAttribute("username", claims.getSubject());
            request.setAttribute("role", claims.get("role"));
            return true;
        } catch (Exception e) {
            log.warn("token 校验失败: {}", e.getMessage());
            response.setStatus(401);
            return false;
        }
    }
}
