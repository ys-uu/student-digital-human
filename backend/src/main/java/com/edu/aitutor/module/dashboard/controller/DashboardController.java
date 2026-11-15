package com.edu.aitutor.module.dashboard.controller;

import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.dashboard.service.DashboardService;
import com.edu.aitutor.module.dashboard.vo.DashboardOverviewVO;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教务数据大屏接口。仅教务角色可访问。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    /**
     * 大屏首屏快照
     *
     * @param days     统计天数，默认 7，范围 1～90
     * @param courseId 课程筛选，为空表示全部课程
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview(@RequestParam(defaultValue = "7") int days,
                                                @RequestParam(required = false) Long courseId) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        return Result.success(dashboardService.overview(safeDays, courseId));
    }
}
