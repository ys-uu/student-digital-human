package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号密码登录请求（接口文档 3.1）。
 */
@Data
public class PasswordLoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotNull(message = "登录类型不能为空")
    private Integer loginType;

    private String deviceId;
}
