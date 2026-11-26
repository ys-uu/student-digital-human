package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.module.privatechat.repository.redis.PrivateChannelRedisRepository;
import com.edu.aitutor.module.privatechat.vo.MessageVO;
import com.edu.aitutor.module.privatechat.websocket.WsPacket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 私信实时推送。
 * <p>
 * 两条投递路径：
 * <ol>
 *   <li><b>本机直推</b>：接收方连接落在当前实例时，直接遍历本地 session 发送，无网络开销；</li>
 *   <li><b>Redis Pub/Sub 广播</b>：接收方连在别的实例时，本实例发布到频道，
 *       持有该连接的实例订阅后推送。单实例部署下本实例会收到自己发布的消息，
 *       因此消息体带 originId，收到与本实例 ID 相同时跳过，避免重复推送。</li>
 * </ol>
 * 离线时不投递也不报错：消息已在 MySQL 落库，由客户端上线后的 sync 补拉。
 * <p>
 * 并发安全：同一 WebSocketSession 不支持并发写（否则抛 TEXT_PARTIAL_WRITING），
 * 发送必须对 session 加锁，这点沿用 DashboardWebSocketHandler 的既有做法。
 */
@Service
public class PrivatePushService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PrivatePushService.class);

    /** 本实例标识，用于 Pub/Sub 消息回环去重 */
    private final String originId = UUID.randomUUID().toString();

    private final OnlineUserService onlineUserService;
    private final PrivateChannelRedisRepository privateChannelRedisRepository;
    /** Spring Boot 4 默认装配 Jackson 3，此处沿用大屏 handler 的 Jackson 2 实例 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrivatePushService(OnlineUserService onlineUserService,
                              PrivateChannelRedisRepository privateChannelRedisRepository) {
        this.onlineUserService = onlineUserService;
        this.privateChannelRedisRepository = privateChannelRedisRepository;
    }

    /**
     * ★★★【Q1 实时性 + Q4 离线兜底 + PPT P17 跨实例】推送一条私信 ★★★
     * <p>
     * 这是"服务端主动推"的具体实现，也是 Q1 与 Q4 的分界点：
     * <pre>
     * 接收方在线  → deliverLocal() 直接推本机连接（Q1：毫秒级到达）
     *              + Redis Pub/Sub 广播给其他实例（P17：多机部署时对方可能连在别的机器）
     * 接收方离线  → 什么都不做（Q4：消息已落库，等上线 sync 补拉）
     * </pre>
     * <p>
     * <b>为什么离线时不报错、不重试</b>：
     * <br>
     * PPT P15 讲 App 被杀的兜底方案是厂商推送（APNs/FCM），本项目是 Web 端无此能力，
     * 因此离线路径直接依赖 Q4 的"落库 + 上线补拉"。
     * 这不是缺陷，而是 PPT P12 强调的设计原则：
     * <b>"推"只是加速，"拉"才是保证最终一致的兜底</b>。
     * <p>
     * <b>跨实例投递的两个关键点</b>（PPT P17 高并发扩展）：
     * <ol>
     *   <li>在线表存 Redis 而非进程内存——否则加到第二台机器就收不到消息；</li>
     *   <li>用 Redis Pub/Sub 广播，各实例只推自己持有的连接；
     *       消息带 originId，发布者收到自己的广播时跳过，避免重复推送。</li>
     * </ol>
     */
    public void pushMessage(MessageVO message) {
        Long receiverId;
        try {
            receiverId = Long.valueOf(message.getTo());
        } catch (Exception e) {
            log.warn("推送目标非法，to={}", message.getTo());
            return;
        }
        // ★【Q4】：查 Redis 在线表判断对方是否在线
        // 离线直接返回——消息已在库里，对方上线后 sync 时自然能拉到。
        if (!onlineUserService.isOnline(receiverId)) {
            log.debug("接收方离线，跳过推送，receiverId={}，messageId={}", receiverId, message.getMessageId());
            return;
        }

        WsPacket packet = WsPacket.of(WsPacket.PRIVATE_RECV, toMap(message));
        String json = toJson(packet);
        if (json == null) {
            return;
        }

        // ★【Q1】：先推本机持有的连接（对方可能连在当前实例上）
        deliverLocal(receiverId, json, null);

        // ★【P17 跨实例】：再广播给其他实例
        // 携带有 originId，收到自己发的消息时跳过（见 onMessage），避免重复推送。
        // 单实例部署时这段也会执行，只是没有别的实例接收，无副作用。
        try {
            privateChannelRedisRepository.publish(originId + "|" + receiverId + "|" + json);
        } catch (Exception e) {
            // Pub/Sub 失败不影响本机推送；其他实例上的连接由客户端补拉兜底
            log.warn("广播私信推送失败，receiverId={}：{}", receiverId, e.getMessage());
        }
    }

    /**
     * 推送一条系统通知给指定用户，报文类型 notify。
     */
    public void pushNotification(Long userId, Map<String, Object> notification) {
        if (userId == null || !onlineUserService.isOnline(userId)) {
            return;
        }
        String json = toJson(WsPacket.of(WsPacket.NOTIFY, notification));
        if (json == null) {
            return;
        }
        deliverLocal(userId, json, null);
        try {
            privateChannelRedisRepository.publish(originId + "|" + userId + "|" + json);
        } catch (Exception e) {
            log.warn("广播通知推送失败，userId={}：{}", userId, e.getMessage());
        }
    }

    /**
     * 向用户所有连接推送一条报文（如已读回执），供业务层直接调用。
     */
    public void pushPacket(Long userId, WsPacket packet) {
        if (userId == null) {
            return;
        }
        String json = toJson(packet);
        if (json != null) {
            deliverLocal(userId, json, null);
        }
    }

    /**
     * Redis Pub/Sub 回调：其他实例广播过来的推送在此落地。
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        // 格式：originId|receiverId|json
        int first = payload.indexOf('|');
        int second = first < 0 ? -1 : payload.indexOf('|', first + 1);
        if (first < 0 || second < 0) {
            log.warn("私信广播报文格式非法，已忽略");
            return;
        }
        String fromOrigin = payload.substring(0, first);
        if (originId.equals(fromOrigin)) {
            // 本实例自己发布的，已在本机直推过一次，跳过避免重复
            return;
        }
        Long receiverId;
        try {
            receiverId = Long.valueOf(payload.substring(first + 1, second));
        } catch (NumberFormatException e) {
            log.warn("私信广播接收方非法，已忽略");
            return;
        }
        String json = payload.substring(second + 1);
        deliverLocal(receiverId, json, fromOrigin);
    }

    /**
     * 把报文推给本实例持有的该用户全部连接。
     * <p>
     * 发送失败（连接已断/被关）时移除该连接，避免后续反复失败。
     */
    private void deliverLocal(Long userId, String json, String expectedOrigin) {
        Map<String, WebSocketSession> sessions = onlineUserService.localSessionsOf(userId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            try {
                if (session.isOpen()) {
                    // 同一 session 并发写会抛 TEXT_PARTIAL_WRITING，必须串行化
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception e) {
                log.debug("推送失败，移除连接 sessionId={}：{}", session.getId(), e.getMessage());
                onlineUserService.offline(userId, session);
            }
        }
    }

    /**
     * MessageVO 转 Map，使推送报文与 HTTP 返回结构一致（字段名沿用 18.4.4）。
     */
    private Map<String, Object> toMap(MessageVO vo) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("message_id", vo.getMessageId());
        data.put("conversation_id", vo.getConversationId());
        data.put("seq", vo.getSeq());
        data.put("from", vo.getFrom());
        data.put("from_name", vo.getFromName());
        data.put("to", vo.getTo());
        data.put("content", vo.getContent());
        data.put("msg_type", vo.getMsgType());
        data.put("media_url", vo.getMediaUrl());
        data.put("media_size", vo.getMediaSize());
        data.put("media_duration", vo.getMediaDuration());
        data.put("media_name", vo.getMediaName());
        data.put("create_time", vo.getCreateTime() == null ? null : vo.getCreateTime().toString());
        return data;
    }

    private String toJson(WsPacket packet) {
        try {
            return objectMapper.writeValueAsString(packet);
        } catch (Exception e) {
            log.warn("报文序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /** 供持有本实例标识的场景做回环判断（测试用） */
    public String getOriginId() {
        return originId;
    }
}