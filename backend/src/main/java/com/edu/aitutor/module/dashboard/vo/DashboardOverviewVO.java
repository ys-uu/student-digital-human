package com.edu.aitutor.module.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * 教务数据大屏聚合返回体，按展示区块分组，前端可局部刷新。
 */
@Data
public class DashboardOverviewVO {

    /** 核心指标 */
    private Summary summary;
    /** 学习行为趋势 */
    private List<TrendPoint> learningTrend;
    /** 知识掌握分布 */
    private List<NameValue> masteryDistribution;
    /** 课程学习排行 */
    private List<NameValue> courseRanking;
    /** 薄弱知识点排行 */
    private List<NameValue> weakPoints;
    /** 班级对比 */
    private List<ClassCompare> classCompare;
    /** 学情预警 */
    private List<WarningItem> warnings;
    /** AI 使用情况 */
    private AiUsage aiUsage;
    /** 实时动态 */
    private List<LiveEvent> liveEvents;
    /** 数据生成时间 */
    private String generatedAt;

    @Data
    public static class Summary {
        private long activeStudents;
        private long studyMinutes;
        private long qaCount;
        private double correctRate;
        private double averageMastery;
        private long aiCallCount;
        private long pendingHomework;
        /** 今日教师新布置的作业数 */
        private long todayHomework;
    }

    @Data
    public static class TrendPoint {
        private String date;
        private long studyMinutes;
        private long qaCount;
        private long exerciseCount;
    }

    @Data
    public static class NameValue {
        private String name;
        private double value;
    }

    @Data
    public static class ClassCompare {
        private String name;
        private long students;
        private long avgMinutes;
        private double avgMastery;
    }

    @Data
    public static class WarningItem {
        private String studentName;
        private String className;
        private String reason;
    }

    @Data
    public static class AiUsage {
        private long totalTokens;
        private long avgDurationMs;
        private double successRate;
        private List<NameValue> sceneDistribution;
    }

    @Data
    public static class LiveEvent {
        private String type;
        private String userName;
        private String content;
        private String time;
    }
}
