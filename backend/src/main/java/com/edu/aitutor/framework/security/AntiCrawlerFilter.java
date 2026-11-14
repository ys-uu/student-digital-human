package com.edu.aitutor.framework.security;

import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.framework.config.AntiCrawlerProperties;
import com.edu.aitutor.framework.web.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 反爬虫入口过滤器，位于 Spring Security 之前，尽量在业务逻辑和数据库之前拦截自动化流量。
 *
 * <p>处理顺序：IP 封禁 → 蜜罐路径 → User-Agent 识别 → 路径限流 → 放行。
 * 触发拦截时返回统一的 JSON {@link Result}，HTTP 状态使用 403 / 429，
 * 便于 Nginx、前端和监控直接识别。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AntiCrawlerFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AntiCrawlerProperties properties;
    private final AntiCrawlerGuard guard;
    private final JsonMapper jsonMapper;

    public AntiCrawlerFilter(AntiCrawlerProperties properties,
                             AntiCrawlerGuard guard,
                             JsonMapper jsonMapper) {
        this.properties = properties;
        this.guard = guard;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        // CORS 预检不带业务数据，放行避免影响浏览器跨域
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return matchesAny(properties.getWhitelistPaths(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = ClientIpUtil.resolve(request);
        addSecurityHeaders(response);

        try {
            // 1. 已被封禁的 IP：直接拒绝，不再消耗 Redis 限流脚本
            if (guard.isBanned(ip)) {
                long retrySeconds = Math.max(1, guard.getBanRemainingSeconds(ip));
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_BLOCKED,
                        "访问异常，已被临时限制，请稍后再试", retrySeconds);
                return;
            }

            // 2. 蜜罐路径：正常前端不会访问，命中即视为扫描器并立即封禁
            if (matchesAny(properties.getHoneypotPaths(), path)) {
                guard.ban(ip, "honeypot:" + path);
                log.warn("命中蜜罐路径 ip={} path={} ua={}", ip, path, request.getHeader("User-Agent"));
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_BLOCKED,
                        "访问被拒绝", 0);
                return;
            }

            // 3. 明显是脚本 / 采集工具的 User-Agent
            if (isBadUserAgent(request.getHeader("User-Agent"))) {
                guard.recordViolation(ip, "bad-ua");
                log.warn("拦截可疑 User-Agent ip={} path={} ua={}",
                        ip, path, request.getHeader("User-Agent"));
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_BLOCKED,
                        "访问被拒绝", 0);
                return;
            }

            // 4. 路径级滑动窗口限流
            AntiCrawlerProperties.Rule rule = matchRule(path);
            if (rule != null) {
                AntiCrawlerGuard.RateLimitResult result = guard.checkRateLimit(ip, rule);
                response.setHeader("X-RateLimit-Limit", String.valueOf(rule.getLimit()));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

                if (!result.allowed()) {
                    long retrySeconds = Math.max(1, result.retryAfterSeconds());
                    response.setHeader("X-RateLimit-Reset",
                            String.valueOf(System.currentTimeMillis() / 1000 + retrySeconds));
                    guard.recordViolation(ip, "rate-limit:" + rule.getName());
                    log.warn("接口限流触发 ip={} path={} rule={} retry={}s",
                            ip, path, rule.getName(), retrySeconds);
                    writeError(response, 429, ErrorCode.TOO_FREQUENT,
                            "请求过于频繁，请稍后再试", retrySeconds);
                    return;
                }
            }
        } catch (IOException e) {
            // 写响应失败（如客户端断开）不再降级放行，交由容器处理
            throw e;
        } catch (Exception e) {
            // 风控组件自身异常时降级放行，优先保证业务可用性
            log.warn("反爬组件异常，降级放行 ip={} path={} err={}", ip, path, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private AntiCrawlerProperties.Rule matchRule(String path) {
        List<AntiCrawlerProperties.Rule> rules = properties.getRules();
        if (rules == null) {
            return null;
        }
        for (AntiCrawlerProperties.Rule rule : rules) {
            if (rule != null && matchesAny(rule.getPaths(), path)) {
                return rule;
            }
        }
        return null;
    }

    private boolean isBadUserAgent(String userAgent) {
        AntiCrawlerProperties.UserAgent config = properties.getUserAgent();
        if (config == null || !config.isEnabled()) {
            return false;
        }
        String value = userAgent == null ? "" : userAgent.trim();
        if (value.isEmpty()) {
            return config.isBlockEmpty();
        }
        String lower = value.toLowerCase(Locale.ROOT);
        List<String> keywords = config.getBlockedKeywords();
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null || path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        if (!properties.isAddSecurityHeaders()) {
            return;
        }
        // 明确告知搜索引擎 / 采集器不要收录接口内容
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
    }

    private void writeError(HttpServletResponse response, int status, int code, String message,
                            long retryAfterSeconds) throws IOException {
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}