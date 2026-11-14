package com.edu.aitutor.module.qr.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.qr.dto.QrScanDTO;
import com.edu.aitutor.module.qr.entity.QrSession;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.auth.mapper.LoginWhitelistMapper;
import com.edu.aitutor.module.user.mapper.UserMapper;
import com.edu.aitutor.framework.jwt.JwtUtil;
import com.edu.aitutor.module.qr.vo.QrCreateVO;
import com.edu.aitutor.module.qr.vo.QrMockUserVO;
import com.edu.aitutor.module.qr.vo.QrStatusVO;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class QrLoginServiceImpl implements QrLoginService {

    /** 状态机：0 待扫码，1 待确认，2 已登录，3 已过期，4 已取消，5 已拒绝 */
    private static final int STATUS_WAIT_SCAN = 0;
    private static final int STATUS_WAIT_CONFIRM = 1;
    private static final int STATUS_LOGGED_IN = 2;
    private static final int STATUS_CANCELED = 4;
    private static final int STATUS_REJECTED = 5;

    @Value("${qr.login.ttl-seconds:120}")
    private long ttlSeconds;

    @Value("${qr.login.whitelist:}")
    private String whitelistConfig;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private LoginWhitelistMapper whitelistMapper;
    @Resource
    private JwtUtil jwtUtil;

    @Override
    public QrCreateVO create() {
        String qrToken = UUID.randomUUID().toString().replace("-", "");
        QrSession session = new QrSession();
        session.setQrToken(qrToken);
        session.setStatus(STATUS_WAIT_SCAN);
        session.setCreateTime(LocalDateTime.now());
        save(session);

        QrCreateVO vo = new QrCreateVO();
        vo.setQrToken(qrToken);
        vo.setExpireSeconds((int) ttlSeconds);
        return vo;
    }

    @Override
    public QrStatusVO status(String qrToken) {
        QrSession session = getSession(qrToken);
        QrStatusVO vo = new QrStatusVO();
        if (session == null) {
            vo.setStatus(3);
            vo.setStatusDesc("已过期");
            return vo;
        }
        vo.setStatus(session.getStatus());
        vo.setStatusDesc(statusDesc(session.getStatus()));
        vo.setToken(session.getToken());
        vo.setUsername(session.getUsername());
        vo.setId(session.getUserId());
        vo.setRole(session.getRole());
        return vo;
    }

    @Override
    public void scan(QrScanDTO dto) {
        QrSession session = getSession(dto.getQrToken());
        if (session == null) {
            throw new BusinessException(ErrorCode.QR_EXPIRED, "二维码不存在或已过期，请刷新");
        }
        if (session.getStatus() != STATUS_WAIT_SCAN) {
            throw new BusinessException(ErrorCode.QR_EXPIRED, "二维码状态异常，请刷新后重试");
        }
        String account = dto.getAccount();
        if (!isWhitelisted(account)) {
            session.setStatus(STATUS_REJECTED);
            save(session);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该账号不在登录白名单，无法扫码登录");
        }
        User user = userMapper.selectByUserNo(account);
        if (user == null) {
            session.setStatus(STATUS_REJECTED);
            save(session);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "账号不存在，无法扫码登录");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            session.setStatus(STATUS_REJECTED);
            save(session);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，无法登录");
        }
        session.setStatus(STATUS_WAIT_CONFIRM);
        session.setAccount(account);
        session.setUserId(user.getId());
        session.setUsername(user.getUserNo());
        session.setRole(user.getRole());
        save(session);
    }

    @Override
    public void confirm(String qrToken) {
        QrSession session = getSession(qrToken);
        if (session == null) {
            throw new BusinessException(ErrorCode.QR_EXPIRED, "二维码不存在或已过期，请刷新");
        }
        if (session.getStatus() != STATUS_WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.QR_EXPIRED, "二维码状态异常，请刷新后重试");
        }
        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        String token = jwtUtil.generateAccessToken(user.getId(), user.getUserNo(), user.getRole());
        session.setStatus(STATUS_LOGGED_IN);
        session.setToken(token);
        save(session);
    }

    @Override
    public void cancel(String qrToken) {
        QrSession session = getSession(qrToken);
        if (session == null) {
            throw new BusinessException(ErrorCode.QR_EXPIRED, "二维码不存在或已过期，请刷新");
        }
        session.setStatus(STATUS_CANCELED);
        save(session);
    }

    @Override
    public List<QrMockUserVO> mockUsers() {
        return userMapper.selectMockUsers().stream().map(user -> {
            QrMockUserVO vo = new QrMockUserVO();
            vo.setAccount(user.getUserNo());
            vo.setRealName(user.getRealName());
            vo.setRole(user.getRole());
            vo.setInWhitelist(isWhitelisted(user.getUserNo()));
            return vo;
        }).toList();
    }

    // ==================== 私有辅助 ====================

    private void save(QrSession session) {
        try {
            redisTemplate.opsForValue().set(
                    String.format(RedisKeys.QR_LOGIN, session.getQrToken()),
                    objectMapper.writeValueAsString(session),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("二维码会话保存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "二维码会话保存失败");
        }
    }

    private QrSession getSession(String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(String.format(RedisKeys.QR_LOGIN, qrToken));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, QrSession.class);
        } catch (Exception e) {
            log.error("二维码会话解析失败 qrToken={}", qrToken, e);
            return null;
        }
    }

    /**
     * 白名单校验：login_whitelist 表有启用数据时以表为准，否则回退配置文件。
     */
    private boolean isWhitelisted(String account) {
        if (whitelistMapper.countEnabled() > 0) {
            return whitelistMapper.selectAllEnabled().stream()
                    .anyMatch(w -> w.getAccount().equals(account));
        }
        if (whitelistConfig == null || whitelistConfig.isBlank()) {
            return false;
        }
        return Arrays.stream(whitelistConfig.split(","))
                .map(String::trim)
                .anyMatch(account::equals);
    }

    private String statusDesc(int status) {
        return switch (status) {
            case 0 -> "待扫码";
            case 1 -> "已扫码待确认";
            case 2 -> "已登录";
            case 3 -> "已过期";
            case 4 -> "已取消";
            case 5 -> "已拒绝";
            default -> "未知状态";
        };
    }
}
