package com.edu.aitutor.module.privatechat.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * 私信消息文档（MongoDB）。
 *
 * ============================================================================
 * 【为什么消息改存 MongoDB 而不是 MySQL】
 * ----------------------------------------------------------------------------
 * ① 结构灵活：文本消息只有 content；图片要 url+宽高；音频要 url+时长；
 *    文件要 url+文件名+大小。若用 MySQL 关系表，需要加一堆大部分时间都为 NULL 的列，
 *    且每加一种消息类型就要 ALTER TABLE。文档模型天然适配这种"同表异构"数据。
 *
 * ② 增长模式匹配：消息量随时间线性增长且只追加不修改，正是文档数据库擅长的场景；
 *    MySQL 单表过千万行后需要分库分表，MongoDB 分片更简单。
 *
 * ③ 媒体元数据：本表新增 mediaUrl / mediaSize / mediaDuration，
 *    一条消息可以携带图片或音频的完整描述，无需再建关联表。
 *    注意：文件本体不放这里，仍存 MinIO，本集合只存 URL 与元数据。
 *
 * ============================================================================
 * 【与 MySQL 的分工（重要，答辩会被问）】
 * ----------------------------------------------------------------------------
 *   MySQL   ：sys_user、course、class_info、class_member、private_conversation
 *             → 强关系型数据，需要 JOIN、外键、事务
 *   MongoDB ：private_message
 *             → 消息本体，结构随类型变化、量级大、只需按会话/游标查询
 *
 *   两边通过【业务主键】而非外键关联：
 *     conversationId → MySQL.private_conversation.id
 *     senderId/receiverId → MySQL.sys_user.id
 *   不建物理外键，由应用层保证引用完整性（跨库无法建外键）。
 *
 * ============================================================================
 * 【字段设计要点】
 * ----------------------------------------------------------------------------
 * · id      ：业务主键，由应用层分配（MySQL 并发自增序列表），全局单调递增。
 *             用途同原 MySQL 自增主键：仅作跨会话"新旧"判断与游标，不参与会话内排序。
 * · seq     ：会话内单调递增序号，由 Redis INCR 发号（MessageSeqService）。
 *             用途：会话内排序、空洞检测、已读/送达游标。
 *             与 id 的分工：seq 管会话内有序，id 管跨会话新旧。
 * · clientMsgId：客户端生成的幂等键，配 uk_client 唯一索引实现重发去重。
 * · msgType ：1 文本 / 2 图片 / 3 文件 / 4 音频。
 * · content ：文本内容；媒体消息可为空（这是与原 MySQL schema 最大的差异）。
 * · mediaUrl：媒体文件在 MinIO 的访问地址（预签名或直链）。
 * · mediaSize / mediaDuration：媒体元数据，用于前端展示与流量预估。
 * ============================================================================
 */
@Document(collection = "private_message")
@CompoundIndexes({
        // 幂等去重：同一发送者 + 同一 clientMsgId 只允许一条（对应原 MySQL uk_client）
        @CompoundIndex(name = "uk_client", def = "{'senderId': 1, 'clientMsgId': 1}", unique = true),
        // 会话内序号唯一：防止并发抢到同一 seq（对应原 MySQL uk_conv_seq）
        @CompoundIndex(name = "uk_conv_seq", def = "{'conversationId': 1, 'seq': 1}", unique = true)
})
public class PrivateMessageDocument {

    /** 业务主键：应用层分配的全局递增 id，用 Mongo 的 _id 承载，避免额外查询 */
    @Id
    @Field("_id")
    private Long id;

    /** 所属会话，引用 MySQL private_conversation.id（不建物理外键） */
    @Indexed(name = "idx_conversation")
    @Field("conversationId")
    private Long conversationId;

    /** 会话内单调递增序号，从 1 开始连续，由 Redis INCR 发号 */
    @Field("seq")
    private Long seq;

    /** 发送者 user_id，引用 MySQL sys_user.id */
    @Field("senderId")
    private Long senderId;

    /** 接收者 user_id，引用 MySQL sys_user.id；冗余存储便于按接收者补拉 */
    @Indexed(name = "idx_receiver")
    @Field("receiverId")
    private Long receiverId;

    /**
     * 消息类型：1 文本，2 图片，3 文件，4 音频。
     * 用 int 而非枚举存库，避免以后加类型时旧数据反序列化失败。
     */
    @Field("msgType")
    private Integer msgType;

    /**
     * 文本内容。
     * <p>
     * 媒体消息（图片/文件/音频）时可为 null —— 这是与 MySQL schema 的关键差异：
     * MySQL 里 content 是 NOT NULL，导致发图片也必须塞个空字符串；
     * MongoDB 允许字段缺失，语义更清晰。
     */
    @Field("content")
    private String content;

    /** 媒体文件访问地址（MinIO）；文本消息为 null */
    @Field("mediaUrl")
    private String mediaUrl;

    /** 媒体文件大小（字节）；文本消息为 null */
    @Field("mediaSize")
    private Long mediaSize;

    /** 音频时长（秒）；仅音频消息有值，其余为 null */
    @Field("mediaDuration")
    private Integer mediaDuration;

    /** 媒体原始文件名（可选），便于前端下载时显示 */
    @Field("mediaName")
    private String mediaName;

    /** 客户端生成的幂等 ID（UUID），重发时保持不变，配 uk_client 去重 */
    @Field("clientMsgId")
    private String clientMsgId;

    /** 送达时间：对方客户端收到即回写，驱动三态中的「已送达」 */
    @Field("deliveredAt")
    private LocalDateTime deliveredAt;

    /** 已读时间：对方点开会话时回写，驱动三态中的「已读」 */
    @Field("readAt")
    private LocalDateTime readAt;

    /** 撤回状态：0 正常，1 已撤回（预留） */
    @Field("recallStatus")
    private Integer recallStatus;

    /** 举报状态：0 无，1 已举报（预留） */
    @Field("reportStatus")
    private Integer reportStatus;

    /** 记录生成时间，只追加不修改 */
    @Field("createTime")
    private LocalDateTime createTime;

    // ==================== 便捷判断 ====================

    /** 是否为文本消息 */
    public boolean isText() {
        return msgType == null || msgType == 1;
    }

    /** 是否为图片消息 */
    public boolean isImage() {
        return msgType != null && msgType == 2;
    }

    /** 是否为文件消息 */
    public boolean isFile() {
        return msgType != null && msgType == 3;
    }

    /** 是否为音频消息 */
    public boolean isAudio() {
        return msgType != null && msgType == 4;
    }

    // ==================== getter / setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Integer getMsgType() {
        return msgType;
    }

    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public Long getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(Long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public Integer getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(Integer mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public String getMediaName() {
        return mediaName;
    }

    public void setMediaName(String mediaName) {
        this.mediaName = mediaName;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public Integer getRecallStatus() {
        return recallStatus;
    }

    public void setRecallStatus(Integer recallStatus) {
        this.recallStatus = recallStatus;
    }

    public Integer getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(Integer reportStatus) {
        this.reportStatus = reportStatus;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}