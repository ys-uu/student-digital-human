package com.edu.aitutor.module.rbac.service;

import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.module.rbac.dto.RolePermissionDTO;
import com.edu.aitutor.module.rbac.dto.RoleSaveDTO;
import com.edu.aitutor.module.rbac.dto.UserRoleAssignDTO;
import com.edu.aitutor.module.rbac.entity.SysRole;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.rbac.vo.PermissionNodeVO;

import java.util.List;

/**
 * 角色授权与鉴权管理服务：角色维护、权限树、授权保存与用户角色分配。
 */
public interface RbacService {

    /** 角色列表 */
    List<SysRole> listRoles();

    /** 角色详情 */
    SysRole getRole(Long id);

    /** 新增/编辑角色（按 id 是否为空区分） */
    void saveRole(RoleSaveDTO dto);

    /** 删除角色（逻辑删除） */
    void removeRole(Long id);

    /** 权限树（菜单为父节点、操作为子节点） */
    List<PermissionNodeVO> listPermissionTree();

    /** 角色已绑定的权限 id，授权页回显 */
    List<Long> listRolePermissionIds(Long roleId);

    /** 保存角色授权（先删后插并清缓存） */
    void assignRolePermissions(Long roleId, RolePermissionDTO dto);

    /** 用户分页列表 */
    PageResult<User> pageUsers(String keyword, int page, int size);

    /** 用户换角色 */
    void assignUserRole(UserRoleAssignDTO dto);
}
