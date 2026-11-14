package com.edu.aitutor.module.auth.mapper;

import com.edu.aitutor.module.auth.entity.LoginWhitelist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LoginWhitelistMapper {

    /**
     * 全部启用的白名单
     */
    @Select("select id, account, remark, enabled, create_time, update_time " +
            "from login_whitelist where enabled = 1")
    List<LoginWhitelist> selectAllEnabled();

    /**
     * 启用白名单的条数，用于判断表是否维护了数据
     */
    @Select("select count(*) from login_whitelist where enabled = 1")
    long countEnabled();
}
