package com.edu.aitutor.security;

import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.Result;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 已登录但权限不足：HTTP 403 并返回统一 Result（业务码 1004）。
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.info("权限不足 uri={} reason={}", request.getRequestURI(), accessDeniedException.getMessage());
        Result<Void> body = Result.error(ErrorCode.NO_PERMISSION, "权限不足，禁止访问");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
