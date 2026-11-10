package com.edu.aitutor.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转成 Result，避免在 Controller 里手写错误分支。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
