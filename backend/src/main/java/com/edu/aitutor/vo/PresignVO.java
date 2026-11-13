package com.edu.aitutor.vo;

import lombok.Data;

/**
 * 预签名上传 URL 响应，对齐接口文档 17.3.1。
 */
@Data
public class PresignVO {

    /** 文件记录 ID */
    private Long fileId;

    /** 桶名 */
    private String bucketName;

    /** 对象 key */
    private String objectKey;

    /** 预签名上传 URL，前端直接 PUT */
    private String uploadUrl;

    /** URL 有效期（秒） */
    private long expiresIn;
}
