package com.edu.aitutor.module.file.service;

import java.time.Duration;

/**
 * 对象存储抽象：收口预签名与对象校验，业务层不直接依赖具体 SDK。
 * 更换存储后端时只需提供新实现，业务编排不变。
 */
public interface FileStorageService {

    /** 生成预签名 PUT（上传）URL */
    String presignedPutUrl(String bucket, String objectKey, Duration expiry);

    /** 生成预签名 GET（下载）URL */
    String presignedGetUrl(String bucket, String objectKey, Duration expiry);

    /** 校验对象是否已存在 */
    boolean objectExists(String bucket, String objectKey);
}
