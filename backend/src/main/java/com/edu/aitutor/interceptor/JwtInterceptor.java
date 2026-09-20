package com.edu.aitutor.interceptor;

import com.edu.aitutor.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("请求uri: {}, 请求方法:{}", request.getRequestURI(),request.getMethod());
        if("OPTIONS".equalsIgnoreCase(request.getMethod())){
            log.info("放行OPTIONS请求");
            return true;
        }
        String uri = request.getRequestURI();
        if ("/api/v1/user/login".equals(uri) || "/error".equals(uri)) {
            log.info("放行登录/error接口");
            return true;
        }

        // 解析Authorization Bearer token
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if(authHeader != null && authHeader.startsWith("Bearer ")){
            token = authHeader.substring(7);
        }
        if (token == null || token.isEmpty()) {
            log.info("token为空，返回401");
            response.setStatus(401);
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            if (jwtUtil.isExpired(token)) {
                log.info("token过期，返回401");
                response.setStatus(401);
                return false;
            }
            request.setAttribute("username", claims.getSubject());
            request.setAttribute("role", claims.get("role"));
            log.info("token校验通过");
            return true;
        } catch (Exception e) {
            log.error("token解析异常",e);
            response.setStatus(401);
            return false;
        }
    }
}
