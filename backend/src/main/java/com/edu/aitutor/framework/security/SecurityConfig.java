package com.edu.aitutor.framework.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 全局配置：无状态 JWT 鉴权、方法级权限开关、CORS 与统一异常处理。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 免鉴权白名单，与原 JwtInterceptor 保持一致：登录注册、短信、扫码、健康检查、错误页
     */
    private static final String[] WHITE_LIST = {
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
            // WebSocket 握手走 HTTP GET，凭证在 query 参数中，由 WebSocketHandshakeInterceptor 校验
            "/ws/**",
            "/error"
    };

    @Value("${cors.allow-origins}")
    private String[] allowOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        // 防点击劫持，等价 X-Frame-Options: DENY
                        .frameOptions(frame -> frame.deny())
                        // 禁止 MIME 嗅探，等价 X-Content-Type-Options: nosniff
                        .contentTypeOptions(opts -> {})
                        // HSTS 留待 HTTPS 阶段开启，当前显式关闭
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                        // 限制浏览器敏感能力：本系统不使用摄像头、麦克风、定位
                        // permissionsPolicy 自 6.4 起废弃并将移除，改用 permissionsPolicyHeader
                        // （返回 HeadersConfigurer，写法与其他安全头一致）
                        .permissionsPolicyHeader(pp -> pp.policy(
                                "camera=(), microphone=(), geolocation=()"))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 关闭 JWT 过滤器在 Servlet 容器的自动注册，避免其在 Security 链外重复执行
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * CORS 配置源：cors(Customizer.withDefaults()) 读取此 Bean，替代原 WebConfig/CorsConfig
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 密码编码器：保持 BCrypt，与现有密码哈希完全兼容
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
