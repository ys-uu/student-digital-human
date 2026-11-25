package com.edu.aitutor.module.privatechat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息对象，对齐接口文档 18.4.4 private_recv 的 data 结构。
 * <p>
 * 同时用于 WebSocket 推送与 HTTP 历史查询，保证两种通道返回结构一致。
 * ID 类字段以字符串返回，避免前端 Number 精度丢失。
 */
@Data
public class MessageVO {

    /** 消息ID */
    private String messageId;

    /** 会话ID */
    private String conversationId;

    /**
     * 会话内单调递增序号，从 1 开始连续。
     * 客户端用于顺序保证与空洞检测：seq == localMax+1 表示接上，
     * seq &gt; localMax+1 表示中间漏收，需调用 seq 区间接口补洞。
     */
    private Long seq;

    /** 发送者 user_id */
    private String from;

    /** 发送者姓名 */
    private String fromName;

    /** 接收者 user_id */
    private String to;

    /**
     * 消息内容。
     * <p>
     * v1.9 起：文本消息有值；图片/文件/音频消息可能为 null，
     * 前端需按 msgType 判断渲染方式，不能假设 content 一定存在。
     */
    private String content;

    /** 消息类型：1 文本，2 图片，3 文件，4 音频 */
    private Integer msgType;

    /** 媒体地址（MinIO 直链或预签名 URL）；文本消息为 null */
    private String mediaUrl;

    /** 媒体文件大小（字节），可选 */
    private Long mediaSize;

    /** 音频时长（秒），仅 msgType=4 有值 */
    private Integer mediaDuration;

    /** 媒体原始文件名，文件消息展示用 */
    private String mediaName;

    /** 送达时间 */
    private LocalDateTime deliveredAt;

    /** 已读时间 */
    private LocalDateTime readAt;

    /** 发送时间 */
    private LocalDateTime createTime;
}