package com.edu.aitutor.module.dashboard.mq;

import com.edu.aitutor.module.dashboard.repository.DashboardCacheRedisRepository;
import com.edu.aitutor.module.dashboard.websocket.DashboardWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 大屏刷新事件消费者。
 * 收到业务事件后：先让图表缓存失效，再立即向所有大屏连接推送一次。
 */
@Component
public class DashboardEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DashboardEventConsumer.class);

    private final DashboardCacheRedisRepository dashboardCacheRedisRepository;
    private final DashboardWebSocketHandler webSocketHandler;

    public DashboardEventConsumer(DashboardCacheRedisRepository dashboardCacheRedisRepository,
                                  DashboardWebSocketHandler webSocketHandler) {
        this.dashboardCacheRedisRepository = dashboardCacheRedisRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @RabbitListener(queues = DashboardMqConfig.QUEUE)
    public void onRefresh(String payload) {
        dashboardCacheRedisRepository.evictAllAnalyticsCache();
        webSocketHandler.pushNow();
        log.debug("收到大屏刷新事件并完成推送：{}", payload);
    }
}
