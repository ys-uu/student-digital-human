package com.edu.aitutor.mapper;

import com.edu.aitutor.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    String COLUMNS = "id, user_no, password_hash, real_name, role, gender, phone, phone_verified, " +
            "email, avatar_url, status, last_login_at, last_login_ip, create_time, update_time, is_deleted";

    /**
     * 根据账号查询
     */
    @Select("select " + COLUMNS + " from sys_user where user_no = #{userNo} and is_deleted = 0")
    User selectByUserNo(@Param("userNo") String userNo);

    /**
     * 根据主键查询
     */
    @Select("select " + COLUMNS + " from sys_user where id = #{id} and is_deleted = 0")
    User selectById(@Param("id") Long id);

    /**
     * 根据手机号查询（验证码登录反查账号）
     */
    @Select("select " + COLUMNS + " from sys_user where phone = #{phone} and is_deleted = 0")
    User selectByPhone(@Param("phone") String phone);

    /**
     * 新增用户（注册 / 短信自动注册），回填主键
     */
    @Insert("insert into sys_user (user_no, password_hash, real_name, role, gender, phone, phone_verified, " +
            "email, avatar_url, status) values (#{userNo}, #{passwordHash}, #{realName}, #{role}, #{gender}, " +
            "#{phone}, #{phoneVerified}, #{email}, #{avatarUrl}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 更新登录时间与登录 IP
     */
    @Update("update sys_user set last_login_at = #{lastLoginAt}, last_login_ip = #{lastLoginIp} where id = #{id}")
    int updateLoginInfo(User user);

    /**
     * 模拟手机端候选账号（dev 环境 mock-users）
     */
    @Select("select id, user_no, real_name, role from sys_user where status = 1 and is_deleted = 0 order by id")
    List<User> selectMockUsers();

    /**
     * 更新个人资料 / 密码
     */
    @Update("update sys_user set real_name = #{realName}, avatar_url = #{avatarUrl}, phone = #{phone}, " +
            "email = #{email}, password_hash = #{passwordHash} where id = #{id}")
    int updateProfile(User user);
}
