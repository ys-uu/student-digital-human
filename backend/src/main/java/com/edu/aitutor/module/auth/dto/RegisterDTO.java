package com.edu.aitutor.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号注册请求（文档未定义，按项目需要新增 POST /api/v1/auth/register）。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Size(max = 32, message = "账号长度不能超过32位")
    private String userNo;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需为8-20位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String realName;

    private Integer gender;

    /** 角色：1 学生，2 教师；注册接口默认学生，教师由后台创建 */
    private Integer role;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
