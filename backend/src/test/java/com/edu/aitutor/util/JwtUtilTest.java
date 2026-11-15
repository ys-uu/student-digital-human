package com.edu.aitutor.util;

import com.edu.aitutor.framework.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 密钥外置后的单元测试：不启动完整 Spring 容器，
 * 通过 ReflectionTestUtils 模拟 @Value 注入的 jwt.secret。
 */
class JwtUtilTest {

    private static final String DEFAULT_SECRET = "aitutor-secret-key-minimum32chars-long-123456";

    private JwtUtil buildUtil(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secretString", secret);
        return util;
    }

    @Test
    void init_validSecret_signAndParseRoundTrip() {
        JwtUtil util = buildUtil(DEFAULT_SECRET);
        util.init();

        String token = util.generateAccessToken(1001L, "2024100236", 1);
        Claims claims = util.parseToken(token);

        assertEquals("2024100236", claims.getSubject());
        assertEquals(1001L, util.getUserId(token));
        assertEquals("1", claims.get("role"));
        assertEquals("access", claims.get("tokenType"));
        assertTrue(util.verifyToken(token));
    }

    @Test
    void init_refreshToken_carriesRefreshType() {
        JwtUtil util = buildUtil(DEFAULT_SECRET);
        util.init();

        String refresh = util.generateRefreshToken(1001L, "2024100236", 1);
        assertEquals("refresh", util.parseToken(refresh).get("tokenType"));
    }

    @Test
    void init_secretShorterThan32Bytes_failFast() {
        JwtUtil util = buildUtil("short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, util::init);
        assertTrue(ex.getMessage().contains("JWT 密钥长度不足"));
    }

    @Test
    void verifyToken_signedWithDifferentSecret_isRejected() {
        JwtUtil issuer = buildUtil(DEFAULT_SECRET);
        issuer.init();
        String token = issuer.generateAccessToken(1L, "userNo", 1);

        JwtUtil verifier = buildUtil("another-totally-different-secret-32bytes-xxxxx");
        verifier.init();

        // 换密钥后旧 token 无法通过校验，证明密钥确实参与签名且可替换
        assertFalse(verifier.verifyToken(token));
    }

    @Test
    void springContext_wiresValueAndRunsPostConstruct() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(JwtUtil.class);
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test-props", Map.of("jwt.secret", DEFAULT_SECRET)));
            ctx.refresh();

            // 能拿到 Bean 并正常签发，说明 @Value 已注入、@PostConstruct 已执行
            JwtUtil util = ctx.getBean(JwtUtil.class);
            String token = util.generateAccessToken(2L, "T20001", 2);
            assertEquals("T20001", util.parseToken(token).getSubject());
            assertEquals("2", util.parseToken(token).get("role"));
        }
    }

    @Test
    void placeholder_resolvesEnvVarWhenSet_otherwiseDefault() {
        ConfigurableEnvironment env = new StandardEnvironment();

        // 未设置 JWT_SECRET：回退到冒号后的默认值
        String withDefault = "default-secret-32bytes-long-zzzzzzzzzz";
        assertEquals(withDefault, env.resolvePlaceholders("${JWT_SECRET:" + withDefault + "}"));

        // 设置 JWT_SECRET：优先使用环境变量值
        env.getPropertySources().addFirst(
                new MapPropertySource("fake-env", Map.of("JWT_SECRET", "env-secret-32bytes-long-yyyyyyyyyy")));
        assertEquals("env-secret-32bytes-long-yyyyyyyyyy",
                env.resolvePlaceholders("${JWT_SECRET:" + withDefault + "}"));
    }
}
