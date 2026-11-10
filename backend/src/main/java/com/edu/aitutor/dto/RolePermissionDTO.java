package com.edu.aitutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 角色权限分配入参。
 */
@Data
public class RolePermissionDTO {
    /** 勾选的权限id集合（含菜单与操作），空列表表示清空该角色权限 */
    private List<Long> permissionIds;
}
