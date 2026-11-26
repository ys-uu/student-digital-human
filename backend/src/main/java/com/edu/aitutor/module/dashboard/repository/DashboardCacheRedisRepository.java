package com.edu.aitutor.module.dashboard.repository;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.dashboard.vo.DashboardAnalyticsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Set;

/**
 * 数据大屏聚合快照 Redis 操作：key 前缀为 {@link RedisKeys#DASHBOARD_ANALYTICS_PREFIX}，
 * value 为 {@link DashboardAnalyticsSnapshot} JSON，业务事件触发批量失效。
 */
@Repository
public class DashboardCacheRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(DashboardCacheRedisRepository.class);

    /** 批量失效匹配模式，第一阶段沿用 KEYS，行为优化另行排期。 */
    private static final String EVICT_PATTERN = RedisKeys.DASHBOARD_ANALYTICS_PREFIX + "*";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardCacheRedisRepository(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取大屏快照缓存；未命中、空值或反序列化失败时返回 null，由调用方回源 MySQL。
     */
    public DashboardAnalyticsSnapshot findAnalytics(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, DashboardAnalyticsSnapshot.class);
        } catch (Exception e) {
            log.warn("读取大屏缓存失败，回退直查数据库：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入大屏快照缓存；写入失败只记录日志，不影响主查询。
     */
    public void saveAnalytics(String cacheKey, DashboardAnalyticsSnapshot snapshot, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(snapshot), ttl);
        } catch (Exception e) {
            log.warn("写入大屏缓存失败：{}", e.getMessage());
        }
    }

    /**
     * 批量失效所有大屏快照缓存；失败时交由 TTL 自然过期。
     */
    public void evictAllAnalyticsCache() {
        try {
            Set<String> keys = redisTemplate.keys(EVICT_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("大屏缓存失效失败，交由 TTL 自然过期：{}", e.getMessage());
        }
    }
}
