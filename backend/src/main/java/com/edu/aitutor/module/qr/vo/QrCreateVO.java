package com.edu.aitutor.module.qr.vo;

import lombok.Data;

/**
 * 生成登录二维码响应（接口文档 3.6）。
 */
@Data
public class QrCreateVO {

    private String qrToken;

    private Integer expireSeconds;
}
