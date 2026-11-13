package com.edu.aitutor.security;

import com.edu.aitutor.config.AntiCrawlerProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反爬过滤器单元测试：不依赖 Redis / 数据库 / Mockito，Guard 用内存 Fake 替代。
 *
 * <p>不使用 Mockito 是为了避免其 inline mock maker 在受限沙箱中动态加载 agent 失败。</p>
 */
class AntiCrawlerFilterTest {

    private AntiCrawlerProperties properties;
    private FakeGuard guard;
    private AntiCrawlerFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private boolean[] chainCalled;

    @BeforeEach
    void setUp() {
        properties = new AntiCrawlerProperties();
        properties.setRules(List.of(
                new AntiCrawlerProperties.Rule("api", List.of("/api/**"), 2, 60)));
        properties.setWhitelistPaths(List.of("/api/v1/health"));
        properties.setHoneypotPaths(List.of("/trap/**"));
        properties.getUserAgent().setBlockedKeywords(List.of("python-requests"));
        properties.getUserAgent().setBlockEmpty(true);

        guard = new FakeGuard(properties);
        filter = new AntiCrawlerFilter(properties, guard, JsonMapper.builder().build());

        request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        response = new MockHttpServletResponse();
        chainCalled = new boolean[]{false};
    }

    private FilterChain chain() {
        return (req, res) -> chainCalled[0] = true;
    }

    @Test
    void blockedUserAgentReturns403() throws Exception {
        request.removeHeader("User-Agent");
        request.addHeader("User-Agent", "python-requests/2.31.0");

        filter.doFilter(request, response, chain());

        assertEquals(403, response.getStatus());
        assertFalse(chainCalled[0]);
        assertTrue(response.getContentAsString().contains("\"code\":1013"));
        assertTrue(guard.violations.contains("bad-ua"));
    }

    @Test
    void emptyUserAgentReturns403WhenConfigured() throws Exception {
        request.removeHeader("User-Agent");

        filter.doFilter(request, response, chain());

        assertEquals(403, response.getStatus());
        assertFalse(chainCalled[0]);
    }

    @Test
    void rateLimitedRequestReturns429AndRetryAfter() throws Exception {
        guard.rateResult = new AntiCrawlerGuard.RateLimitResult(false, 0, 30);

        filter.doFilter(request, response, chain());

        assertEquals(429, response.getStatus());
        assertEquals("30", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("\"code\":1006"));
        assertFalse(chainCalled[0]);
        assertTrue(guard.violations.contains("rate-limit:api"));
    }

    @Test
    void allowedRequestPassesThroughWithHeaders() throws Exception {
        guard.rateResult = new AntiCrawlerGuard.RateLimitResult(true, 1, 0);

        filter.doFilter(request, response, chain());

        assertTrue(chainCalled[0]);
        assertEquals("noindex, nofollow, noarchive", response.getHeader("X-Robots-Tag"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("2", response.getHeader("X-RateLimit-Limit"));
        assertEquals("1", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void whitelistPathBypassesAllChecks() throws Exception {
        request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "python-requests/2.31.0");

        filter.doFilter(request, response, chain());

        assertTrue(chainCalled[0]);
        assertEquals(0, guard.rateChecks);
        assertTrue(guard.violations.isEmpty());
        assertTrue(guard.bans.isEmpty());
    }

    @Test
    void honeypotPathBansIpAndReturns403() throws Exception {
        request = new MockHttpServletRequest("GET", "/trap/admin");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "Mozilla/5.0");

        filter.doFilter(request, response, chain());

        assertEquals(403, response.getStatus());
        assertFalse(chainCalled[0]);
        assertTrue(guard.bans.contains("honeypot:/trap/admin"));
    }

    @Test
    void bannedIpIsRejectedBeforeRateLimit() throws Exception {
        guard.banned = true;
        guard.banRemainingSeconds = 120;

        filter.doFilter(request, response, chain());

        assertEquals(403, response.getStatus());
        assertEquals("120", response.getHeader("Retry-After"));
        assertFalse(chainCalled[0]);
        assertEquals(0, guard.rateChecks);
    }

    /**
     * 内存版 Guard：只覆盖过滤器用到的方法，避免真实 Redis 依赖。
     */
    private static final class FakeGuard extends AntiCrawlerGuard {

        private boolean banned;
        private long banRemainingSeconds;
        private int rateChecks;
        private AntiCrawlerGuard.RateLimitResult rateResult =
                new AntiCrawlerGuard.RateLimitResult(true, 1, 0);
        private final List<String> violations = new ArrayList<>();
        private final List<String> bans = new ArrayList<>();

        private FakeGuard(AntiCrawlerProperties properties) {
            super(null, properties);
        }

        @Override
        public boolean isBanned(String ip) {
            return banned;
        }

        @Override
        public long getBanRemainingSeconds(String ip) {
            return banRemainingSeconds;
        }

        @Override
        public void recordViolation(String ip, String reason) {
            violations.add(reason);
        }

        @Override
        public void ban(String ip, String reason) {
            bans.add(reason);
        }

        @Override
        public RateLimitResult checkRateLimit(String ip, AntiCrawlerProperties.Rule rule) {
            rateChecks++;
            return rateResult;
        }
    }
}