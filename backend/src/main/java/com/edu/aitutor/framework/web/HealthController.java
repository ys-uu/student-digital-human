package com.edu.aitutor.framework.web;

import com.edu.aitutor.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（接口文档 16.7），免鉴权。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "up");
        data.put("service", "ai-tutor");
        data.put("time", LocalDateTime.now().toString());
        return Result.success(data);
    }
}
