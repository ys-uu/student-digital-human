package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.module.privatechat.document.PrivateMessageDocument;
import com.edu.aitutor.module.privatechat.dto.SendPrivateMessageDTO;
import com.edu.aitutor.module.privatechat.entity.PrivateConversation;
import com.edu.aitutor.module.privatechat.mapper.PrivateConversationMapper;
import com.edu.aitutor.module.privatechat.repository.PrivateMessageMongoRepository;
import com.edu.aitutor.module.privatechat.vo.ConversationVO;
import com.edu.aitutor.module.privatechat.vo.MessageVO;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 私信核心业务：会话建立、消息落库、已读推进、离线补拉。
 * <p>
 * 三条关键约定：
 * <ol>
 *   <li><b>先鉴权后建会话</b>：权限不通过时不写任何数据，避免留下空会话；</li>
 *   <li><b>先落库后推送</b>：消息写入 MongoDB 并更新会话摘要后才推送。
 *       推送动作注册在事务提交之后（AFTER_COMMIT），
 *       否则接收方可能在事务提交前收到消息、回查却查不到，出现"消息闪一下又消失"；</li>
 *   <li><b>幂等靠唯一键</b>：同一 clientMsgId 重复提交由 uk_client 兜底，
 *       命中即返回首次落库的消息，不重复入库、不重复推送。</li>
 * </ol>
 *
 * ============================================================================
 * 【v1.9 存储改造：消息从 MySQL 迁移到 MongoDB】
 * ----------------------------------------------------------------------------
 * 变更点：
 *   · 消息本体 → MongoDB 集合 private_message（支持文本/图片/文件/音频）；
 *   · 会话摘要 → 仍在 MySQL（参与列表分页与 JOIN，属强关系数据）；
 *   · 消息主键 id → 由 MySQL 序列表 message_id_seq 集中分配（Mongo 无自增主键）；
 *   · 会话内序号 seq → 仍由 Redis INCR 发号，逻辑不变。
 *
 * 为什么消息适合文档库：
 *   四种消息类型字段差异大（文本只有 content；图片要尺寸；音频要时长），
 *   文档模型无需为每种类型加列，也不会因新增类型而 ALTER TABLE。
 * ============================================================================
 */
@Service
public class PrivateMessageService {

    private static final Logger log = LoggerFactory.getLogger(PrivateMessageService.class);

    /** 会话摘要截断长度，与 private_conversation.last_message_preview 字段长度一致 */
    private static final int PREVIEW_MAX_LENGTH = 200;

    /** 离线补拉单次上限，超出时客户端再次发 sync 续拉 */
    private static final int SYNC_BATCH_SIZE = 200;

    private final PrivatePermissionService permissionService;
    private final PrivateConversationMapper conversationMapper;
    /**
     * 消息仓储（MongoDB）。
     * <p>
     * v1.9 起由原 PrivateMessageMapper（MySQL）替换为本文档仓储。
     * 方法语义一一对应，只是底层存储换成文档数据库，
     * 以支持文本/图片/文件/音频四种结构差异较大的消息体。
     */
    private final PrivateMessageMongoRepository messageRepository;
    private final PrivatePushService pushService;
    private final UserMapper userMapper;
    private final MessageSeqService seqService;
    /** 全局消息 ID 分配器（MongoDB 无自增主键，由 MySQL 序列表集中分配） */
    private final MessageIdGenerator idGenerator;

    public PrivateMessageService(PrivatePermissionService permissionService,
                                 PrivateConversationMapper conversationMapper,
                                 PrivateMessageMongoRepository messageRepository,
                                 PrivatePushService pushService,
                                 UserMapper userMapper,
                                 MessageSeqService seqService,
                                 MessageIdGenerator idGenerator) {
        this.permissionService = permissionService;
        this.conversationMapper = conversationMapper;
        this.messageRepository = messageRepository;
        this.pushService = pushService;
        this.userMapper = userMapper;
        this.seqService = seqService;
        this.idGenerator = idGenerator;
    }

    // ==================== 发送消息 ====================

