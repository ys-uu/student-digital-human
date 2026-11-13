package com.edu.aitutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置。
 * 凭证不写死，本地通过 yaml、生产通过环境变量注入。
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO S3 API 接入地址，如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥 */
    private String secretKey;

    /** 默认桶（知识库文档等未显式指定时使用） */
    private String defaultBucket = "documents";

    /** 预签名 URL 有效期（秒），默认 600 秒 */
    private long presignExpiry = 600;
}
