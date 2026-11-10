package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户换角色入参。
 */
@Data
public class UserRoleAssignDTO {
    /** 用户id */
    @NotNull(message = "用户id不能为空")
    private Long userId;
    /** 角色id */
    @NotNull(message = "角色id不能为空")
    private Long roleId;
}