    /**
     * 发送一条私信（WebSocket 与 HTTP 兜底共用）。
     * <p>
     * 流程遵循《实时聊天系统技术与实现》P4「一条消息的一生」：
     * 鉴权 → 建会话 → 分配 seq → 落库 → 更新摘要 → 事务提交后投递。
     * seq 必须先于落库分配：它既是订单号（顺序权威），也是幂等重试的重发依据。
     *
     * @return 落库后的消息对象；重复提交时返回首次落库的那条
     */
    /**
     * ★★★【Q2 + Q3 + Q4 三合一】发送消息的核心业务 ★★★
     * <p>
     * 这是全项目最值得讲解的方法：一次发送同时回答了 PPT P03 的三个问题。
     * <pre>
     * 步骤   做什么                   对应 PPT 问题        技术手段
     * ────────────────────────────────────────────────────────────────
     * ①  权限校验                   （安全）             同老师+同课程判定
     * ②  找或建会话                 （数据模型）         uk_pair 归一化去重
     * ③  幂等前置判断    ★【Q2】   消息会不会重复       client_msg_id 唯一索引
     * ④  分配 seq        ★【Q3】   先说的怎么排前面     Redis INCR 会话级发号
     * ⑤  落库            ★【Q4】   离线消息谁保管       先写 MySQL，再谈推送
     * ⑥  更新会话摘要              （展示）             未读数 +1、最后消息
     * ⑦  事务提交后推送  ★【Q1/Q4】对方怎么收到         在线推、离线等 sync
     * </pre>
     * <p>
     * <b>最关键的设计决策（PPT P12 原话："推是加速，拉是兜底"）：</b>
     * <br>
     * 第 ⑤ 步落库是<b>必做的</b>，第 ⑦ 步推送只是<b>顺带的事</b>。
     * 即使推送全部失败，消息已经在数据库里，
     * 对方上线后通过 sync 补拉即可——这就是"离线不丢"的本质。
     * <p>
     * <b>为什么推送要放在事务提交后（第 ⑦ 步）</b>：
     * <br>
     * 若在事务内推送，接收方可能在事务提交前收到消息、回查却查不到，
     * 出现"消息闪一下又消失"。用 registerAfterCommit 保证推送时数据已可见。
     * <p>
     * <b>为什么先判幂等再发号（第 ③ ④ 步顺序）</b>：
     * <br>
     * 重复提交不应该消耗新 seq，否则会造成 seq 空洞
     * （客户端空洞检测会误判为"漏收消息"而反复补拉）。
     *
     * @return 落库后的消息对象；重复提交时返回首次落库的那条
     */
    @Transactional
    public MessageVO send(Long fromId, SendPrivateMessageDTO dto) {
        // ① 权限校验：不通过直接抛出，不写任何数据
        permissionService.assertCanSend(fromId, dto.getTo());

        // ①.5 消息类型与媒体字段校验（v1.9 新增）
        // 文本消息必须有 content；媒体消息（图片/文件/音频）必须有 mediaUrl。
        // 这是 MySQL 时代不需要的检查——那时 content 是 NOT NULL，
        // 而文档模型允许字段缺失，所以必须显式校验，否则会写进一条"空消息"。
        int msgType = dto.getMsgType() == null ? 1 : dto.getMsgType();
        assertMessagePayload(msgType, dto);

        // ② 找或建会话（a<b 归一化，配合 uk_pair 保证一对用户仅一条会话）
        long a = Math.min(fromId, dto.getTo());
        long b = Math.max(fromId, dto.getTo());
        PrivateConversation conversation = findOrCreateConversation(a, b);

        // ③ ★【Q2 可靠性】幂等前置判断
        // 同一 clientMsgId 已落库则直接返回原记录，不重复入库、不消耗新 seq。
        // 这是客户端"3s 超时重发"能安全工作的前提（PPT P10）。
        // 改存 MongoDB 后这里查的是文档，但走的仍是 uk_client 唯一索引。
        PrivateMessageDocument existing = messageRepository.selectByClientId(fromId, dto.getClientMsgId());
        if (existing != null) {
            log.debug("消息重复提交，返回原记录，messageId={}", existing.getId());
            return toVO(existing);
        }

        // ④ ★【Q3 顺序性】分配会话级 seq
        // Redis INCR 原子发号，保证同一会话内严格递增、无空洞。
        // 客户端靠这个 seq 排序，并判断"是不是漏收了中间某条"。
        long dbMaxSeq = messageRepository.selectMaxSeq(conversation.getId());
        long seq = seqService.nextSeq(conversation.getId(), dbMaxSeq);

        // ④.5 分配全局消息 id（v1.9 新增）
        // MongoDB 没有 AUTO_INCREMENT，必须由 MySQL 序列表集中分配，
        // 保证 id 全局单调递增——这是离线补拉按 id 判新旧的前提。
        // 注意与 seq 的分工：id 管跨会话新旧，seq 管会话内顺序。
        long messageId = idGenerator.nextId();

        // ⑤ ★【Q4 状态】落库——这一步是整个可靠性的地基
        // 从 MySQL 改为 MongoDB：文档模型天然承载文本/图片/文件/音频的异构字段。
        PrivateMessageDocument message = new PrivateMessageDocument();
        message.setId(messageId);
        message.setConversationId(conversation.getId());
        message.setSeq(seq);                    // 会话内有序序号
        message.setSenderId(fromId);
        message.setReceiverId(dto.getTo());
        message.setMsgType(msgType);
        // 文本消息存 content；媒体消息 content 可为空（这是文档模型相对 MySQL 的优势）
        message.setContent(isBlank(dto.getContent()) ? null : dto.getContent());
        message.setMediaUrl(isBlank(dto.getMediaUrl()) ? null : dto.getMediaUrl());
        message.setMediaSize(dto.getMediaSize());
        message.setMediaDuration(dto.getMediaDuration());
        message.setMediaName(dto.getMediaName());
        message.setClientMsgId(dto.getClientMsgId());   // 幂等键
        message.setRecallStatus(0);
        message.setReportStatus(0);
        message.setCreateTime(LocalDateTime.now());

        try {
            messageRepository.insert(message);
        } catch (DuplicateKeyException e) {
            // 两类冲突：uk_client（客户端重发）或 uk_conv_seq（并发抢到同一 seq）
            PrivateMessageDocument dup = messageRepository.selectByClientId(fromId, dto.getClientMsgId());
            if (dup != null) {
                log.debug("消息重复提交（唯一键），返回原记录，messageId={}", dup.getId());
                return toVO(dup);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息序号冲突，请重试");
        }

        // ⑥ 更新会话摘要与接收方未读数（会话摘要仍在 MySQL）
        // 媒体消息的摘要文案由 summaryOf() 生成，例如 "[图片]"、"[语音 12'' ]"，
        // 否则会话列表会因 content 为空而显示空白。
        PrivateMessageDocument saved = messageRepository.selectById(messageId);
        LocalDateTime sendAt = saved.getCreateTime() == null ? LocalDateTime.now() : saved.getCreateTime();
        conversationMapper.updateAfterSend(
                conversation.getId(),
                saved.getId(),
                sendAt,
                summaryOf(saved),
                dto.getTo().equals(a)
        );

        MessageVO vo = toVO(saved);

        // ⑦ ★【Q1 实时性 + Q4 状态】推送——放在事务提交之后
        // 为什么必须在 afterCommit：
        //   若在事务内推送，对方可能在事务提交前收到消息、回查却查不到，
        //   出现"消息闪一下又消失"。
        //
        // 推送成败不影响消息本身：
        //   推成功了 → 对方实时看到（Q1 实时性）
        //   推失败/对方离线 → 消息仍在库里，等对方上线 sync 补拉（Q4 离线兜底）
        // 这就是 PPT P12 说的"推是加速，拉是兜底"。
        registerAfterCommit(() -> {
            try {
                pushService.pushMessage(vo);
            } catch (Exception e) {
                log.warn("事务提交后推送失败，messageId={}：{}", vo.getMessageId(), e.getMessage());
            }
        });

        return vo;
    }

    /**
     * 消息体校验（v1.9 新增，支持图片/音频后必需）。
     *
     * <pre>
     * 类型 1 文本  → content 必填，mediaUrl 忽略
     * 类型 2 图片  → mediaUrl 必填
     * 类型 3 文件  → mediaUrl 必填
     * 类型 4 音频  → mediaUrl 必填（mediaDuration 可选）
     * </pre>
     * 为什么不交给 MySQL 约束：文档模型没有 NOT NULL，
     * 若不在这里拦住，会写入"既无文字也无附件"的空消息，客户端无法渲染。
     */
    private void assertMessagePayload(int msgType, SendPrivateMessageDTO dto) {
        if (msgType < 1 || msgType > 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "msg_type 取值 1~4");
        }
        if (msgType == 1) {
            if (isBlank(dto.getContent())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文本消息 content 必填");
            }
            return;
        }
        if (isBlank(dto.getMediaUrl())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, MEDIA_TYPE_NAME.getOrDefault(msgType, "媒体") + "消息必须上传文件");
        }
        // 音频时长允许为空（部分浏览器无法读取时长），但若给了必须是正数
        if (msgType == 4 && dto.getMediaDuration() != null && dto.getMediaDuration() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "音频时长不能为负数");
        }
    }

    /** 消息类型名称，用于错误提示与会话摘要 */
    private static final Map<Integer, String> MEDIA_TYPE_NAME = Map.of(
            2, "图片", 3, "文件", 4, "语音");

    /**
     * 生成会话摘要文案。
     * <p>
     * 媒体消息 content 为空，若直接写入摘要，会话列表会显示空白，
     * 因此按类型生成占位文案，与微信等产品的做法一致。
     */
    private String summaryOf(PrivateMessageDocument message) {
        Integer type = message.getMsgType();
        if (type == null || type == 1) {
            return truncate(message.getContent());
        }
        return switch (type) {
            case 2 -> "[图片]";
            case 3 -> "[文件] " + (isBlank(message.getMediaName()) ? "" : message.getMediaName());
            // 音频摘要带上时长，便于在会话列表中一眼识别
            case 4 -> message.getMediaDuration() != null
                    ? "[语音] " + message.getMediaDuration() + "\""
                    : "[语音]";
            default -> "[消息]";
        };
    }

    /**
     * 查找或创建会话。
     * <p>
     * 并发场景下两个用户同时首聊，双方可能都判定"不存在"并同时插入，
     * uk_pair 会让其中一个抛 DuplicateKeyException；捕获后重查即可拿到已建好的那条，
     * 比加分布式锁更轻，语义也更准确。
     */
    private PrivateConversation findOrCreateConversation(long a, long b) {
        PrivateConversation existing = conversationMapper.selectByPair(a, b);
        if (existing != null) {
            return existing;
        }
        PrivateConversation conversation = new PrivateConversation();
        conversation.setConversationNo("PC" + UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        conversation.setUserAId(a);
        conversation.setUserBId(b);
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException e) {
            log.debug("会话并发创建冲突，回查已存在记录，a={}，b={}", a, b);
            PrivateConversation concurrent = conversationMapper.selectByPair(a, b);
            if (concurrent == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话创建失败，请重试");
            }
            return concurrent;
        }
    }

    // ==================== 会话与历史 ====================

    /**
     * 创建或复用会话（HTTP 18.5.2）。
     */
    @Transactional
    public Map<String, Object> createOrGetConversation(Long userId, Long peerId) {
        permissionService.assertCanSend(userId, peerId);
        long a = Math.min(userId, peerId);
        long b = Math.max(userId, peerId);
        PrivateConversation conversation = findOrCreateConversation(a, b);
        Map<String, Object> data = new HashMap<>();
        data.put("conversation_id", String.valueOf(conversation.getId()));
        data.put("conversation_no", conversation.getConversationNo());
        return data;
    }

    /**
     * 会话列表（HTTP 18.5.1）。
     */
    public PageResult<ConversationVO> listConversations(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> rows = conversationMapper.selectMyConversations(userId, offset, safeSize);
        long total = conversationMapper.countMyConversations(userId);

        List<ConversationVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(asString(row.get("conversation_id")));
            vo.setConversationNo(asString(row.get("conversation_no")));
            Long peerId = asLong(row.get("peer_id"));
            vo.setPeerId(peerId == null ? null : String.valueOf(peerId));
            vo.setLastMessagePreview(asString(row.get("last_message_preview")));
            vo.setLastMessageAt(asDateTime(row.get("last_message_at")));
            vo.setUnreadCount(asInt(row.get("unread_count"), 0));

            // 补齐对方展示信息；用户被删除时容忍为空，不阻断列表
            if (peerId != null) {
                User peer = userMapper.selectById(peerId);
                if (peer != null) {
                    vo.setPeerName(peer.getRealName());
                    vo.setPeerRole(peer.getRole());
                    vo.setPeerAvatarUrl(peer.getAvatarUrl());
                }
            }
            list.add(vo);
        }
        return new PageResult<>(total, list);
    }

    /**
     * 消息历史（HTTP 18.5.3），按 seq 游标分页。
     * <p>
     * 用 seq 而非 id 或 offset：seq 是会话内有序且连续的，
     * 翻页时不会因其他会话写入而错位，也不会出现"翻到一半跳页"。
     *
     * @param beforeSeq 取该 seq 之前更早的消息；为空时取最新一页
     */
    public List<MessageVO> listMessages(Long userId, Long conversationId, Long beforeSeq, int limit) {
        PrivateConversation conversation = requireConversationOfUser(userId, conversationId);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        // v1.9：从 MongoDB 按 Seq 游标倒序取一页（走 idx_conv_seq_desc 索引）
        List<PrivateMessageDocument> messages =
                messageRepository.selectBySeqCursor(conversation.getId(), beforeSeq, safeLimit);
        List<MessageVO> list = new ArrayList<>(messages.size());
        for (PrivateMessageDocument message : messages) {
            list.add(toVO(message));
        }
        return list;
    }

    /**
     * 按 seq 区间补洞（PPT P11 的 pullMissing）。
     * <p>
     * 客户端检测到 seq 跳号（收到 N 但本地最新是 M，且 N &gt; M+1）时调用，
     * 服务端返回 (afterSeq, upToSeq] 区间内缺失的消息，按 seq 升序。
     */
    public List<MessageVO> listSeqRange(Long userId, Long conversationId, long afterSeq,
                                        Long upToSeq, int limit) {
        PrivateConversation conversation = requireConversationOfUser(userId, conversationId);
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        // v1.9：MongoDB 按 seq 区间查询 (afterSeq, upToSeq]，升序返回
        List<PrivateMessageDocument> messages = messageRepository.selectSeqRange(
                conversation.getId(), afterSeq, upToSeq, safeLimit);
        List<MessageVO> list = new ArrayList<>(messages.size());
        for (PrivateMessageDocument message : messages) {
            list.add(toVO(message));
        }
        return list;
    }

    // ==================== 已读、送达与补拉 ====================

    /**
     * ★【Q4 状态与多端】标记已读（WebSocket private_read 与 HTTP 18.5.5 共用）。
     *
     * <pre>
     * PPT P12 核心思路：「已读也用游标记录，readSeq 往前推就代表已读，
     *                    未读数 = lastSeq - readSeq」
     *
     * 为什么要用【游标】而不是逐条标记：
     *   用户点开会话 = "我读到了第 N 条"，
     *   用一条 UPDATE ... WHERE seq <= N 就能把整个区间打上已读，
     *   避免消息多时产生 N 次数据库往返（N+1 问题）。
     *
     * 双侧写入（保证数据一致）：
     *   · 会话表：last_read_seq 推进 + 未读数清零 → 会话列表红点消失
     *   · 消息表：seq <= cursor 的 read_at 打上时间戳 → 单条消息显示"已读"
     *
     * 为什么已读游标放服务端而不是客户端：
     *   多端登录时（手机 + 电脑），若游标存本地，两端会各算各的。
     *   存服务端则任一端上报后，另一端重新加载即可拿到一致状态。
     *
     * 已知限制（答辩可主动说明）：
     *   跨端【实时】刷新未做——A 端读完后 B 端不会立即收到通知，
     *   但 B 端重新加载会拿到正确值。数据一致，只是不实时。
     * </pre>
     *
     * @param lastReadSeq 已读到的 seq；为空时取会话内最大 seq
     * @return 实际推进到的 seq
     */
    @Transactional
    public Long markRead(Long userId, Long conversationId, Long lastReadSeq) {
        PrivateConversation conversation = requireConversationOfUser(userId, conversationId);
        boolean isA = userId.equals(conversation.getUserAId());
        long cursor;
        if (lastReadSeq != null && lastReadSeq > 0) {
            cursor = lastReadSeq;
        } else {
            // 未传游标：视为读到会话内最新一条
            cursor = Math.max(messageRepository.selectMaxSeq(conversation.getId()), 1L);
        }

        // 会话摘要（未读数清零、已读游标）仍在 MySQL
        conversationMapper.markRead(conversation.getId(), isA, cursor);
        // v1.9：消息已读时间在 MongoDB 中批量回写（一条 updateMany 处理整个 seq 区间）
        messageRepository.markReadUpToSeq(conversation.getId(), userId, cursor);

        Long peerId = isA ? conversation.getUserBId() : conversation.getUserAId();
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("conversation_id", String.valueOf(conversation.getId()));
        receipt.put("last_read_seq", String.valueOf(cursor));
        receipt.put("reader_id", String.valueOf(userId));

        registerAfterCommit(() -> pushService.pushPacket(peerId,
                com.edu.aitutor.module.privatechat.websocket.WsPacket.of(
                        com.edu.aitutor.module.privatechat.websocket.WsPacket.PRIVATE_READ, receipt)));

        return cursor;
    }

    /**
     * ★【Q2 可靠性】标记已送达（三态中的"已送达"）。
     *
     * <pre>
     * 三态完整链路（PPT P10）：
     *   ① 发送中    —— 客户端乐观回显，此时消息还在本地
     *   ② 已发送    —— 收到 private_ack，服务端已落库
     *   ③ 已送达    —— 收到 private_delivered，★ 对方客户端已收到（本方法负责）
     *   ④ 已读      —— 收到 private_read，对方点开了会话
     *
     * 为什么"已送达"值得单独一级：
     *   "已发送"只证明服务端收到了消息，不代表对方能收到（可能对方一直离线）；
     *   "已送达"才证明消息真的到了对方设备上。
     *   如果只有两级（发送/已读），用户无法判断"对方到底有没有收到"。
     *
     * 为什么送达和已读要用两套独立游标：
     *   送达游标：对方客户端收到即推进，与"看没看"无关；
     *   已读游标：对方点开会话才推进。
     *   两者混用会导致"已送达"被误判成"已读"。
     *
     * 触发方式：
     *   客户端收到 private_recv 后【立即】回 private_delivered，
     *   不等待用户操作——这是"送达"语义的正确实现。
     * </pre>
     */
    @Transactional
    public void markDelivered(Long userId, Long conversationId, Long deliveredSeq) {
        if (deliveredSeq == null || deliveredSeq <= 0) {
            return;
        }
        PrivateConversation conversation = requireConversationOfUser(userId, conversationId);
        boolean isA = userId.equals(conversation.getUserAId());

        // 会话摘要的送达游标仍在 MySQL
        conversationMapper.markDelivered(conversation.getId(), isA, deliveredSeq);
        // v1.9：消息送达时间在 MongoDB 中批量回写
        messageRepository.markDeliveredUpToSeq(conversation.getId(), userId, deliveredSeq);

        // 通知发送方：你发的消息已被对方接收
        Long peerId = isA ? conversation.getUserBId() : conversation.getUserAId();
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("conversation_id", String.valueOf(conversation.getId()));
        receipt.put("delivered_seq", String.valueOf(deliveredSeq));
        receipt.put("receiver_id", String.valueOf(userId));
        registerAfterCommit(() -> pushService.pushPacket(peerId,
                com.edu.aitutor.module.privatechat.websocket.WsPacket.of(
                        com.edu.aitutor.module.privatechat.websocket.WsPacket.PRIVATE_DELIVERED, receipt)));
    }

    /**
     * ★【Q4 状态与多端】上线补拉（WebSocket sync）—— 离线消息的最终兜底。
     *
     * <pre>
     * PPT P12 原话：「一切以『拉』为兜底，『推』只是加速：
     *                实时推送丢了没关系，sync 游标对账保证最终一致。」
     *
     * 为什么这行代码是"离线不丢"的最后一道防线：
     *   消息推送可能失败（对方离线、网络抖动、服务重启），
     *   但消息永远在 MongoDB 里。客户端每次连接都带游标来问"我之后还有什么"，
     *   服务端按游标返回增量——只要数据库不丢，消息就不会丢。
     *
     * 为什么跨会话要用消息 id 而不是 seq：
     *   不同会话的 seq 各自从 1 开始（A 会话 seq=5、B 会话 seq=3），
     *   无法用一个 seq 判断"哪些消息是我漏掉的"。
     *   而消息 id 是全局单调递增的（由 MessageIdGenerator 集中分配），
     *   天然适合跨会话比大小。
     *   → seq 负责【会话内排序】，id 负责【跨会话新旧判断】，两者分工不同。
     *
     * 单次 200 条上限：
     *   离线久了的用户可能有上千条消息，一次全拉会导致响应过大、
     *   前端渲染卡顿。所以分批返回，客户端根据 has_more 继续问。
     * </pre>
     */
    public List<MessageVO> syncOffline(Long userId, Long lastMsgId) {
        long since = lastMsgId == null ? 0L : Math.max(lastMsgId, 0L);
        // v1.9：从 MongoDB 按 receiverId + id 游标查增量（走 idx_receiver_id 索引）
        List<PrivateMessageDocument> messages =
                messageRepository.selectOfflineSinceMsgId(userId, since, SYNC_BATCH_SIZE);
        List<MessageVO> list = new ArrayList<>(messages.size());
        for (PrivateMessageDocument message : messages) {
            list.add(toVO(message));
        }
        return list;
    }

    // ==================== 内部工具 ====================

    /**
     * 校验会话存在且当前用户是参与方，防止越权读取他人会话。
     */
    private PrivateConversation requireConversationOfUser(Long userId, Long conversationId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!userId.equals(conversation.getUserAId()) && !userId.equals(conversation.getUserBId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该会话");
        }
        return conversation;
    }

    /**
     * 实体转 VO，ID 统一转字符串防前端精度丢失。
     * <p>
     * v1.9：入参由 PrivateMessage（MySQL 实体）改为 PrivateMessageDocument（Mongo 文档），
     * 并补上媒体相关字段（mediaSize / mediaDuration / mediaName），
     * 前端据此渲染图片尺寸占位与音频时长。
     */
    private MessageVO toVO(PrivateMessageDocument message) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(asString(message.getId()));
        vo.setConversationId(asString(message.getConversationId()));
        vo.setSeq(message.getSeq());
        vo.setFrom(asString(message.getSenderId()));
        vo.setTo(asString(message.getReceiverId()));
        vo.setContent(message.getContent());
        vo.setMsgType(message.getMsgType());
        vo.setMediaUrl(message.getMediaUrl());
        // v1.9 新增：媒体元数据，供前端展示图片加载占位与音频时长
        vo.setMediaSize(message.getMediaSize());
        vo.setMediaDuration(message.getMediaDuration());
        vo.setMediaName(message.getMediaName());
        vo.setDeliveredAt(message.getDeliveredAt());
        vo.setReadAt(message.getReadAt());
        vo.setCreateTime(message.getCreateTime());
        // 发送者姓名：失败不阻断，前端可回退显示ID
        if (message.getSenderId() != null) {
            try {
                User sender = userMapper.selectById(message.getSenderId());
                if (sender != null) {
                    vo.setFromName(sender.getRealName());
                }
            } catch (Exception e) {
                log.debug("补发送者姓名失败，senderId={}：{}", message.getSenderId(), e.getMessage());
            }
        }
        return vo;
    }

    /**
     * 注册"事务提交后"回调。
     * <p>
     * 无事务上下文（如被直接单元测试调用）时立即执行，保证推送不会因缺少事务而丢失。
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_MAX_LENGTH
                ? content
                : content.substring(0, PREVIEW_MAX_LENGTH);
    }

    /**
     * 判断字符串是否为空白（null 或仅含空白字符）。
     * <p>
     * v1.9 新增：媒体消息允许 content 为空，校验逻辑需要区分
     * "没填"（null/空白）与"填了内容"，因此统一用本方法判断，
     * 不依赖 Spring 的 StringUtils，保持模块内自足。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long asLongOrDefault(Long value) {
        return value == null ? 0L : value;
    }

    private Integer asInt(Object value, int defaultValue) {
        Long parsed = asLong(value);
        return parsed == null ? defaultValue : parsed.intValue();
    }

    private LocalDateTime asDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}