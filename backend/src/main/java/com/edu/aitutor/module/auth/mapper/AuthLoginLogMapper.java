package com.edu.aitutor.module.auth.mapper;

import com.edu.aitutor.module.auth.entity.AuthLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLoginLogMapper {

    /**
     * 写入登录审计日志（追加型）
     */
    @Insert("insert into auth_login_log (user_id, account, login_type, login_result, fail_reason, " +
            "device_id, ip, user_agent) values (#{userId}, #{account}, #{loginType}, #{loginResult}, " +
            "#{failReason}, #{deviceId}, #{ip}, #{userAgent})")
    int insert(AuthLoginLog log);
}
