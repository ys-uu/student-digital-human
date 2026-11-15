package com.edu.aitutor.framework.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册反爬虫配置属性，使 {@code anti-crawler.*} 能绑定到 {@link AntiCrawlerProperties}。
 */
@Configuration
@EnableConfigurationProperties(AntiCrawlerProperties.class)
public class AntiCrawlerConfig {
}