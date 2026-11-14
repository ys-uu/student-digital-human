package com.edu.aitutor.module.qr.vo;

import lombok.Data;

/**
 * 二维码轮询响应（接口文档 3.7）。
 */
@Data
public class QrStatusVO {

    private Integer status;

    private String statusDesc;

    /** status=2 时携带 */
    private String token;

    private String username;

    private Long id;

    private Integer role;
}
