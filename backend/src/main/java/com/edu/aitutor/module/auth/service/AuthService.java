package com.edu.aitutor.module.auth.service;

import com.edu.aitutor.module.auth.dto.PasswordLoginDTO;
import com.edu.aitutor.module.auth.dto.RegisterDTO;
import com.edu.aitutor.module.auth.dto.SmsLoginDTO;
import com.edu.aitutor.module.user.dto.UserUpdateDTO;
import com.edu.aitutor.module.auth.vo.TokenVO;
import com.edu.aitutor.module.user.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证与用户服务：账密登录、注册、短信登录、刷新、当前用户、退出、资料更新。
 */
public interface AuthService {

    /** 账号密码登录（文档 3.1），refresh token 经 HttpOnly cookie 下发 */
    TokenVO loginByPassword(PasswordLoginDTO dto, String ip, String userAgent, HttpServletResponse response);

    /** 账号注册（新增 /auth/register），注册后自动登录 */
    TokenVO register(RegisterDTO dto, String ip, String userAgent, HttpServletResponse response);

    /** 手机号验证码登录（文档 3.1 loginType=3 的独立实现），手机号不存在时自动注册 */
    TokenVO loginBySms(SmsLoginDTO dto, String ip, String userAgent, HttpServletResponse response);

    /** 刷新令牌（文档 3.2），refresh token 仅从 cookie 读取，本期不做旋转 */
    TokenVO refresh(HttpServletRequest request);

    /** 当前登录用户信息（文档 3.4） */
    UserInfoVO me(Long userId);

    /** 退出登录（文档 3.3）：access 加黑名单、refresh 白名单删除并清除 cookie */
    void logout(String token, HttpServletRequest request, HttpServletResponse response);

    /** 更新个人信息 / 改密（文档 3.5） */
    UserInfoVO updateProfile(Long userId, UserUpdateDTO dto);
}
