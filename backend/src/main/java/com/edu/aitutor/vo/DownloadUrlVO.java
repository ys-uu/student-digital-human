package com.edu.aitutor.vo;

import lombok.Data;

/**
 * 预签名下载 URL 响应，对齐接口文档 17.3.3。
 */
@Data
public class DownloadUrlVO {

    /** 预签名下载 URL */
    private String downloadUrl;

    /** URL 有效期（秒） */
    private long expiresIn;
}
