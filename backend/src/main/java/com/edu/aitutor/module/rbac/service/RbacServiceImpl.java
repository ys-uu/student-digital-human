package com.edu.aitutor.module.rbac.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.module.rbac.dto.RolePermissionDTO;
import com.edu.aitutor.module.rbac.dto.RoleSaveDTO;
import com.edu.aitutor.module.rbac.dto.UserRoleAssignDTO;
import com.edu.aitutor.module.rbac.entity.SysPermission;
import com.edu.aitutor.module.rbac.entity.SysRole;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.rbac.mapper.PermissionMapper;
import com.edu.aitutor.module.rbac.mapper.RoleMapper;
import com.edu.aitutor.module.user.mapper.UserMapper;
import com.edu.aitutor.framework.security.PermissionService;
import com.edu.aitutor.framework.security.RbacUserDetailsService;
import com.edu.aitutor.module.rbac.vo.PermissionNodeVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色授权管理服务实现：内置角色与通配保护、事务内先删后插、授权后按用户清除权限缓存。
 */
@Service
public class RbacServiceImpl implements RbacService {

    /** 内置角色 id，与 rbac.sql 初始化的学生/教师/教务对齐，禁止删除 */
    private static final Set<Long> BUILTIN_ROLE_IDS = Set.of(1L, 2L, 3L);

    @Resource
    private RoleMapper roleMapper;
    @Resource
    private PermissionMapper permissionMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RbacUserDetailsService rbacUserDetailsService;

    @Override
    public List<SysRole> listRoles() {
        return roleMapper.selectAll();
    }

    @Override
    public SysRole getRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    @Override
    public void saveRole(RoleSaveDTO dto) {
        SysRole role = new SysRole();
        role.setId(dto.getId());
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDataScope(dto.getDataScope());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());

        if (roleMapper.countByCode(role.getRoleCode(), role.getId()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        if (role.getId() == null) {
            roleMapper.insert(role);
            return;
        }
        SysRole existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        // 内置角色不允许修改编码，避免破坏 ROLE_ 前缀与既有鉴权语义
        if (BUILTIN_ROLE_IDS.contains(role.getId()) && !existing.getRoleCode().equals(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内置角色编码不允许修改");
        }
        roleMapper.update(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (BUILTIN_ROLE_IDS.contains(id)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内置角色不允许删除");
        }
        if (roleMapper.countUsersByRoleId(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该角色下仍有用户，无法删除");
        }
        // 无用户绑定：清理其权限关联后逻辑删除
        roleMapper.deletePermissionsByRoleId(id);
        roleMapper.logicDelete(id);
    }

    @Override
    public List<PermissionNodeVO> listPermissionTree() {
        List<SysPermission> all = permissionMapper.selectAllEnabled();
        Map<Long, PermissionNodeVO> nodeMap = new LinkedHashMap<>();
        for (SysPermission permission : all) {
            PermissionNodeVO node = new PermissionNodeVO();
            node.setId(permission.getId());
            node.setParentId(permission.getParentId());
            node.setPermCode(permission.getPermCode());
            node.setPermName(permission.getPermName());
            node.setPermType(permission.getPermType());
            nodeMap.put(permission.getId(), node);
        }
        List<PermissionNodeVO> roots = new ArrayList<>();
        for (PermissionNodeVO node : nodeMap.values()) {
            PermissionNodeVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public List<Long> listRolePermissionIds(Long roleId) {
        getRole(roleId);
        return roleMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolePermissions(Long roleId, RolePermissionDTO dto) {
        getRole(roleId);
        List<Long> newIds = dto.getPermissionIds() == null
                ? new ArrayList<>()
                : dto.getPermissionIds().stream().distinct().collect(Collectors.toList());

        // 通配保护：已持有 *:* 的角色不允许移除通配，防止超级角色被意外降级
        Long wildcardId = findWildcardId();
        List<Long> currentIds = roleMapper.selectPermissionIdsByRoleId(roleId);
        if (wildcardId != null && currentIds.contains(wildcardId) && !newIds.contains(wildcardId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许移除该角色的通配权限 *:*");
        }

        roleMapper.deletePermissionsByRoleId(roleId);
        if (!newIds.isEmpty()) {
            roleMapper.batchInsertRolePermissions(roleId, newIds);
        }
        // 该角色下所有用户的权限缓存失效，下次请求重新查库并回写
        for (Long userId : roleMapper.selectUserIdsByRoleId(roleId)) {
            rbacUserDetailsService.clearPermCache(userId);
        }
    }

    @Override
    public PageResult<User> pageUsers(String keyword, int page, int size) {
        int pageNum = Math.max(page, 1);
        int pageSize = size <= 0 ? 10 : Math.min(size, 100);
        int offset = (pageNum - 1) * pageSize;
        List<User> list = userMapper.selectUserPage(keyword, offset, pageSize);
        long total = userMapper.countUsers(keyword);
        return new PageResult<>(total, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRole(UserRoleAssignDTO dto) {
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        SysRole role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        // 一人一角色：先删旧关联再插新关联，同步 sys_user.role 冗余字段，最后清缓存
        roleMapper.deleteUserRoleByUserId(dto.getUserId());
        roleMapper.insertUserRole(dto.getUserId(), dto.getRoleId());
        userMapper.updateRole(dto.getUserId(), dto.getRoleId());
        rbacUserDetailsService.clearPermCache(dto.getUserId());
    }

    /**
     * 查询通配权限 *:* 的 id，未配置时返回 null
     */
    private Long findWildcardId() {
        return permissionMapper.selectAllEnabled().stream()
                .filter(p -> PermissionService.WILDCARD.equals(p.getPermCode()))
                .map(SysPermission::getId)
                .findFirst()
                .orElse(null);
    }
}
