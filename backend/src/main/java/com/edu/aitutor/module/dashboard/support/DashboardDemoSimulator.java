package com.edu.aitutor.module.dashboard.support;

import com.edu.aitutor.module.dashboard.mapper.DashboardMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据大屏演示数据模拟器。
 * <p>
 * 按固定节拍向明细表写入少量记录，让大屏的指标卡和实时动态流持续变化，
 * 从而演示 WebSocket 实时推送效果。仅在配置 {@code dashboard.demo-simulator=true} 时启用，
 * 生产环境必须关闭。
 * <p>
 * 模拟数据可通过以下语句清理：
 * <pre>
 * delete from ai_usage_log where model_name = 'demo-simulator';
 * delete from chat_session where session_no like 'DEMO-SIM-%';
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "dashboard.demo-simulator", havingValue = "true")
public class DashboardDemoSimulator {

    private static final Logger log = LoggerFactory.getLogger(DashboardDemoSimulator.class);

    /** 写入节拍（秒）：大屏推送节拍为 3 秒，这里略慢一点保证每次推送都有变化 */
    private static final long TICK_SECONDS = 5L;

    /** 每 N 个节拍产生一次答疑会话 */
    private static final long SESSION_EVERY_TICKS = 3L;

    private final DashboardMapper dashboardMapper;
    private final AtomicLong tick = new AtomicLong();
    private ScheduledExecutorService scheduler;

    public DashboardDemoSimulator(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @PostConstruct
    public void start() {
        try {
            dashboardMapper.ensureTodayStudyStat();
        } catch (Exception e) {
            log.warn("演示模拟器初始化当天统计失败：{}", e.getMessage());
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dashboard-demo-simulator");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::tickSafely, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        log.info("数据大屏演示模拟器已启动，每 {} 秒产生一次模拟数据", TICK_SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void tickSafely() {
        try {
            dashboardMapper.insertSimulatedAiLog();
            dashboardMapper.increaseTodayStudyStat();
            long current = tick.incrementAndGet();
            if (current % SESSION_EVERY_TICKS == 0) {
                dashboardMapper.insertSimulatedSession();
            }
        } catch (Exception e) {
            log.warn("演示模拟器写入失败：{}", e.getMessage());
        }
    }
}
