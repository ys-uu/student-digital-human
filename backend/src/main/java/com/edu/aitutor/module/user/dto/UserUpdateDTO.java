package com.edu.aitutor.module.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人信息 / 修改密码（接口文档 3.5）。
 */
@Data
public class UserUpdateDTO {

    @Size(max = 50, message = "姓名长度不能超过50")
    private String realName;

    private String avatarUrl;

    private String phone;

    private String email;

    private String oldPassword;

    @Size(min = 8, max = 20, message = "新密码长度需为8-20位")
    private String newPassword;
}
