package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求（接口文档 3.2）。
 */
@Data
public class RefreshDTO {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
