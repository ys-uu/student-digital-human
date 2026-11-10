package com.edu.aitutor.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 扫码登录会话，以 JSON 存 Redis（TTL 120s），不落库。
 * 状态机：0 待扫码，1 已扫码待确认，2 已登录，3 已过期，4 已取消，5 已拒绝。
 */
@Data
public class QrSession implements Serializable {

    private String qrToken;

    private Integer status;

    /** 扫码账号（user_no），scan 之后写入 */
    private String account;

    private Long userId;

    private String username;

    private Integer role;

    /** 确认登录后签发的 JWT，status=2 时写入 */
    private String token;

    private LocalDateTime createTime;
}
