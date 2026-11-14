package com.edu.aitutor.framework.config;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信相关配置。仅 sms.provider=aliyun 时创建阿里云客户端。
 */
@Configuration
@EnableConfigurationProperties(AliyunSmsProperties.class)
public class SmsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "aliyun")
    public IAcsClient acsClient(AliyunSmsProperties props) throws ClientException {
        DefaultProfile profile = DefaultProfile.getProfile(
                props.getRegionId(), props.getAccessKeyId(), props.getAccessKeySecret());
        DefaultProfile.addEndpoint(props.getRegionId(), "Dypnsapi", props.getEndpoint());
        return new DefaultAcsClient(profile);
    }
}
