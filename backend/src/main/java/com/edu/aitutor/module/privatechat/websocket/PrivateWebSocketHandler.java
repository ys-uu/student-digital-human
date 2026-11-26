package com.edu.aitutor.module.privatechat.websocket;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.module.privatechat.dto.SendPrivateMessageDTO;
import com.edu.aitutor.module.privatechat.service.OnlineUserService;
import com.edu.aitutor.module.privatechat.service.PrivateMessageService;
import com.edu.aitutor.module.privatechat.vo.MessageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信实时通道 /ws/private，报文契约见接口文档第 18 章。
 * <p>
 * 职责边界：本类只做「报文解析 → 调业务 → 回包」，
 * 落库与推送全部委托给 {@link PrivateMessageService}，避免把手写 SQL 逻辑塞进 WebSocket 层。
 * <p>
 * 鉴权：握手阶段由 WebSocketHandshakeInterceptor 校验 JWT 并写入 userId，
 * 本类所有业务动作都以该 userId 为发送方，<b>不信任报文里任何身份字段</b>。
 *
 * ============================================================================
 * 【PPT P03 四问在本类中的落点】—— 按此顺序阅读代码即可对应上 PPT
 * ----------------------------------------------------------------------------
 * 【Q1 实时性】服务端如何主动推
 *   · afterConnectionEstablished()  —— 连接建立，登记在线表
 *   · handleTextMessage()          —— 收到客户端帧，按 type 分发
 *   · send()                       —— ★所有下行报文的唯一出口（服务端主动推）
 *   说明：WebSocket 建立后，服务端可在任意时刻调用 send() 推数据，
 *        这正是 HTTP 做不到的"服务端主动找客户端"。
 *
 * 【Q2 可靠性】消息会不会丢
 *   · handleSendMessage()   —— 调业务落库后，回 private_ack（★ACK 确认）
 *   · 客户端侧配套：3s 未收到 ACK 就重发（见 usePrivateSocket.js sendWithRetry）
 *   · 服务端去重：uk_client(sender_id, client_msg_id) 唯一索引
 *   三者缺一不可，PPT P10 强调"重发与去重永远是一对"。
 *
 * 【Q3 顺序性】先说的怎么排在后面
 *   · handleSendMessage() 中调用 messageService.send()，内部会分配会话级 seq
 *   · ACK 报文回带 seq，供客户端排序与空洞检测
 *   · handleResync() —— 客户端发现 seq 跳号时补洞（PPT P11 的 pullMissing）
 *
 * 【Q4 状态与多端】离线消息谁保管
 *   · afterConnectionEstablished() —— 多端并存：一个 userId 可持有多个 session
 *   · afterConnectionClosed()      —— 断开时清理在线标记
 *   · handleSync()                 —— ★上线补拉：断线期间的消息由客户端按游标拉取
 *   核心思想（PPT P12）：推是加速，拉是兜底。
 * ============================================================================
 */
