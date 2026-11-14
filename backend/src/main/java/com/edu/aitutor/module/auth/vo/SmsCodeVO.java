package com.edu.aitutor.module.auth.vo;

import lombok.Data;

/**
 * 发送短信验证码的响应。
 */
@Data
public class SmsCodeVO {

    private String phone;

    /** 验证码有效期（秒） */
    private Long expireSeconds;

    /** 提示文案 */
    private String tip;
}
