package com.edu.aitutor.module.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计日志（对应 auth_login_log 表），追加型，不做修改。
 */
@Data
public class AuthLoginLog {

    private Long id;

    /** 登录用户ID，失败时可为空 */
    private Long userId;

    /** 本次提交的账号 / 手机号 */
    private String account;

    /** 登录方式：1 学号密码，2 工号密码，3 手机号验证码，4 扫码登录 */
    private Integer loginType;

    /** 登录结果：0 失败，1 成功 */
    private Integer loginResult;

    /** 失败原因 */
    private String failReason;

    /** 设备标识（X-Device-Id） */
    private String deviceId;

    /** 登录 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    private LocalDateTime createTime;
}