@Component
public class PrivateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PrivateWebSocketHandler.class);

    private final OnlineUserService onlineUserService;
    private final PrivateMessageService messageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrivateWebSocketHandler(OnlineUserService onlineUserService,
                                   PrivateMessageService messageService) {
        this.onlineUserService = onlineUserService;
        this.messageService = messageService;
    }

    // ==================== 连接生命周期 ====================

    /**
     * 【Q1 实时性 + Q4 多端】连接建立回调。
     * <p>
     * 这里是 WebSocket 与 HTTP 的根本差异：HTTP 请求结束即断开，
     * 而此方法触发后连接持续存在，服务端此后可随时向它推送数据。
     * <p>
     * 同时登记在线表（{@link OnlineUserService}），
     * 一个 userId 可对应多个 session，实现"同一账号多设备同时在线"。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = currentUserId(session);
        if (userId == null) {
            // 握手拦截器未放行时会直接失败；此处兜底，避免匿名连接进入在线表
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }
        onlineUserService.online(userId, session);

        Map<String, Object> data = new HashMap<>();
        data.put("user_id", String.valueOf(userId));
        data.put("server_time", LocalDateTime.now().toString());
        send(session, WsPacket.of(WsPacket.CONNECTED, data));

        log.debug("私信连接建立，userId={}，sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = currentUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }
        // 任何报文都视为"活着"，续期在线标记
        onlineUserService.touch(userId, session);

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, ErrorCode.PARAM_ERROR, "报文不是合法 JSON", null);
            return;
        }
        String type = root.path("type").asText("");
        JsonNode data = root.path("data");

        // ★【Q1 实时性】报文分发中心
        // 客户端每条帧都在这里被路由到对应的业务处理方法，
        // 处理完再由 send() 下行回包——这就是"双向"的另一半。
        switch (type) {
            // ping/pong 心跳：PPt P07，30s 一次，防止 NAT/防火墙回收空闲连接
            case WsPacket.PING -> send(session, WsPacket.of(WsPacket.PONG, null));
            // 【Q2 + Q3 + Q4】发消息：落库 → 分配 seq → 回 ACK → 投递
            case WsPacket.PRIVATE_MSG -> handleSendMessage(session, userId, data);
            // 【Q4】已读游标：推进会话已读 seq，清零未读
            case WsPacket.PRIVATE_READ -> handleRead(session, userId, data);
            // 【Q2】已送达回执：驱动三态中的"已送达"
            case WsPacket.PRIVATE_DELIVERED -> handleDelivered(session, userId, data);
            // 【Q3】补洞：客户端发现 seq 跳号时拉取缺失区间（PPT P11 pullMissing）
            case WsPacket.PRIVATE_RESYNC -> handleResync(session, userId, data);
            // 【Q4】上线补拉：断线期间的消息按游标拉取（PPT P12）
            case WsPacket.SYNC -> handleSync(session, userId, data);
            default -> sendError(session, ErrorCode.PARAM_ERROR, "未知报文类型：" + type, type);
        }
    }

    /**
     * 【Q4 状态与多端】连接关闭回调。
     * <p>
     * 移除在线表标记。注意：标记带 TTL，
     * 即使进程崩溃来不及执行这里，Redis 也会在 60s 后自动清除——
     * 这是防止"假在线"（显示在线但推不到）的关键设计（PPT P13）。
     * <p>
     * 消息不会因断开而丢失：发送方发的消息已落库，
     * 本端重新上线后通过 handleSync() 补拉即可。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = currentUserId(session);
        if (userId != null) {
            onlineUserService.offline(userId, session);
            log.debug("私信连接关闭，userId={}，code={}", userId, status.getCode());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("私信连接传输异常，sessionId={}：{}", session.getId(), exception.getMessage());
    }

    // ==================== 报文处理 ====================

    /**
     * ★★★【Q2 可靠性 + Q3 顺序性 + Q4 状态】发消息的完整入口 ★★★
     * <p>
     * 一次发送要同时回答 PPT P03 的三个问题：
     * <pre>
     * ① 鉴权与建会话        → messageService.send()
     * ② 分配会话级 seq（Q3）→ Redis INCR，保证会话内单调递增
     * ③ 落库（Q4）          → 先进 MySQL，再谈推送；离线也不丢
     * ④ 回 ACK（Q2）        → 发送方收到才算"发出去了"
     * ⑤ 投递（Q1/Q4）       → 对方在线则推，离线则等上线 sync 补拉
     * </pre>
     * <p>
     * Q2 的三角关系（PPT P10 原文："重发与去重永远是一对，单写一个必出事"）：
     * <ul>
     *   <li><b>ACK</b>：就是下面 send(PRIVATE_ACK) 这一行，客户端靠它判断是否需要重发；</li>
     *   <li><b>重发</b>：客户端 3s 未收到 ACK 就原样重发（usePrivateSocket.js sendWithRetry）；</li>
     *   <li><b>去重</b>：服务端 uk_client(sender_id, client_msg_id) 唯一索引兜底，
     *       同一 clientMsgId 重复到达时返回首次落库结果，不重复投递。</li>
     * </ul>
     * <p>
     * Q3：ACK 报文里回带 seq，客户端据此排序、并判断是否出现空洞。
     */
    private void handleSendMessage(WebSocketSession session, Long userId, JsonNode data) {
        SendPrivateMessageDTO dto = new SendPrivateMessageDTO();
        try {
            dto.setTo(parseLong(data.path("to")));
            dto.setMsgType(data.hasNonNull("msg_type") ? data.get("msg_type").asInt() : 1);
            dto.setMediaUrl(data.hasNonNull("media_url") ? data.get("media_url").asText() : null);
            // v1.9 新增媒体元数据：图片尺寸、音频时长、文件名，客户端可选上传
            dto.setMediaSize(data.hasNonNull("media_size") ? data.get("media_size").asLong() : null);
            dto.setMediaDuration(data.hasNonNull("media_duration") ? data.get("media_duration").asInt() : null);
            dto.setMediaName(data.hasNonNull("media_name") ? data.get("media_name").asText() : null);
            // client_msg_id 是幂等键的锚点（PPT P10）：整个去重体系都靠它
            dto.setClientMsgId(data.hasNonNull("client_msg_id") ? data.get("client_msg_id").asText() : null);
            dto.setContent(data.hasNonNull("content") ? data.get("content").asText() : null);
        } catch (Exception e) {
            sendError(session, ErrorCode.PARAM_ERROR, "报文字段格式错误", WsPacket.PRIVATE_MSG);
            return;
        }

        // v1.9：to 与 client_msg_id 始终必填；
        // content / media_url 按消息类型二选一，具体规则交给业务层 assertMessagePayload 判断，
        // 这里只做最基础的非空拦截，避免图片消息因 content 为空被误拒。
        if (dto.getTo() == null || isBlank(dto.getClientMsgId())) {
            sendError(session, ErrorCode.PARAM_ERROR, "to、client_msg_id 均为必填", WsPacket.PRIVATE_MSG);
            return;
        }
        // 消息类型扩展为 1~4（新增音频）
        if (dto.getMsgType() != null && (dto.getMsgType() < 1 || dto.getMsgType() > 4)) {
            sendError(session, ErrorCode.PARAM_ERROR, "msg_type 取值 1~4", WsPacket.PRIVATE_MSG);
            return;
        }

        try {
            // 业务核心：鉴权 → 建会话 → 分配 seq(Q3) → 落库(Q4) → 事务提交后推送
            MessageVO saved = messageService.send(userId, dto);

            // ★【Q2 可靠性】ACK 确认回执
            // 发送方收到后把"发送中"改为"已发送"；
            // 若 3s 内没收到，客户端会自动重发（见 usePrivateSocket.js）。
            // 服务端幂等保证重发不会产生重复消息。
            Map<String, Object> ack = new HashMap<>();
            ack.put("client_msg_id", dto.getClientMsgId());   // 让客户端对上号
            ack.put("message_id", saved.getMessageId());
            ack.put("conversation_id", saved.getConversationId());
            ack.put("seq", saved.getSeq());                   // ★【Q3】顺序基准，客户端据此排序
            ack.put("create_time", saved.getCreateTime() == null
                    ? LocalDateTime.now().toString()
                    : saved.getCreateTime().toString());
            send(session, WsPacket.of(WsPacket.PRIVATE_ACK, ack));
        } catch (BusinessException e) {
            sendError(session, e.getCode(), e.getMessage(), WsPacket.PRIVATE_MSG);
        } catch (Exception e) {
            log.warn("发送私信失败，userId={}：{}", userId, e.getMessage());
            sendError(session, ErrorCode.SYSTEM_ERROR, "消息发送失败，请重试", WsPacket.PRIVATE_MSG);
        }
    }

    /**
     * ★【Q4 状态与多端】private_read：推进已读 seq 游标，会话未读数清零。
     *
     * <pre>
     * PPT P12 原话：「已读也用游标记录，readSeq 往前推就代表已读，
     *                还能顺手算出未读数 = lastSeq - readSeq」
     *
     * 本方法做三件事：
     *   ① 把会话表的 last_read_seq 推进到客户端上报的位置；
     *   ② 把该会话内 seq <= 游标 的未读消息批量打上 read_at；
     *   ③ 回执确认，并通知对方"你发的消息被读了"（驱动三态的"已读"）。
     *
     * 为什么用 seq 游标而不是逐条标记已读：
     *   一条 UPDATE 就能把整个会话的未读清零，避免消息量大时 N 次数据库往返。
     *
     * 为什么已读状态在服务端而不是客户端：
     *   多端场景下（手机 + 电脑），各端从服务端读取同一份已读游标，
     *   保证"任一端读过，其他端状态一致"。
     *   ⚠ 已知限制：跨端不会【实时】刷新，但重新加载会拿到正确值。
     * </pre>
     */
    private void handleRead(WebSocketSession session, Long userId, JsonNode data) {
        Long conversationId = parseLong(data.path("conversation_id"));
        // 兼容旧字段名 last_read_id，统一按 seq 语义处理
        Long lastReadSeq = parseLong(data.path("last_read_seq"));
        if (lastReadSeq == null) {
            lastReadSeq = parseLong(data.path("last_read_id"));
        }
        if (conversationId == null) {
            sendError(session, ErrorCode.PARAM_ERROR, "conversation_id 必填", WsPacket.PRIVATE_READ);
            return;
        }
        try {
            // 服务端推进已读游标：会话表 + 消息表双向更新
            Long cursor = messageService.markRead(userId, conversationId, lastReadSeq);
            Map<String, Object> ack = new HashMap<>();
            ack.put("conversation_id", String.valueOf(conversationId));
            ack.put("last_read_seq", String.valueOf(cursor));
            // 回执给本人（确认已读生效）
            send(session, WsPacket.of(WsPacket.PRIVATE_READ, ack));
            // 注意：通知对方的动作在 messageService.markRead() 内部通过
            //      pushService.pushPacket(peerId, ...) 完成，不在本类处理。
        } catch (BusinessException e) {
            sendError(session, e.getCode(), e.getMessage(), WsPacket.PRIVATE_READ);
        } catch (Exception e) {
            log.warn("标记已读失败，userId={}：{}", userId, e.getMessage());
            sendError(session, ErrorCode.SYSTEM_ERROR, "标记已读失败", WsPacket.PRIVATE_READ);
        }
    }

    /**
     * ★【Q2 可靠性】private_delivered：接收方上报「已收悉到 seq 几」，驱动三态中的"已送达"。
     *
     * <pre>
     * 三态演进（PPT P10）：
     *   发送中 → 已发送（收到 private_ack）
     *           → 已送达（收到本报文，★ 说明对方客户端真的收到了，不只是服务端存了）
     *           → 已读（对方点开会话，触发 private_read）
     *
     * 为什么"送达"和"已读"要分开：
     *   送达 = 消息进入了对方客户端（对方可能没看）；
     *   已读 = 对方真的打开了会话。
     *   微信等产品都区分这两级，否则"对方已读"的含义会被稀释。
     *
     * 触发时机：
     *   客户端收到 private_recv 后【立即】回本报文，
     *   而不是等用户点开——这正是"送达"的语义。
     * </pre>
     */
    private void handleDelivered(WebSocketSession session, Long userId, JsonNode data) {
        Long conversationId = parseLong(data.path("conversation_id"));
        Long deliveredSeq = parseLong(data.path("delivered_seq"));
        if (conversationId == null || deliveredSeq == null) {
            sendError(session, ErrorCode.PARAM_ERROR,
                    "conversation_id、delivered_seq 均为必填", WsPacket.PRIVATE_DELIVERED);
            return;
        }
        try {
            // 推进会话表的 delivered_seq + 回写消息的 delivered_at，并通知发送方
            messageService.markDelivered(userId, conversationId, deliveredSeq);
        } catch (BusinessException e) {
            sendError(session, e.getCode(), e.getMessage(), WsPacket.PRIVATE_DELIVERED);
        } catch (Exception e) {
            log.warn("标记送达失败，userId={}：{}", userId, e.getMessage());
            sendError(session, ErrorCode.SYSTEM_ERROR, "标记送达失败", WsPacket.PRIVATE_DELIVERED);
        }
    }

    /**
     * ★【Q3 顺序性】private_resync：按 seq 区间补洞（PPT P11 的 pullMissing）。
     *
     * <pre>
     * 触发时机：
     *   客户端 trackSeq() 发现收到 seq=10 但本地 localMax=7，
     *   说明 8、9 两条没收到 → 立即发 private_resync 请求 (7, 10] 区间。
     *
     * 为什么会漏消息（即便 TCP 有序）：
     *   ① 断线重连瞬间，服务端推的那几条客户端不在线；
     *   ② 多实例部署时两台机器的推送到达顺序可能交错；
     *   ③ 消息"分配 seq"与"实际推送"不是原子操作，并发时可能乱序到达。
     *
     * 为什么返回结构与 sync 一致：
     *   两者都是"批量返回消息"，客户端复用同一套插入逻辑，
     *   减少处理分支、降低出错概率。
     *
     * 与 Q4 的 sync 的区别：
     *   · sync         → 跨会话，按消息 id 游标，用于"重连后补全部漏掉的"
     *   · private_resync → 单会话，按 seq 区间，用于"发现跳号后补这一段"
     *   前者是兜底大扫，后者是精确补漏。
     * </pre>
     */
    private void handleResync(WebSocketSession session, Long userId, JsonNode data) {
        Long conversationId = parseLong(data.path("conversation_id"));
        Long afterSeq = parseLong(data.path("after_seq"));
        Long upToSeq = parseLong(data.path("up_to_seq"));
        if (conversationId == null || afterSeq == null) {
            sendError(session, ErrorCode.PARAM_ERROR,
                    "conversation_id、after_seq 均为必填", WsPacket.PRIVATE_RESYNC);
            return;
        }
        try {
            // 拉取 (afterSeq, upToSeq] 区间的消息，按 seq 升序返回
            List<MessageVO> messages = messageService.listSeqRange(
                    userId, conversationId, afterSeq, upToSeq, 200);
            List<Map<String, Object>> payload = new ArrayList<>(messages.size());
            for (MessageVO message : messages) {
                payload.add(toMap(message));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("conversation_id", String.valueOf(conversationId));
            result.put("count", payload.size());
            // has_more=true 表示这一批被 200 条上限截断，客户端应继续请求
            result.put("has_more", payload.size() >= 200);
            result.put("messages", payload);
            send(session, WsPacket.of(WsPacket.PRIVATE_RESYNC, result));
        } catch (BusinessException e) {
            sendError(session, e.getCode(), e.getMessage(), WsPacket.PRIVATE_RESYNC);
        } catch (Exception e) {
            log.warn("补洞失败，userId={}：{}", userId, e.getMessage());
            sendError(session, ErrorCode.SYSTEM_ERROR, "补洞失败，请重试", WsPacket.PRIVATE_RESYNC);
        }
    }

    /**
     * ★【Q4 状态与多端】sync：重连后补拉缺失消息 —— "离线不丢"的兜底入口。
     *
     * <pre>
     * PPT P12 原话：「一切以『拉』为兜底，『推』只是加速：实时推送丢了没关系，
     *                sync 游标对账保证最终一致——这是 IM 可靠性的设计心法。」
     *
     * 触发时机：
     *   客户端 ws.onopen 里【每次连接成功都发】（不只是重连）：
     *     send('sync', { last_msg_id: lastMessageId })
     *
     * 为什么必须由客户端主动拉、而不是服务端"上线后主动推"：
     *   ① 服务端不知道客户端本地已有哪些消息，推多了会重复、推少了会漏；
     *   ② 大批量推送容易超时被截断，而拉取可以分批（has_more 续拉）；
     *   ③ 拉取是幂等的，客户端重试不会有副作用。
     *   这就是 PPT P12 坑点里说的"离线消息用推送而不是拉取"的反面教材。
     *
     * 跨会话场景的处理：
     *   不同会话的 seq 各自从 1 开始，无法用一个 seq 判全局新旧，
     *   所以这里按【消息 id】（全局自增）作跨会话游标，
     *   返回的每条消息仍带自己的 session 内 seq 供前端局部排序。
     *
     * 单次上限 200 条：返回满批时客户端应再次发送 sync 续拉，直到 has_more=false。
     * </pre>
     */
    private void handleSync(WebSocketSession session, Long userId, JsonNode data) {
        Long lastMsgId = parseLong(data.path("last_msg_id"));
        try {
            List<MessageVO> messages = messageService.syncOffline(userId, lastMsgId);
            List<Map<String, Object>> payload = new ArrayList<>(messages.size());
            for (MessageVO message : messages) {
                payload.add(toMap(message));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("count", payload.size());
            result.put("has_more", payload.size() >= 200);
            result.put("messages", payload);
            send(session, WsPacket.of(WsPacket.SYNC, result));
        } catch (Exception e) {
            log.warn("离线补拉失败，userId={}：{}", userId, e.getMessage());
            sendError(session, ErrorCode.SYSTEM_ERROR, "补拉失败，请重试", WsPacket.SYNC);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从握手 attributes 取 userId；这是唯一的身份来源。
     */
    private Long currentUserId(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * 发送报文。WebSocketSession 不支持并发写，必须加锁串行化。
     */
    private void send(WebSocketSession session, WsPacket packet) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(packet);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("回包失败，sessionId={}：{}", session.getId(), e.getMessage());
        }
    }

    /**
     * 发送业务异常报文，格式见接口文档 18.4.8。
     */
    private void sendError(WebSocketSession session, int code, String msg, String refType) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("msg", msg);
        if (refType != null) {
            data.put("ref_type", refType);
        }
        send(session, WsPacket.of(WsPacket.ERROR, data));
    }

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

    /**
     * 解析 long 型字段：兼容 JSON 数字与字符串两种写法（前端按字符串传 BIGINT 是文档约定）。
     */
    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.asLong();
            }
            String text = node.asText();
            return text.isBlank() ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("关闭连接失败：{}", e.getMessage());
        }
    }
}