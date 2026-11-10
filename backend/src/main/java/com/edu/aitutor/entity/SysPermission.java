package com.edu.aitutor.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限实体，对应 sys_permission 表。
 */
@Data
public class SysPermission {
    /** 权限id */
    private Long id;
    /** 父权限id，0 为顶级 */
    private Long parentId;
    /** 权限码，资源:动作；*:* 为通配 */
    private String permCode;
    /** 权限名称 */
    private String permName;
    /** 权限类型：1 菜单，2 操作 */
    private Integer permType;
    /** 前端路由路径（预留） */
    private String path;
    /** 前端组件路径（预留） */
    private String component;
    /** 菜单图标（预留） */
    private String icon;
    /** 状态：0 停用，1 启用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除标记 */
    private Integer isDeleted;
}
