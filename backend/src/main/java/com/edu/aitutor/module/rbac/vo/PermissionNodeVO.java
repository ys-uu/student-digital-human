package com.edu.aitutor.module.rbac.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点：菜单为父节点，其下挂载操作。
 */
@Data
public class PermissionNodeVO {
    /** 权限id */
    private Long id;
    /** 父权限id */
    private Long parentId;
    /** 权限码 */
    private String permCode;
    /** 权限名称 */
    private String permName;
    /** 权限类型：1 菜单，2 操作 */
    private Integer permType;
    /** 子节点 */
    private List<PermissionNodeVO> children = new ArrayList<>();
}
