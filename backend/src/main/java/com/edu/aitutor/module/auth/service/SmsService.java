package com.edu.aitutor.module.auth.service;

/**
 * 短信验证码服务。
 * mock 实现本地模拟；aliyun 实现由阿里云号码认证服务托管验证码生命周期。
 */
public interface SmsService {

    /**
     * 发送验证码
     */
    void sendVerifyCode(String phone);

    /**
     * 校验验证码
     *
     * @return true=校验通过
     */
    boolean checkVerifyCode(String phone, String code);
}
