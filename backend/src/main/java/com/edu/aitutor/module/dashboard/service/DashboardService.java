package com.edu.aitutor.module.dashboard.service;

import com.edu.aitutor.module.dashboard.vo.DashboardOverviewVO;

/**
 * 教务数据大屏数据服务。
 */
public interface DashboardService {

    /**
     * 大屏全量快照
     *
     * @param days     统计天数（含当天）
     * @param courseId 课程筛选，为空表示全部课程
     */
    DashboardOverviewVO overview(int days, Long courseId);

    /**
     * 实时增量：仅核心指标与最近动态
     */
    DashboardOverviewVO realtime();

    /**
     * 当前用户是否教务角色
     */
    boolean isAdmin(Long userId);
}
