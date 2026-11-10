package com.edu.aitutor.service.impl;

import com.edu.aitutor.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 本地模拟短信服务：不依赖阿里云，固定验证码 123456，便于开发与演示。
 * sms.provider 缺省或为 mock 时生效。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsServiceImpl implements SmsService {

    /** 模拟固定验证码 */
    public static final String MOCK_CODE = "123456";

    @Override
    public void sendVerifyCode(String phone) {
        // 真实场景此处为短信下发；模拟环境仅打印日志
        log.info("【模拟短信】手机号 {} 的登录验证码为：{}（5分钟内有效）", phone, MOCK_CODE);
    }

    @Override
    public boolean checkVerifyCode(String phone, String code) {
        return MOCK_CODE.equals(code);
    }
}
