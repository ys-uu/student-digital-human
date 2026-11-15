package com.edu.aitutor.module.dashboard.mq;

import com.edu.aitutor.module.dashboard.websocket.DashboardWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 大屏刷新事件消费者。
 * 收到业务事件后：先让图表缓存失效，再立即向所有大屏连接推送一次。
 */
@Component
public class DashboardEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DashboardEventConsumer.class);

    private static final String CACHE_PATTERN = "dashboard:analytics:*";

    private final StringRedisTemplate redisTemplate;
    private final DashboardWebSocketHandler webSocketHandler;

    public DashboardEventConsumer(StringRedisTemplate redisTemplate,
                                  DashboardWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    @RabbitListener(queues = DashboardMqConfig.QUEUE)
    public void onRefresh(String payload) {
        evictCache();
        webSocketHandler.pushNow();
        log.debug("收到大屏刷新事件并完成推送：{}", payload);
    }

    private void evictCache() {
        try {
            Set<String> keys = redisTemplate.keys(CACHE_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("大屏缓存失效失败，交由 TTL 自然过期：{}", e.getMessage());
        }
    }
}
