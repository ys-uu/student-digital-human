package com.edu.aitutor.module.teacher.controller;

import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.teacher.service.TeacherWorkbenchService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教师工作台接口。教师与教务可访问，便于演示时用教务账号验证。
 */
@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController {

    @Resource
    private TeacherWorkbenchService teacherWorkbenchService;

    /**
     * 一键布置随堂练习，成功后教务大屏会在下一次推送中呈现。
     * 按权限码鉴权：教师需持有 homework:create，教务靠通配码 *:* 自动放行。
     */
    @PreAuthorize("@ss.hasPerm('homework:create')")
    @PostMapping("/homework/quick")
    public Result<Map<String, Object>> quickHomework() {
        return Result.success(teacherWorkbenchService.publishQuickHomework());
    }
}
