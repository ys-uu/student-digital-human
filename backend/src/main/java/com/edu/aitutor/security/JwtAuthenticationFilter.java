package com.edu.aitutor.security;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：解析 token、校验黑名单、加载权限并填充 SecurityContext，无状态不依赖会话。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private RbacUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                // 退出登录的 token 黑名单，命中即视为凭证失效
                if (Boolean.TRUE.equals(
                        redisTemplate.hasKey(String.format(RedisKeys.TOKEN_BLACKLIST, token)))) {
                    throw new BadCredentialsException("token 已被加入黑名单，请重新登录");
                }
                Long userId = Long.valueOf(claims.get("userId").toString());
                LoginUser loginUser = userDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 兼容现有 Controller 通过 request.getAttribute 读取用户标识的用法
                request.setAttribute("userId", userId);
                request.setAttribute("username", claims.getSubject());
                request.setAttribute("role", claims.get("role"));
            } catch (Exception e) {
                // 仅处理本过滤器自身的 token 异常：清空认证后继续，受保护资源由授权层返回 1002
                log.warn("token 校验失败 uri={} reason={}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        try {
            // 链路只执行一次，下游异常不在此吞掉，保证安全异常与业务异常正常传播
            filterChain.doFilter(request, response);
        } finally {
            // 无状态请求结束即清理线程上下文，避免容器线程复用导致身份串号
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
