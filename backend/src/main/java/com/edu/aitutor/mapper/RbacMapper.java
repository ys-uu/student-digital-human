package com.edu.aitutor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RBAC 权限查询：角色编码与权限码加载，供 Security 过滤器与方法级鉴权使用。
 */
@Mapper
public interface RbacMapper {

    /**
     * 查询用户启用状态的角色编码（当前一人一角色，实际返回 0 或 1 条）
     */
    @Select("select r.role_code from sys_user_role ur " +
            "join sys_role r on r.id = ur.role_id " +
            "where ur.user_id = #{userId} and r.status = 1 and r.is_deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户经角色-权限关联得到的全部权限码（去重）
     */
    @Select("select distinct p.perm_code from sys_user_role ur " +
            "join sys_role_permission rp on rp.role_id = ur.role_id " +
            "join sys_permission p on p.id = rp.permission_id " +
            "where ur.user_id = #{userId} and p.status = 1 and p.is_deleted = 0")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);
}
