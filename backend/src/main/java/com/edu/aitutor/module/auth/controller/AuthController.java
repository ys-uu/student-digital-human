package com.edu.aitutor.module.auth.controller;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.auth.dto.PasswordLoginDTO;
import com.edu.aitutor.module.auth.dto.RegisterDTO;
import com.edu.aitutor.module.auth.dto.SmsCodeDTO;
import com.edu.aitutor.module.auth.dto.SmsLoginDTO;
import com.edu.aitutor.module.user.dto.UserUpdateDTO;
import com.edu.aitutor.module.auth.repository.SmsLimitRedisRepository;
import com.edu.aitutor.module.auth.service.AuthService;
import com.edu.aitutor.module.auth.service.SmsService;
import com.edu.aitutor.framework.web.ClientIpUtil;
import com.edu.aitutor.module.auth.vo.SmsCodeVO;
import com.edu.aitutor.module.auth.vo.TokenVO;
import com.edu.aitutor.module.user.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

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
    @Resource
    private SmsLimitRedisRepository smsLimitRedisRepository;

    /** 允许源白名单，用于刷新接口的 Origin 校验，与 CORS 配置同源 */
    @Value("${cors.allow-origins}")
    private String[] allowOrigins;

    /** 3.1 账号密码登录 */
    @PostMapping("/auth/login")
    public Result<TokenVO> login(@Valid @RequestBody PasswordLoginDTO dto,
                                 HttpServletRequest request, HttpServletResponse response) {
        return Result.success(authService.loginByPassword(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent"), response));
    }

    /** 注册（文档外新增），注册后自动登录 */
    @PostMapping("/auth/register")
    public Result<TokenVO> register(@Valid @RequestBody RegisterDTO dto,
                                    HttpServletRequest request, HttpServletResponse response) {
        return Result.success(authService.register(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent"), response));
    }

    /** 发送短信验证码（60s 频控） */
    @PostMapping("/auth/sms/code")
    public Result<SmsCodeVO> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        if (!smsLimitRedisRepository.tryAcquireSendLock(dto.getPhone(), Duration.ofSeconds(60))) {
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
    public Result<TokenVO> smsLogin(@Valid @RequestBody SmsLoginDTO dto,
                                    HttpServletRequest request, HttpServletResponse response) {
        return Result.success(authService.loginBySms(
                dto, ClientIpUtil.resolve(request), request.getHeader("User-Agent"), response));
    }

    /**
     * 3.2 刷新令牌。
     * refresh token 仅从 HttpOnly cookie 读取，不再接受请求体；
     * 先校验 Origin 防 CSRF，再由 Service 校验白名单。
     */
    @PostMapping("/auth/refresh")
    public Result<TokenVO> refresh(HttpServletRequest request) {
        assertSameOrigin(request);
        return Result.success(authService.refresh(request));
    }

    /** 3.4 当前登录用户信息 */
    @GetMapping("/auth/me")
    public Result<UserInfoVO> me(HttpServletRequest request) {
        return Result.success(authService.me((Long) request.getAttribute("userId")));
    }

    /** 3.3 退出登录：access 拉黑、refresh 白名单删除并清除 cookie */
    @PostMapping("/auth/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = resolveToken(request);
        if (token != null) {
            authService.logout(token, request, response);
        }
        return Result.success(null);
    }

    /** 3.5 更新个人信息 / 改密 */
    @PatchMapping("/users/me")
    public Result<UserInfoVO> updateMe(@RequestBody UserUpdateDTO dto, HttpServletRequest request) {
        return Result.success(authService.updateProfile((Long) request.getAttribute("userId"), dto));
    }

    /**
     * CSRF 双保险：刷新接口依赖 cookie 自动携带，
     * 校验请求 Origin 必须在允许源白名单内，否则拒绝。
     */
    private void assertSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !Arrays.asList(allowOrigins).contains(origin)) {
            log.warn("刷新请求来源非法 origin={}", origin);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "非法来源的刷新请求");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
