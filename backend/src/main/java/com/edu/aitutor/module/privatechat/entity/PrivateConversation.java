package com.edu.aitutor.module.privatechat.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信会话实体，对应表36 private_conversation。
 * <p>
 * 一对用户之间只保留一条会话：写入前由应用层把较小 user_id 归一到 user_a_id、
 * 较大者归一到 user_b_id，配合唯一键 uk_pair(user_a_id, user_b_id) 保证唯一。
 * 未读数与已读游标按 a/b 两组列拆分，避免引入"每会话每人一行"的成员表。
 */
@Data
public class PrivateConversation {

    /** 会话ID */
    private Long id;

    /** 会话编号（UUID），唯一 */
    private String conversationNo;

    /** 对话方A，约定 user_a_id < user_b_id */
    private Long userAId;

    /** 对话方B */
    private Long userBId;

    /** 最后一条消息ID，关联 private_message.id */
    private Long lastMessageId;

    /** 最后一条消息时间，会话列表排序依据 */
    private LocalDateTime lastMessageAt;

    /** 最后一条消息摘要（截断至 200 字符） */
    private String lastMessagePreview;

    /** A 的未读消息数 */
    private Integer aUnreadCount;

    /** B 的未读消息数 */
    private Integer bUnreadCount;

    /** A 已读到的最新消息ID（游标） */
    private Long aLastReadId;

    /** B 已读到的最新消息ID（游标） */
    private Long bLastReadId;

    /** A 已送达到的最大 seq，用于"已送达"状态回执 */
    private Long aDeliveredSeq;

    /** B 已送达到的最大 seq */
    private Long bDeliveredSeq;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    private Integer isDeleted;
}