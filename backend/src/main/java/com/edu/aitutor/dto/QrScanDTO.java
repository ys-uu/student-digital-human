package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟扫码请求（接口文档 3.8）。
 */
@Data
public class QrScanDTO {

    @NotBlank(message = "二维码凭证不能为空")
    private String qrToken;

    @NotBlank(message = "扫码账号不能为空")
    private String account;
}
