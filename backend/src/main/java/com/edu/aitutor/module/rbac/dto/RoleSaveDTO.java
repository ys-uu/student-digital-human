package com.edu.aitutor.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 角色新增/编辑入参。
 */
@Data
public class RoleSaveDTO {
    /** 角色id，编辑时必填，新增时为空 */
    private Long id;
    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    /** 数据范围 */
    @NotNull(message = "数据范围不能为空")
    private Integer dataScope;
    /** 状态 */
    @NotNull(message = "状态不能为空")
    private Integer status;
    /** 备注 */
    private String remark;
}
