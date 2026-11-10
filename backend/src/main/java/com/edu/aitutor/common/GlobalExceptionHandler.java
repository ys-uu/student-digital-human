package com.edu.aitutor.common;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：保证所有接口响应结构统一为 Result。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按携带的错误码返回 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 方法级鉴权拒绝：@PreAuthorize 抛出，统一返回 1004 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletResponse response) {
        log.warn("权限不足: {}", e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return Result.error(ErrorCode.NO_PERMISSION, "权限不足，禁止访问");
    }

    /** 认证异常兜底：凭证失效等，统一返回 1002 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException e, HttpServletResponse response) {
        log.warn("认证失败: {}", e.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return Result.error(ErrorCode.TOKEN_INVALID, "未登录或登录已过期，请重新登录");
    }

    /** @RequestBody 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = firstError(e);
        return Result.error(ErrorCode.PARAM_ERROR, msg);
    }

    /** 表单 / 参数校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.error(ErrorCode.PARAM_ERROR, firstError(e));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.error(ErrorCode.PARAM_ERROR, e.getMessage());
    }

    /** 兜底：未预期异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }

    private String firstError(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
    }
}
