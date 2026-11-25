package com.edu.aitutor.module.privatechat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项，对齐接口文档 18.5.1。
 * <p>
 * conversation_id / peer_id 按文档约定以字符串返回（前端按字符串处理 BIGINT），
 * 避免 JS Number 精度丢失。
 */
@Data
public class ConversationVO {

    /** 会话ID（字符串形式，防精度丢失） */
    private String conversationId;

    /** 会话编号 */
    private String conversationNo;

    /** 对方 user_id */
    private String peerId;

    /** 对方姓名 */
    private String peerName;

    /** 对方角色：1 学生，2 教师 */
    private Integer peerRole;

    /** 对方头像 */
    private String peerAvatarUrl;

    /** 最后一条消息摘要 */
    private String lastMessagePreview;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageAt;

    /** 当前用户在该会话的未读数 */
    private Integer unreadCount;
}