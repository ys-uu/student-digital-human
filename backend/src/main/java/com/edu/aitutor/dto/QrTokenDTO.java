package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 二维码凭证请求（确认 / 取消 / 轮询的 body 形式）。
 */
@Data
public class QrTokenDTO {

    @NotBlank(message = "二维码凭证不能为空")
    private String qrToken;
}
