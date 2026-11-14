package com.edu.aitutor.module.auth.controller;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.auth.dto.PasswordLoginDTO;
import com.edu.aitutor.module.auth.dto.RefreshDTO;
import com.edu.aitutor.module.auth.dto.RegisterDTO;
import com.edu.aitutor.module.auth.dto.SmsCodeDTO;
import com.edu.aitutor.module.auth.dto.SmsLoginDTO;
import com.edu.aitutor.module.user.dto.UserUpdateDTO;
import com.edu.aitutor.module.auth.service.AuthService;
import com.edu.aitutor.module.auth.service.SmsService;
import com.edu.aitutor.framework.web.ClientIpUtil;
import com.edu.aitutor.module.auth.vo.SmsCodeVO;
import com.edu.aitutor.module.auth.vo.TokenVO;
import com.edu.aitutor.module.user.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证与用户接口（接口文档第 3 章）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @Resource
    private AuthService authService;
    @Resource
    private SmsService smsService;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /** 3.1 账号密码登录 */
    @PostMapping("/auth/login")
    public Result<TokenVO> login(@Valid @RequestBody PasswordLoginDTO dto, HttpServletRequest request) {
        return Result.success(authService.loginByPassword(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent")));
    }

    /** 注册（文档外新增），注册后自动登录 */
    @PostMapping("/auth/register")
    public Result<TokenVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return Result.success(authService.register(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent")));
    }

    /** 发送短信验证码（60s 频控） */
    @PostMapping("/auth/sms/code")
    public Result<SmsCodeVO> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        String limitKey = String.format(RedisKeys.SMS_LIMIT, dto.getPhone());
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(allowed)) {
            throw new BusinessException(ErrorCode.TOO_FREQUENT, "验证码发送过于频繁，请60秒后再试");
        }
        smsService.sendVerifyCode(dto.getPhone());
        SmsCodeVO vo = new SmsCodeVO();
        vo.setPhone(dto.getPhone());
        vo.setExpireSeconds(300L);
        vo.setTip("验证码已发送，5分钟内有效");
        return Result.success(vo);
    }

    /** 手机号验证码登录 */
    @PostMapping("/auth/sms/login")
    public Result<TokenVO> smsLogin(@Valid @RequestBody SmsLoginDTO dto, HttpServletRequest request) {
        return Result.success(authService.loginBySms(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent")));
    }

    /** 3.2 刷新令牌 */
    @PostMapping("/auth/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.success(authService.refresh(dto.getRefreshToken()));
    }

    /** 3.4 当前登录用户信息 */
    @GetMapping("/auth/me")
    public Result<UserInfoVO> me(HttpServletRequest request) {
        return Result.success(authService.me((Long) request.getAttribute("userId")));
    }

    /** 3.3 退出登录 */
    @PostMapping("/auth/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return Result.success(null);
    }

    /** 3.5 更新个人信息 / 改密 */
    @PatchMapping("/users/me")
    public Result<UserInfoVO> updateMe(@RequestBody UserUpdateDTO dto, HttpServletRequest request) {
        return Result.success(authService.updateProfile((Long) request.getAttribute("userId"), dto));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
