package com.edu.aitutor.module.privatechat.websocket;

import lombok.Data;

import java.util.Map;

/**
 * WebSocket 统一报文包装，格式见接口文档 18.3：
 * <pre>{ "type": "...", "data": { } }</pre>
 * <p>
 * 收发两侧共用同一结构，避免各处手拼 JSON 造成字段不一致。
 */
@Data
public class WsPacket {

    /** 报文类型，取值见接口文档 18.4 */
    private String type;

    /** 业务载荷，无载荷时为 null */
    private Map<String, Object> data;

    public WsPacket() {
    }

    public WsPacket(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public static WsPacket of(String type, Map<String, Object> data) {
        return new WsPacket(type, data);
    }

    // ==================== 报文类型常量 ====================

    /** 服务端 → 客户端：连接建立成功 */
    public static final String CONNECTED = "connected";
    /** 客户端 → 服务端：心跳 */
    public static final String PING = "ping";
    /** 服务端 → 客户端：心跳应答 */
    public static final String PONG = "pong";
    /** 客户端 → 服务端：发送1对1消息 */
    public static final String PRIVATE_MSG = "private_msg";
    /** 服务端 → 客户端：发送确认 */
    public static final String PRIVATE_ACK = "private_ack";
    /** 服务端 → 客户端：收到消息 */
    public static final String PRIVATE_RECV = "private_recv";
    /** 双向：已读回执 */
    public static final String PRIVATE_READ = "private_read";
    /**
     * 双向：已送达回执（三态消息中的"已送达"）。
     * C → S 上报「我收悉到 seq 几」，S → C 通知发送方「你那条已被对方接收」。
     * 见《实时聊天系统技术与实现》P10 三态与 P12 游标对账。
     */
    public static final String PRIVATE_DELIVERED = "private_delivered";
    /**
     * C → S：请求补洞。
     * 客户端发现 seq 跳号（收到 N 但本地最新是 M，且 N > M+1）时发送，
     * 服务端返回 (after_seq, up_to_seq] 区间缺失的消息。见 P11 空洞检测。
     */
    public static final String PRIVATE_RESYNC = "private_resync";
    /** 客户端 → 服务端：上线补拉 */
    public static final String SYNC = "sync";
    /** 服务端 → 客户端：系统通知 */
    public static final String NOTIFY = "notify";
    /** 服务端 → 客户端：业务异常 */
    public static final String ERROR = "error";
}