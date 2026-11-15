package com.edu.aitutor.module.file.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import io.minio.errors.ErrorResponseException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 MinIO SDK 的存储服务实现：预签名 URL 签发与对象存在性校验。
 */
@Slf4j
@Service
public class MinioStorageServiceImpl implements FileStorageService {

    @Resource
    private MinioClient minioClient;

    @Override
    public String presignedPutUrl(String bucket, String objectKey, Duration expiry) {
        return presigned(Method.PUT, bucket, objectKey, expiry);
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, Duration expiry) {
        return presigned(Method.GET, bucket, objectKey, expiry);
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储访问失败");
        } catch (Exception e) {
            log.error("校验 MinIO 对象失败 bucket={} key={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储访问失败");
        }
    }

    private String presigned(Method method, String bucket, String objectKey, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) expiry.getSeconds())
                    .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败 method={} bucket={} key={}", method, bucket, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成预签名 URL 失败");
        }
    }
}
