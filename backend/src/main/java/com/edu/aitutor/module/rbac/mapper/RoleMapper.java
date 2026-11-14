package com.edu.aitutor.module.rbac.mapper;

import com.edu.aitutor.module.rbac.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 角色管理 Mapper：角色 CRUD、角色-权限关联、用户-角色关联，批量语句使用 script 动态 SQL。
 */
@Mapper
public interface RoleMapper {

    /**
     * 全部启用角色（角色数量少，管理页直接全量展示）
     */
    @Select("select id, role_code, role_name, data_scope, status, remark, create_time, update_time " +
            "from sys_role where is_deleted = 0 order by id")
    List<SysRole> selectAll();

    /**
     * 按主键查询角色
     */
    @Select("select id, role_code, role_name, data_scope, status, remark, create_time, update_time " +
            "from sys_role where id = #{id} and is_deleted = 0")
    SysRole selectById(@Param("id") Long id);

    /**
     * 按编码统计数量，excludeId 为空统计新增、否则排除自身（编辑场景）
     */
    @Select("select count(*) from sys_role where role_code = #{roleCode} and is_deleted = 0 " +
            "and (#{excludeId} is null or id <> #{excludeId})")
    long countByCode(@Param("roleCode") String roleCode, @Param("excludeId") Long excludeId);

    /**
     * 新增角色，回填主键
     */
    @Insert("insert into sys_role (role_code, role_name, data_scope, status, remark) " +
            "values (#{roleCode}, #{roleName}, #{dataScope}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysRole role);

    /**
     * 编辑角色
     */
    @Update("update sys_role set role_code = #{roleCode}, role_name = #{roleName}, data_scope = #{dataScope}, " +
            "status = #{status}, remark = #{remark} where id = #{id} and is_deleted = 0")
    int update(SysRole role);

    /**
     * 逻辑删除角色
     */
    @Update("update sys_role set is_deleted = 1 where id = #{id}")
    int logicDelete(@Param("id") Long id);

    /**
     * 统计角色下绑定的用户数量，删除前校验
     */
    @Select("select count(*) from sys_user_role where role_id = #{roleId}")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除角色的全部权限关联（授权保存前先清空）
     */
    @Delete("delete from sys_role_permission where role_id = #{roleId}")
    int deletePermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已绑定的权限 id，授权页回显勾选
     */
    @Select("select permission_id from sys_role_permission where role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色-权限关联
     */
    @Insert("<script>" +
            "insert into sys_role_permission (role_id, permission_id) values " +
            "<foreach collection='permissionIds' item='permId' separator=','>" +
            "(#{roleId}, #{permId})" +
            "</foreach>" +
            "</script>")
    int batchInsertRolePermissions(@Param("roleId") Long roleId,
                                   @Param("permissionIds") List<Long> permissionIds);

    /**
     * 查询角色下全部用户 id，授权变更后逐个清除权限缓存
     */
    @Select("select user_id from sys_user_role where role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除用户的角色关联（换角色前先删旧关联）
     */
    @Delete("delete from sys_user_role where user_id = #{userId}")
    int deleteUserRoleByUserId(@Param("userId") Long userId);

    /**
     * 插入用户-角色关联
     */
    @Insert("insert into sys_user_role (user_id, role_id) values (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
