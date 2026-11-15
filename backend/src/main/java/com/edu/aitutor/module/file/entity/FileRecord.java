package com.edu.aitutor.module.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一文件元数据，对应 file_record 表；物理对象存 MinIO，本表只登记对象 key 与元数据。
 */
@Data
public class FileRecord {

    /** 文件 ID */
    private Long id;

    /** MinIO 桶名 */
    private String bucketName;

    /** MinIO 对象 key */
    private String objectKey;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名：pdf / docx / txt / md 等 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件内容哈希（SHA-256，去重校验） */
    private String fileHash;

    /** 业务类型：1 知识库文档，2 聊天媒体，3 头像，4 数字人资源，9 其他 */
    private Integer bizType;

    /** 上传人 sys_user.id */
    private Long uploaderId;

    /** 创建时间（仅追加） */
    private LocalDateTime createTime;

    /** 逻辑删除：0 未删，1 已删 */
    private Integer isDeleted;
}
