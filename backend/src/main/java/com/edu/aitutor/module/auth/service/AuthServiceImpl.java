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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
    public TokenVO loginByPassword(PasswordLoginDTO dto, String ip, String userAgent) {
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
        TokenVO token = afterLogin(user, passwordLoginType(user.getRole()), dto.getDeviceId(), ip, userAgent);
        antiCrawlerGuard.clearLoginFailure(dto.getUsername());
        return token;
    }

    @Override
    public TokenVO register(RegisterDTO dto, String ip, String userAgent) {
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
        return buildTokenVO(saved);
    }

    @Override
    public TokenVO loginBySms(SmsLoginDTO dto, String ip, String userAgent) {
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
        TokenVO token = afterLogin(user, TYPE_SMS, dto.getDeviceId(), ip, userAgent);
        antiCrawlerGuard.clearLoginFailure(phone);
        return token;
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (!JwtUtil.TYPE_REFRESH.equals(claims.get("tokenType"))) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "refreshToken 类型无效");
            }
            Long userId = Long.valueOf(claims.get("userId").toString());
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
            }
            return buildTokenVO(user);
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
    public void logout(String token) {
        try {
            long remain = jwtUtil.getRemainingExpirationMillis(token);
            if (remain > 0) {
                redisTemplate.opsForValue().set(
                        String.format(RedisKeys.TOKEN_BLACKLIST, token), "1", Duration.ofMillis(remain));
            }
        } catch (Exception e) {
            // token 已失效则无需拉黑
            log.warn("退出登录拉黑跳过: {}", e.getMessage());
        }
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

    /** 登录成功后的统一收尾：更新登录信息、写审计、签发令牌 */
    private TokenVO afterLogin(User user, int loginType, String deviceId, String ip, String userAgent) {
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        update.setLastLoginIp(ip);
        userMapper.updateLoginInfo(update);
        recordLog(user.getId(), user.getUserNo(), loginType, 1, null, deviceId, ip, userAgent);
        return buildTokenVO(user);
    }

    private TokenVO buildTokenVO(User user) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId(), user.getUserNo(), user.getRole()));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(user.getId(), user.getUserNo(), user.getRole()));
        vo.setExpiresIn(jwtUtil.getAccessExpirationSeconds());
        vo.setTokenType("Bearer");
        vo.setUser(buildUserInfoVO(user));
        return vo;
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

    private String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
