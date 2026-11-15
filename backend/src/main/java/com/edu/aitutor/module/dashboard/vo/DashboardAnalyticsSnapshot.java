package com.edu.aitutor.module.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * 大屏图表聚合结果快照，用于 Redis 缓存。
 * 只包含重查询的图表数据；今日指标与实时动态始终实时查询，不进入缓存。
 */
@Data
public class DashboardAnalyticsSnapshot {

    private List<DashboardOverviewVO.TrendPoint> learningTrend;
    private List<DashboardOverviewVO.NameValue> masteryDistribution;
    private List<DashboardOverviewVO.NameValue> courseRanking;
    private List<DashboardOverviewVO.NameValue> weakPoints;
    private List<DashboardOverviewVO.ClassCompare> classCompare;
    private List<DashboardOverviewVO.WarningItem> warnings;
    private DashboardOverviewVO.AiUsage aiUsage;
}
