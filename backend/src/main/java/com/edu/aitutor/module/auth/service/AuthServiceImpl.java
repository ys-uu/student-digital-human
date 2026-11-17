package com.edu.aitutor.module.auth.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.auth.dto.PasswordLoginDTO;
import com.edu.aitutor.module.auth.dto.RegisterDTO;
import com.edu.aitutor.module.auth.dto.SmsLoginDTO;
import com.edu.aitutor.module.user.dto.UserUpdateDTO;
import com.edu.aitutor.module.auth.entity.AuthLoginLog;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.auth.mapper.AuthLoginLogMapper;
import com.edu.aitutor.module.user.mapper.UserMapper;
import com.edu.aitutor.framework.security.AntiCrawlerGuard;
import com.edu.aitutor.framework.security.RbacUserDetailsService;
import com.edu.aitutor.framework.jwt.JwtUtil;
import com.edu.aitutor.module.auth.vo.TokenVO;
import com.edu.aitutor.module.user.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 登录方式：1 学号密码，2 工号密码，3 手机号验证码，4 扫码 */
    private static final int TYPE_STUDENT_PWD = 1;
    private static final int TYPE_TEACHER_PWD = 2;
    private static final int TYPE_SMS = 3;

    /** refresh token cookie 名称 */
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    /**
     * 作用路径取认证模块前缀而非仅 /refresh：
     * 登出接口 /auth/logout 也必须能读到该 cookie 才能删除白名单，
     * 若限定到 /refresh，登出请求路径不匹配，浏览器不会携带，会导致吊销失效。
     */
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** cookie Secure 开关：本地 HTTP 关闭，生产 HTTPS 开启，配置项 auth.refresh-cookie.secure */
    @Value("${auth.refresh-cookie.secure:false}")
    private boolean cookieSecure;

    @Resource
    private UserMapper userMapper;
    @Resource
    private AuthLoginLogMapper authLoginLogMapper;
    @Resource
    private SmsService smsService;
    @Resource
    private JwtUtil jwtUtil;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private RbacUserDetailsService rbacUserDetailsService;
    @Resource
    private AntiCrawlerGuard antiCrawlerGuard;

    @Override
    public TokenVO loginByPassword(PasswordLoginDTO dto, String ip, String userAgent,
                                   HttpServletResponse response) {
        // 登录失败锁定：在查库、BCrypt 校验之前拦截撞库请求
        antiCrawlerGuard.assertLoginAllowed(dto.getUsername(), ip);

        User user = userMapper.selectByUserNo(dto.getUsername());
        if (user == null) {
            antiCrawlerGuard.recordLoginFailure(dto.getUsername(), ip);
            recordLog(null, dto.getUsername(), TYPE_STUDENT_PWD, 0, "账号不存在", dto.getDeviceId(), ip, userAgent);
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLog(user.getId(), dto.getUsername(), passwordLoginType(user.getRole()), 0, "账号已禁用",
                    dto.getDeviceId(), ip, userAgent);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            antiCrawlerGuard.recordLoginFailure(dto.getUsername(), ip);
            recordLog(user.getId(), dto.getUsername(), passwordLoginType(user.getRole()), 0, "密码错误",
                    dto.getDeviceId(), ip, userAgent);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "账号或密码错误");
        }
        TokenVO token = afterLogin(user, passwordLoginType(user.getRole()), dto.getDeviceId(),
                ip, userAgent, response);
        antiCrawlerGuard.clearLoginFailure(dto.getUsername());
        return token;
    }

    @Override
    public TokenVO register(RegisterDTO dto, String ip, String userAgent,
                            HttpServletResponse response) {
        if (userMapper.selectByUserNo(dto.getUserNo()) != null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "账号已存在");
        }
        String phone = blankToNull(dto.getPhone());
        if (phone != null && userMapper.selectByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "手机号已被占用");
        }
        User user = new User();
        user.setUserNo(dto.getUserNo());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole() == null ? 1 : dto.getRole());
        user.setGender(dto.getGender() == null ? 0 : dto.getGender());
        user.setPhone(phone);
        user.setPhoneVerified(0);
        user.setStatus(1);
        userMapper.insert(user);

        User saved = userMapper.selectById(user.getId());
        recordLog(saved.getId(), saved.getUserNo(), passwordLoginType(saved.getRole()), 1, null, null, ip, userAgent);
        return buildLoginTokenVO(saved, response);
    }

    @Override
    public TokenVO loginBySms(SmsLoginDTO dto, String ip, String userAgent,
                              HttpServletResponse response) {
        String phone = dto.getPhone();
        // 验证码同样可被暴力猜测，纳入账号维度的失败锁定
        antiCrawlerGuard.assertLoginAllowed(phone, ip);
        // 先校验验证码，再处理账号
        if (!smsService.checkVerifyCode(phone, dto.getVerifyCode())) {
            antiCrawlerGuard.recordLoginFailure(phone, ip);
            recordLog(null, phone, TYPE_SMS, 0, "验证码错误或已过期", dto.getDeviceId(), ip, userAgent);
            throw new BusinessException(ErrorCode.SMS_CODE_ERROR, "验证码错误或已过期");
        }

        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            // 手机号不存在：自动注册（user_no=M+手机号，姓名给默认值，登录后引导补全）
            user = new User();
            user.setUserNo("M" + phone);
            user.setPasswordHash(null);
            user.setRealName("手机用户" + phone.substring(7));
            user.setRole(1);
            user.setGender(0);
            user.setPhone(phone);
            user.setPhoneVerified(1);
            user.setStatus(1);
            userMapper.insert(user);
            user = userMapper.selectById(user.getId());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLog(user.getId(), phone, TYPE_SMS, 0, "账号已禁用", dto.getDeviceId(), ip, userAgent);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        TokenVO token = afterLogin(user, TYPE_SMS, dto.getDeviceId(), ip, userAgent, response);
        antiCrawlerGuard.clearLoginFailure(phone);
        return token;
    }

    @Override
    public TokenVO refresh(HttpServletRequest request) {
        String refreshToken = resolveRefreshCookie(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "未找到刷新凭证，请重新登录");
        }
        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (!JwtUtil.TYPE_REFRESH.equals(claims.get("tokenType"))) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "refreshToken 类型无效");
            }
            String jti = claims.getId();
            Long userId = Long.valueOf(claims.get("userId").toString());
            // 白名单校验：键必须存在，且归属与令牌中的 userId 一致
            String storedUserId = redisTemplate.opsForValue()
                    .get(String.format(RedisKeys.REFRESH_WHITELIST, jti));
            if (storedUserId == null || !storedUserId.equals(userId.toString())) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录凭证已失效，请重新登录");
            }
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
            }
            // 本期不旋转：仅签发新 access token，refresh cookie 保持不变
            return buildRefreshTokenVO(user);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("refreshToken 解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "refreshToken 无效，请重新登录");
        }
    }

    @Override
    public UserInfoVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return buildUserInfoVO(user);
    }

    @Override
    public void logout(String token, HttpServletRequest request, HttpServletResponse response) {
        // 1 access token 加黑名单（现有逻辑），TTL 为剩余有效期
        try {
            long remain = jwtUtil.getRemainingExpirationMillis(token);
            if (remain > 0) {
                redisTemplate.opsForValue().set(
                        String.format(RedisKeys.TOKEN_BLACKLIST, token), "1", Duration.ofMillis(remain));
            }
        } catch (Exception e) {
            // token 已失效则无需拉黑
            log.warn("退出登录 access 拉黑跳过: {}", e.getMessage());
        }
        // 2 删除 refresh 白名单，并从用户 jti 集合移除
        try {
            String refreshToken = resolveRefreshCookie(request);
            if (refreshToken != null) {
                Claims claims = jwtUtil.parseToken(refreshToken);
                String jti = claims.getId();
                Long userId = Long.valueOf(claims.get("userId").toString());
                redisTemplate.delete(String.format(RedisKeys.REFRESH_WHITELIST, jti));
                redisTemplate.opsForSet()
                        .remove(String.format(RedisKeys.REFRESH_USER_SET, userId), jti);
            }
        } catch (Exception e) {
            // cookie 缺失或已过期，跳过白名单清理
            log.warn("退出登录 refresh 白名单清理跳过: {}", e.getMessage());
        }
        // 3 下发 maxAge=0 的过期 cookie，清除浏览器凭证
        writeRefreshCookie(response, "", Duration.ZERO);
    }

    @Override
    public UserInfoVO updateProfile(Long userId, UserUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (dto.getNewPassword() != null) {
            if (dto.getOldPassword() == null || user.getPasswordHash() == null
                    || !passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "原密码不正确");
            }
            user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        }
        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        userMapper.updateProfile(user);
        return UserInfoVO.from(userMapper.selectById(userId));
    }

    // ==================== 私有辅助 ====================

    /** 登录成功后的统一收尾：更新登录信息、写审计、签发令牌与 cookie */
    private TokenVO afterLogin(User user, int loginType, String deviceId, String ip,
                               String userAgent, HttpServletResponse response) {
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        update.setLastLoginIp(ip);
        userMapper.updateLoginInfo(update);
        recordLog(user.getId(), user.getUserNo(), loginType, 1, null, deviceId, ip, userAgent);
        return buildLoginTokenVO(user, response);
    }

    /**
     * 登录类响应：生成双 token、维护 refresh 白名单与用户 jti 集合、下发 cookie。
     */
    private TokenVO buildLoginTokenVO(User user, HttpServletResponse response) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUserNo(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getUserNo(), user.getRole());
        // 从生成的 refresh token 中取回 jti
        String jti = jwtUtil.parseToken(refreshToken).getId();

        // 白名单 String：jti -> userId，TTL 与 refresh 有效期一致（7 天）
        redisTemplate.opsForValue().set(
                String.format(RedisKeys.REFRESH_WHITELIST, jti),
                user.getId().toString(), Duration.ofDays(7));
        // 用户 jti 集合：支撑登出清理与未来全设备下线，TTL 7 天兜底
        String userSetKey = String.format(RedisKeys.REFRESH_USER_SET, user.getId());
        redisTemplate.opsForSet().add(userSetKey, jti);
        redisTemplate.expire(userSetKey, Duration.ofDays(7));

        // HttpOnly cookie 下发，JS 无法读取
        writeRefreshCookie(response, refreshToken, Duration.ofDays(7));

        TokenVO vo = new TokenVO();
        vo.setAccessToken(accessToken);
        vo.setExpiresIn(jwtUtil.getAccessExpirationSeconds());
        vo.setTokenType("Bearer");
        vo.setUser(buildUserInfoVO(user));
        return vo;
    }

    /**
     * 刷新类响应（本期不旋转）：仅签发新的 access token，响应仍带用户信息。
     */
    private TokenVO buildRefreshTokenVO(User user) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(
                user.getId(), user.getUserNo(), user.getRole()));
        vo.setExpiresIn(jwtUtil.getAccessExpirationSeconds());
        vo.setTokenType("Bearer");
        vo.setUser(buildUserInfoVO(user));
        return vo;
    }

    /**
     * 下发 refresh cookie。maxAge 为 0 时即清除浏览器凭证。
     */
    private void writeRefreshCookie(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .path(REFRESH_COOKIE_PATH)
                // 跨站不携带，缓解 CSRF
                .sameSite("Strict")
                .secure(cookieSecure)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 从请求 cookie 中读取 refresh token，不存在返回 null。
     */
    private String resolveRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 组装用户信息视图并填充权限码集合（权限码走 Redis 缓存）
     */
    private UserInfoVO buildUserInfoVO(User user) {
        UserInfoVO vo = UserInfoVO.from(user);
        vo.setPermissions(rbacUserDetailsService.listPermCodes(user.getId()));
        return vo;
    }

    /** 账密登录类型：教师=2 工号密码，其余=1 学号密码 */
    private int passwordLoginType(Integer role) {
        return role != null && role == 2 ? TYPE_TEACHER_PWD : TYPE_STUDENT_PWD;
    }

    private void recordLog(Long userId, String account, int loginType, int result, String reason,
                           String deviceId, String ip, String userAgent) {
        AuthLoginLog entity = new AuthLoginLog();
        entity.setUserId(userId);
        entity.setAccount(account);
        entity.setLoginType(loginType);
        entity.setLoginResult(result);
        entity.setFailReason(reason);
        entity.setDeviceId(deviceId);
        entity.setIp(ip);
        entity.setUserAgent(userAgent);
        authLoginLogMapper.insert(entity);
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
