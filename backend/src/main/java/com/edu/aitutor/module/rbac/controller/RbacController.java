package com.edu.aitutor.module.rbac.controller;

import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.rbac.dto.RolePermissionDTO;
import com.edu.aitutor.module.rbac.dto.RoleSaveDTO;
import com.edu.aitutor.module.rbac.dto.UserRoleAssignDTO;
import com.edu.aitutor.module.rbac.entity.SysRole;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.rbac.service.RbacService;
import com.edu.aitutor.module.rbac.vo.PermissionNodeVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色授权管理接口：角色维护、权限树、角色授权与用户角色分配。
 */
@RestController
@RequestMapping("/api/v1/rbac")
public class RbacController {

    @Resource
    private RbacService rbacService;

    /** 角色列表 */
    @PreAuthorize("@ss.hasPerm('system:role:list')")
    @GetMapping("/roles")
    public Result<List<SysRole>> listRoles() {
        return Result.success(rbacService.listRoles());
    }

    /** 角色详情 */
    @PreAuthorize("@ss.hasPerm('system:role:list')")
    @GetMapping("/roles/{id}")
    public Result<SysRole> getRole(@PathVariable Long id) {
        return Result.success(rbacService.getRole(id));
    }

    /** 新增角色 */
    @PreAuthorize("@ss.hasPerm('system:role:save')")
    @PostMapping("/roles")
    public Result<Void> createRole(@Valid @RequestBody RoleSaveDTO dto) {
        dto.setId(null);
        rbacService.saveRole(dto);
        return Result.success(null);
    }

    /** 编辑角色 */
    @PreAuthorize("@ss.hasPerm('system:role:update')")
    @PutMapping("/roles/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        dto.setId(id);
        rbacService.saveRole(dto);
        return Result.success(null);
    }

    /** 删除角色 */
    @PreAuthorize("@ss.hasPerm('system:role:delete')")
    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        rbacService.removeRole(id);
        return Result.success(null);
    }

    /** 权限树 */
    @PreAuthorize("@ss.hasPerm('system:permission:tree')")
    @GetMapping("/permissions/tree")
    public Result<List<PermissionNodeVO>> permissionTree() {
        return Result.success(rbacService.listPermissionTree());
    }

    /** 角色已绑定权限 id（授权页回显） */
    @PreAuthorize("@ss.hasPerm('system:role:assign')")
    @GetMapping("/roles/{id}/permissions")
    public Result<List<Long>> rolePermissions(@PathVariable Long id) {
        return Result.success(rbacService.listRolePermissionIds(id));
    }

    /** 保存角色授权 */
    @PreAuthorize("@ss.hasPerm('system:role:assign')")
    @PutMapping("/roles/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                         @RequestBody RolePermissionDTO dto) {
        rbacService.assignRolePermissions(id, dto);
        return Result.success(null);
    }

    /** 用户分页列表 */
    @PreAuthorize("@ss.hasPerm('system:user:list')")
    @GetMapping("/users")
    public Result<PageResult<User>> pageUsers(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(rbacService.pageUsers(keyword, page, size));
    }

    /** 用户换角色 */
    @PreAuthorize("@ss.hasPerm('system:user:assign')")
    @PutMapping("/users/role")
    public Result<Void> assignUserRole(@Valid @RequestBody UserRoleAssignDTO dto) {
        rbacService.assignUserRole(dto);
        return Result.success(null);
    }
}
