package com.edu.aitutor.module.privatechat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送私信入参，对齐接口文档 18.4.2（WebSocket）与 18.5.4（HTTP 兜底）。
 * <p>
 * to / client_msg_id 在 WebSocket 场景由报文 data 解析而来，在 HTTP 场景由请求体直接映射，
 * 两种通道共用同一份校验规则，保证行为一致。
 *
 * ============================================================================
 * v1.9 变更：支持图片与音频
 * ----------------------------------------------------------------------------
 * msgType 取值扩展为 1~4：
 *   1 文本  → content 必填，mediaUrl 留空
 *   2 图片  → mediaUrl 必填，content 可为空
 *   3 文件  → mediaUrl 必填，content 可为空
 *   4 音频  → mediaUrl 必填，content 可为空，mediaDuration 可选
 *
 * 关键改动：content 的校验从 {@code @NotBlank} 改为 {@code @Size(max=2000)}。
 * 原因：图片/音频消息没有文字内容，若强制要求 content 非空，
 *      客户端就得塞一个空字符串或占位符，语义上是脏数据。
 *      "文本必须非空、媒体必须有 URL" 这条业务规则改由
 *      PrivateMessageService.assertMessagePayload() 按类型判断，
 *      比一刀切的注解校验更准确。
 * ============================================================================
 */
@Data
public class SendPrivateMessageDTO {

    /** 接收方 user_id */
    @NotNull(message = "接收方不能为空")
    private Long to;

    /**
     * 消息内容，长度上限 2000 字符。
     * <p>
     * 文本消息必填（由业务层校验）；图片/文件/音频消息可为空。
     */
    @Size(max = 2000, message = "消息内容不能超过 2000 字符")
    private String content;

    /** 消息类型：1 文本，2 图片，3 文件，4 音频 */
    @NotNull(message = "消息类型不能为空")
    @Min(value = 1, message = "消息类型取值 1~4")
    @Max(value = 4, message = "消息类型取值 1~4")
    private Integer msgType;

    /** 媒体文件访问地址；msgType 不为 1 时必填（由业务层校验） */
    @Size(max = 512, message = "媒体地址不能超过 512 字符")
    private String mediaUrl;

    /** 媒体文件大小（字节），可选，用于前端展示与流量估算 */
    private Long mediaSize;

    /** 音频时长（秒），仅 msgType=4 时有意义 */
    private Integer mediaDuration;

    /** 媒体原始文件名，可选，文件消息展示用 */
    @Size(max = 128, message = "文件名不能超过 128 字符")
    private String mediaName;

    /**
     * 客户端生成的幂等ID（建议 UUID）。
     * 服务端按 (senderId, clientMsgId) 唯一约束去重，重复提交返回首次落库结果。
     */
    @NotBlank(message = "客户端消息ID不能为空")
    @Size(max = 64, message = "客户端消息ID不能超过 64 字符")
    private String clientMsgId;
}