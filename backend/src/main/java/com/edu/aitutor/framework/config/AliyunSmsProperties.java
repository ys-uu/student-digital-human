package com.edu.aitutor.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云号码认证服务配置（仅 sms.provider=aliyun 时生效）。
 * 凭证不要写死，生产环境通过环境变量注入。
 */
@Data
@ConfigurationProperties(prefix = "aliyun.sms")
public class AliyunSmsProperties {

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** 地域 ID，如 cn-hangzhou */
    private String regionId = "cn-hangzhou";

    /** 接入点域名 */
    private String endpoint = "dypnsapi.aliyuncs.com";

    /** 系统赠送签名名称（控制台获取） */
    private String signName;

    /** 系统赠送验证码模板编号（控制台获取） */
    private String templateCode;
}
