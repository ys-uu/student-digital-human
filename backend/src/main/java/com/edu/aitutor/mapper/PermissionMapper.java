package com.edu.aitutor.mapper;

import com.edu.aitutor.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限管理查询：供权限树构建与授权回显使用，不参与鉴权热路径。
 */
@Mapper
public interface PermissionMapper {

    /**
     * 查询全部启用权限（含通配与菜单/操作），按父级与 id 排序
     */
    @Select("select id, parent_id, perm_code, perm_name, perm_type, path, component, icon, status " +
            "from sys_permission where status = 1 and is_deleted = 0 order by parent_id, id")
    List<SysPermission> selectAllEnabled();
}
