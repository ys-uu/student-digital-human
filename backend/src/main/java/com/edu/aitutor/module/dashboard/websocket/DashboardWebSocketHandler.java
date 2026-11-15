package com.edu.aitutor.module.dashboard.websocket;

import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.dashboard.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据大屏实时推送通道（/ws/dashboard）。
 * 以固定节拍聚合一次指标后广播给所有已连接的教务端，避免每条业务事件都触发查询。
 */
@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final Set<WebSocketSession> SESSIONS = ConcurrentHashMap.newKeySet();

    /** 兜底推送节拍（秒）：主链路由 RabbitMQ 事件触发，定时仅作降级保障 */
    private static final long PUSH_INTERVAL_SECONDS = 10L;

    private final DashboardService dashboardService;
    /** Spring Boot 4 默认装配 Jackson 3，这里直接使用 Jackson 2 实例序列化推送报文 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ScheduledExecutorService scheduler;

    public DashboardWebSocketHandler(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dashboard-ws-push");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::pushSafely, PUSH_INTERVAL_SECONDS,
                PUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (!dashboardService.isAdmin(userId)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("仅教务可访问数据大屏"));
            return;
        }
        SESSIONS.add(session);
        send(session, buildPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session);
    }

    private void pushSafely() {
        pushNow();
    }

    /**
     * 立即向所有大屏连接推送一次。
     * 由 RabbitMQ 消费者在业务事件发生后调用，也可由定时任务触发。
     */
    public void pushNow() {
        if (SESSIONS.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = buildPayload();
        } catch (Exception e) {
            return;
        }
        for (WebSocketSession session : SESSIONS) {
            send(session, payload);
        }
    }

    private void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (Exception e) {
            SESSIONS.remove(session);
        }
    }

    private String buildPayload() throws Exception {
        return objectMapper.writeValueAsString(Result.success(dashboardService.realtime()));
    }
}
