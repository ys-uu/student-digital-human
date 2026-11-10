package com.edu.aitutor.service;

import com.edu.aitutor.dto.PasswordLoginDTO;
import com.edu.aitutor.dto.RegisterDTO;
import com.edu.aitutor.dto.SmsLoginDTO;
import com.edu.aitutor.dto.UserUpdateDTO;
import com.edu.aitutor.vo.TokenVO;
import com.edu.aitutor.vo.UserInfoVO;

/**
 * 认证与用户服务：账密登录、注册、短信登录、刷新、当前用户、退出、资料更新。
 */
public interface AuthService {

    /** 账号密码登录（文档 3.1） */
    TokenVO loginByPassword(PasswordLoginDTO dto, String ip, String userAgent);

    /** 账号注册（新增 /auth/register），注册后自动登录 */
    TokenVO register(RegisterDTO dto, String ip, String userAgent);

    /** 手机号验证码登录（文档 3.1 loginType=3 的独立实现），手机号不存在时自动注册 */
    TokenVO loginBySms(SmsLoginDTO dto, String ip, String userAgent);

    /** 刷新令牌（文档 3.2） */
    TokenVO refresh(String refreshToken);

    /** 当前登录用户信息（文档 3.4） */
    UserInfoVO me(Long userId);

    /** 退出登录（文档 3.3），token 加入 Redis 黑名单 */
    void logout(String token);

    /** 更新个人信息 / 改密（文档 3.5） */
    UserInfoVO updateProfile(Long userId, UserUpdateDTO dto);
}
