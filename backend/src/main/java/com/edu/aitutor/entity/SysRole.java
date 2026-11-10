package com.edu.aitutor.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色实体，对应 sys_role 表。
 */
@Data
public class SysRole {
    /** 角色id */
    private Long id;
    /** 角色编码，加载时加 ROLE_ 前缀 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 数据范围（预留）：1 全部，2 本班，3 本人 */
    private Integer dataScope;
    /** 状态：0 停用，1 启用 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除标记 */
    private Integer isDeleted;
}
