package com.edu.aitutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 反爬虫 / 接口频控配置。
 *
 * <p>所有默认值都内置于此类，未在 yaml 中配置时也能生效；
 * 需要调整阈值时在 {@code application-dev.yaml} 中覆盖，公共模板见
 * {@code application-dev.example.yaml} 的 {@code anti-crawler} 段。</p>
 */
@Data
@ConfigurationProperties(prefix = "anti-crawler")
public class AntiCrawlerProperties {

    /** 总开关，压测 / 本地排查时可临时关闭 */
    private boolean enabled = true;

    /** 是否给接口响应追加反抓取响应头（X-Robots-Tag、no-store 等） */
    private boolean addSecurityHeaders = true;

    /**
     * 路径限流规则，按声明顺序匹配，命中第一条即生效。
     * 默认顺序：认证接口 &gt; AI 接口 &gt; 扫码轮询 &gt; 通用 API。
     */
    private List<Rule> rules = new ArrayList<>(List.of(
            new Rule("auth", List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/sms/code",
                    "/api/v1/auth/sms/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/user/login"), 10, 60),
            new Rule("ai", List.of("/api/v1/ai/**"), 30, 60),
            new Rule("qr", List.of("/qr/**"), 120, 60),
            new Rule("api", List.of("/api/**", "/ws/**"), 300, 60)
    ));

    /** 不参与频控的路径：健康检查、错误页、robots 等 */
    private List<String> whitelistPaths = new ArrayList<>(List.of(
            "/api/v1/health", "/error", "/robots.txt", "/favicon.ico"));

    /**
     * 蜜罐路径：正常用户和前端不会访问，命中即临时封禁。
     * 这些路径通常也是扫描器/采集器最先探测的路径。
     */
    private List<String> honeypotPaths = new ArrayList<>(List.of(
            "/trap/**", "/.env", "/.git/config", "/wp-login.php",
            "/phpmyadmin/**", "/actuator/env"));

    private UserAgent userAgent = new UserAgent();

    private Ban ban = new Ban();

    private Login login = new Login();

    @Data
    public static class Rule {

        /** 规则名，用于 Redis key 和日志区分 */
        private String name;

        /** Ant 风格路径，如 /api/v1/auth/login、/api/** */
        private List<String> paths = new ArrayList<>();

        /** 窗口内允许的请求数 */
        private int limit = 120;

        /** 统计窗口（秒） */
        private int windowSeconds = 60;

        public Rule() {
        }

        public Rule(String name, List<String> paths, int limit, int windowSeconds) {
            this.name = name;
            this.paths = new ArrayList<>(paths);
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }

    @Data
    public static class UserAgent {

        private boolean enabled = true;

        /** 是否拦截空 User-Agent（脚本、部分采集器默认不带 UA） */
        private boolean blockEmpty = true;

        /** 命中任一关键字即视为自动化工具，关键字不区分大小写 */
        private List<String> blockedKeywords = new ArrayList<>(List.of(
                "python-requests", "python-urllib", "scrapy", "wget", "curl/",
                "httpclient", "okhttp", "go-http-client", "java/", "libwww-perl",
                "phantomjs", "headlesschrome", "selenium", "playwright", "puppeteer",
                "postmanruntime", "insomnia"));
    }

    @Data
    public static class Ban {

        private boolean enabled = true;

        /** 统计窗口内累计多少次违规后封禁 */
        private int violationThreshold = 10;

        /** 违规次数统计窗口（秒） */
        private int violationWindowSeconds = 300;

        /** 封禁时长（秒） */
        private int durationSeconds = 600;
    }

    @Data
    public static class Login {

        private boolean enabled = true;

        /** 同一账号在窗口内允许的连续失败次数 */
        private int maxFailures = 5;

        /** 同一 IP 在窗口内允许的连续失败次数（避免误伤 NAT 后的正常用户，阈值放宽） */
        private int maxIpFailures = 30;

        /** 失败次数统计窗口（秒） */
        private int windowSeconds = 900;

        /** 达到阈值后的锁定时长（秒） */
        private int lockSeconds = 900;
    }
}