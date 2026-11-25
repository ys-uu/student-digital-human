package com.edu.aitutor.module.privatechat.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信消息实体，对应表37 private_message。
 * <p>
 * 仅追加、不修改业务内容，故只有 create_time（与 chat_message 约定一致）。
 * client_msg_id 与唯一键 uk_client(sender_id, client_msg_id) 共同实现客户端幂等：
 * 断线重连重发或用户重复点击时，由数据库唯一约束兜底，命中即返回首次落库的消息。
 */
@Data
public class PrivateMessage {

    /** 消息ID，兼作游标分页的游标 */
    private Long id;

    /** 关联 private_conversation.id */
    private Long conversationId;

    /**
     * 会话内单调递增序号，由 Redis INCR 发号，从 1 开始连续。
     * <p>
     * 与主键 id 的区别：id 是全局自增、跨会话穿插，客户端无法据此判断是否漏收；
     * seq 在会话内连续，客户端可用 seq == localMax + 1 判断是否接上，
     * seq &gt; localMax + 1 则说明中间有空洞，需要补拉。
     * 见《实时聊天系统技术与实现》P11。
     */
    private Long seq;

    /** 发送者 user_id */
    private Long senderId;

    /** 接收者 user_id（冗余存储，便于按接收方补拉） */
    private Long receiverId;

    /** 消息文本内容 */
    private String content;

    /** 消息类型：1 文本，2 图片，3 文件 */
    private Integer msgType;

    /** 图片/文件 URL */
    private String mediaUrl;

    /** 客户端生成的幂等ID（UUID） */
    private String clientMsgId;

    /** 送达时间（在线推送成功时回写） */
    private LocalDateTime deliveredAt;

    /** 已读时间 */
    private LocalDateTime readAt;

    /** 撤回状态：0 正常，1 已撤回（预留） */
    private Integer recallStatus;

    /** 举报状态：0 无，1 已举报（预留） */
    private Integer reportStatus;

    /** 记录生成时间 */
    private LocalDateTime createTime;
}