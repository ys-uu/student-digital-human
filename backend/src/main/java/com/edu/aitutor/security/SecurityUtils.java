package com.edu.aitutor.security;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：从 SecurityContext 解析当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID，未登录或身份无法识别时抛业务异常。
     */
    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "未登录或登录已过期，请重新登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID, "无法识别登录身份，请重新登录");
    }
}
