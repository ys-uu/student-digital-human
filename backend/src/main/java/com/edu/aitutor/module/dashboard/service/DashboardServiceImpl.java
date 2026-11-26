package com.edu.aitutor.module.dashboard.service;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.dashboard.mapper.DashboardMapper;
import com.edu.aitutor.module.dashboard.repository.DashboardCacheRedisRepository;
import com.edu.aitutor.module.dashboard.vo.DashboardAnalyticsSnapshot;
import com.edu.aitutor.module.dashboard.vo.DashboardOverviewVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数据大屏聚合实现：统一指标口径，供 HTTP 快照与 WebSocket 增量共用。
 * <p>
 * 图表类聚合结果写入 Redis 缓存（默认 60 秒 + 随机抖动），
 * 今日指标与实时动态保持直查，业务事件由 RabbitMQ 触发缓存失效。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] SCENE_NAMES = {"自由答疑", "拍照搜题", "作业辅导", "课堂追问", "备考"};

    /** 缓存基础 TTL（秒） */
    private static final long CACHE_TTL_SECONDS = 60L;

    /** TTL 随机抖动上限（秒），避免大量 key 同时过期 */
    private static final long CACHE_TTL_JITTER_SECONDS = 15L;

    @Resource
    private DashboardMapper dashboardMapper;

    @Resource
    private DashboardCacheRedisRepository dashboardCacheRedisRepository;

    @Override
    public DashboardOverviewVO overview(int days, Long courseId) {
        LocalDate start = LocalDate.now().minusDays(Math.max(days, 1) - 1L);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        // 指标卡固定为“今日”口径，与 WebSocket 实时推送保持一致，不进入缓存
        vo.setSummary(buildSummary(LocalDate.now(), courseId));
        // 实时动态同样直查，保证事件即时可见
        vo.setLiveEvents(dashboardMapper.selectLiveEvents());

        // 图表聚合结果走缓存，降低重复聚合查询压力
        String cacheKey = RedisKeys.DASHBOARD_ANALYTICS_PREFIX
                + days + ":" + (courseId == null ? "all" : courseId);
        DashboardAnalyticsSnapshot snapshot = dashboardCacheRedisRepository.findAnalytics(cacheKey);
        if (snapshot == null) {
            snapshot = buildAnalytics(start, courseId);
            long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(CACHE_TTL_JITTER_SECONDS);
            dashboardCacheRedisRepository.saveAnalytics(
                    cacheKey, snapshot, Duration.ofSeconds(ttl));
        }
        applyAnalytics(vo, snapshot);

        vo.setGeneratedAt(LocalDateTime.now().format(TIME_FORMAT));
        return vo;
    }

    @Override
    public DashboardOverviewVO realtime() {
        LocalDate start = LocalDate.now();
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setSummary(buildSummary(start, null));
        vo.setLiveEvents(dashboardMapper.selectLiveEvents());
        vo.setGeneratedAt(LocalDateTime.now().format(TIME_FORMAT));
        return vo;
    }

    @Override
    public boolean isAdmin(Long userId) {
        return userId != null && dashboardMapper.countAdminRole(userId) > 0;
    }

    private DashboardAnalyticsSnapshot buildAnalytics(LocalDate start, Long courseId) {
        DashboardAnalyticsSnapshot snapshot = new DashboardAnalyticsSnapshot();
        snapshot.setLearningTrend(dashboardMapper.selectLearningTrend(start, courseId));
        snapshot.setMasteryDistribution(dashboardMapper.selectMasteryDistribution(courseId));
        snapshot.setCourseRanking(dashboardMapper.selectCourseRanking(start));
        snapshot.setWeakPoints(dashboardMapper.selectWeakPoints(courseId));
        snapshot.setClassCompare(dashboardMapper.selectClassCompare(start));
        snapshot.setWarnings(dashboardMapper.selectWarnings());
        snapshot.setAiUsage(buildAiUsage(start));
        return snapshot;
    }

    private void applyAnalytics(DashboardOverviewVO vo, DashboardAnalyticsSnapshot snapshot) {
        vo.setLearningTrend(snapshot.getLearningTrend());
        vo.setMasteryDistribution(snapshot.getMasteryDistribution());
        vo.setCourseRanking(snapshot.getCourseRanking());
        vo.setWeakPoints(snapshot.getWeakPoints());
        vo.setClassCompare(snapshot.getClassCompare());
        vo.setWarnings(snapshot.getWarnings());
        vo.setAiUsage(snapshot.getAiUsage());
    }

    private DashboardOverviewVO.Summary buildSummary(LocalDate start, Long courseId) {
        Map<String, Object> study = dashboardMapper.selectStudySummary(start, courseId);
        Map<String, Object> ai = dashboardMapper.selectAiSummary(start);

        DashboardOverviewVO.Summary summary = new DashboardOverviewVO.Summary();
        summary.setActiveStudents(toLong(study.get("active_students")));
        summary.setStudyMinutes(toLong(study.get("study_minutes")));

        long exercises = toLong(study.get("exercise_count"));
        long correct = toLong(study.get("correct_count"));
        summary.setCorrectRate(exercises == 0 ? 0 : round(correct * 100.0 / exercises));

        summary.setQaCount(dashboardMapper.countQaSessions(start, courseId));
        summary.setAverageMastery(round(dashboardMapper.selectAverageMastery(courseId)));
        summary.setAiCallCount(toLong(ai.get("call_count")));
        summary.setPendingHomework(dashboardMapper.countPendingHomework());
        summary.setTodayHomework(dashboardMapper.countTodayHomework(start));
        return summary;
    }

    private DashboardOverviewVO.AiUsage buildAiUsage(LocalDate start) {
        Map<String, Object> ai = dashboardMapper.selectAiSummary(start);

        DashboardOverviewVO.AiUsage usage = new DashboardOverviewVO.AiUsage();
        usage.setTotalTokens(toLong(ai.get("total_tokens")));
        usage.setAvgDurationMs(toLong(ai.get("avg_duration_ms")));
        usage.setSuccessRate(round(toDouble(ai.get("success_rate"))));

        List<DashboardOverviewVO.NameValue> scenes = new ArrayList<>();
        for (DashboardOverviewVO.NameValue item : dashboardMapper.selectAiSceneDistribution(start)) {
            DashboardOverviewVO.NameValue mapped = new DashboardOverviewVO.NameValue();
            mapped.setName(sceneName(item.getName()));
            mapped.setValue(item.getValue());
            scenes.add(mapped);
        }
        usage.setSceneDistribution(scenes);
        return usage;
    }

    private String sceneName(String raw) {
        try {
            int index = Integer.parseInt(raw) - 1;
            return index >= 0 && index < SCENE_NAMES.length ? SCENE_NAMES[index] : "其他";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
