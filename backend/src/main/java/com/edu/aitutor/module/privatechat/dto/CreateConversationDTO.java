package com.edu.aitutor.module.privatechat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建或复用会话入参，对齐接口文档 18.5.2。
 */
@Data
public class CreateConversationDTO {

    /** 对方 user_id */
    @NotNull(message = "对方用户不能为空")
    private Long peerId;
}