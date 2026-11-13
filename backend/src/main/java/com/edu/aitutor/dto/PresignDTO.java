package com.edu.aitutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请预签名上传 URL 入参，对齐接口文档 17.3.1。
 */
@Data
public class PresignDTO {

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 扩展名，如 pdf / docx / txt */
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    /** 业务类型：1 知识库文档，2 聊天媒体，3 头像，4 数字人资源，9 其他 */
    @NotNull(message = "业务类型不能为空")
    private Integer bizType;

    /** 文件大小（字节），可选 */
    private Long fileSize;
}
