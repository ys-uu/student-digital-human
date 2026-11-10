package com.edu.aitutor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    /**
     * 主键id
     */
    private Long id;

    /**
     * 用户账号（对应数据库 user_no）
     */
    private String userNo;

    /**
     * 密码哈希（对应 password_hash，不要存明文密码）
     */
    private String passwordHash;

    /**
     * 真实姓名 real_name
     */
    private String realName;

    /**
     * 角色：tinyint 1普通用户，2管理员
     */
    private Integer role;

    /**
     * 性别：0未知，1男，2女
     */
    private Integer gender;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 手机号是否已通过验证码校验：0 否，1 是（对应 phone_verified）
     */
    private Integer phoneVerified;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像地址 avatar_url
     */
    private String avatarUrl;

    /**
     * 账号状态：1正常，0禁用
     */
    private Integer status;

    /**
     * 最后登录时间 last_login_at
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录IP last_login_ip
     */
    private String lastLoginIp;

    /**
     * 创建时间 create_time
     */
    private LocalDateTime createTime;

    /**
     * 更新时间 update_time
     */
    private LocalDateTime updateTime;

    /**
     * 删除标记 0未删，1已删
     */
    private Integer isDeleted;
}
