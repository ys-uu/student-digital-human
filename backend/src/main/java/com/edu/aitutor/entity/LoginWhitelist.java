package com.edu.aitutor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫码登录白名单（对应 login_whitelist 表）。
 */
@Data
public class LoginWhitelist {

    private Long id;

    /** 白名单账号，对应 sys_user.user_no */
    private String account;

    /** 备注 */
    private String remark;

    /** 是否启用：0 停用，1 启用 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
