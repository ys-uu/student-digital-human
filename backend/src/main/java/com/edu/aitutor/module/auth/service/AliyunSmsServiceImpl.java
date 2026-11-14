package com.edu.aitutor.module.auth.service;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.dypnsapi.model.v20170525.CheckSmsVerifyCodeRequest;
import com.aliyuncs.dypnsapi.model.v20170525.CheckSmsVerifyCodeResponse;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeRequest;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeResponse;
import com.aliyuncs.exceptions.ClientException;
import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.framework.config.AliyunSmsProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 阿里云号码认证服务实现。验证码由阿里云生成、下发与校验，本地不存储验证码。
 * 仅 sms.provider=aliyun 时生效。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsServiceImpl implements SmsService {

    @Resource
    private IAcsClient acsClient;
    @Resource
    private AliyunSmsProperties props;

    @Override
    public void sendVerifyCode(String phone) {
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest();
        request.setPhoneNumber(phone);
        request.setSignName(props.getSignName());
        request.setTemplateCode(props.getTemplateCode());
        // 模板参数：##code## 占位符由系统动态生成验证码，min 对应模板内有效期分钟数
        request.setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}");
        request.setCodeType(1L);      // 纯数字（占位符模式下必填）
        request.setCodeLength(6L);    // 6 位验证码，与前端输入框 maxlength=6 对齐
        request.setValidTime(300L);   // 有效期 300 秒（5 分钟），单位为秒
        request.setInterval(60L);     // 同号发送间隔 60 秒
        try {
            SendSmsVerifyCodeResponse response = acsClient.getAcsResponse(request);
            if (!"OK".equals(response.getCode())) {
                log.warn("阿里云验证码发送失败 code={} msg={}", response.getCode(), response.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后再试");
            }
        } catch (ClientException e) {
            log.error("阿里云验证码发送异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后再试");
        }
    }

    @Override
    public boolean checkVerifyCode(String phone, String code) {
        CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest();
        request.setPhoneNumber(phone);
        request.setVerifyCode(code);
        request.setCountryCode("86");
        try {
            CheckSmsVerifyCodeResponse response = acsClient.getAcsResponse(request);
            if (!"OK".equals(response.getCode()) || response.getModel() == null) {
                return false;
            }
            return "PASS".equalsIgnoreCase(response.getModel().getVerifyResult());
        } catch (ClientException e) {
            log.error("阿里云验证码校验异常", e);
            return false;
        }
    }
}
