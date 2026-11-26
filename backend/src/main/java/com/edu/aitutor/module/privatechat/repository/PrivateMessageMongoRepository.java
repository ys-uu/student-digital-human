package com.edu.aitutor.module.privatechat.repository;

import com.edu.aitutor.module.privatechat.document.PrivateMessageDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 私信消息仓储（MongoDB）。
 * <p>
 * 统一封装 private_message 集合的写入、游标分页、区间查询和状态回写，
 * 业务层不直接拼接 MongoTemplate 查询。
 * <p>
 * 本模块存在游标分页、区间查询、批量更新等组合条件，因此使用 MongoTemplate
 * 而不是只适合简单 CRUD 的 MongoRepository。
 * <p>
 * 全局消息 ID 由 {@link MessageIdGenerator} 基于 message_counter 集合原子自增分配，
 * 保证 id 全局单调递增，以支持离线补拉按 id 判断新旧。
 */
@Repository
public class PrivateMessageMongoRepository {

    private final MongoTemplate mongo;

    public PrivateMessageMongoRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ==================== 写入 ====================

    /**
     * 插入一条消息。
     * <p>
     * 依赖集合上的唯一索引：
     * <ul>
     *   <li>{@code uk_client(senderId, clientMsgId)}：客户端重发时抛 DuplicateKeyException，
     *       业务层捕获后回查已有记录（Q2 去重兜底）；</li>
     *   <li>{@code uk_conv_seq(conversationId, seq)}：并发抢到同一 seq 时同样抛异常。</li>
     * </ul>
     *
     * @return 插入后的文档（含应用层分配的 id）
     */
    public PrivateMessageDocument insert(PrivateMessageDocument message) {
        return mongo.insert(message);
    }

    // ==================== 查询：单条 ====================

    /** 按主键查单条消息 */
    public PrivateMessageDocument selectById(Long id) {
        return mongo.findById(id, PrivateMessageDocument.class);
    }

    /**
     * 按客户端幂等键回查已有消息 —— 幂等去重的核心查询。
     * <p>
     * 走 uk_client 索引，命中即说明这条消息之前已经落库过，
     * 直接返回原记录，不再重复插入、也不消耗新的 seq。
     */
    public PrivateMessageDocument selectByClientId(Long senderId, String clientMsgId) {
        Query query = new Query(Criteria.where("senderId").is(senderId)
                .and("clientMsgId").is(clientMsgId));
        return mongo.findOne(query, PrivateMessageDocument.class);
    }

    // ==================== 查询：聚合 ====================

    /**
     * 取某会话当前最大 seq —— Redis 发号器键丢失时用于重建基线。
     *
     * @return 最大 seq；会话无消息时返回 0
     */
    public long selectMaxSeq(Long conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId))
                .with(Sort.by(Sort.Direction.DESC, "seq"))
                .limit(1);
        query.fields().include("seq");
        PrivateMessageDocument latest = mongo.findOne(query, PrivateMessageDocument.class);
        return latest == null || latest.getSeq() == null ? 0L : latest.getSeq();
    }

    /**
     * 会话内按 seq 游标分页（历史消息）。
     * <p>
     * beforeSeq 为空时取最新一页；否则取比它更早的一页。
     * 按 seq 倒序返回，前端渲染时再翻转成时间正序。
     * <p>
     * 走 idx_conv_seq_desc 索引，无需全表扫描。
     */
    public List<PrivateMessageDocument> selectBySeqCursor(Long conversationId, Long beforeSeq, int size) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        if (beforeSeq != null) {
            criteria = criteria.and("seq").lt(beforeSeq);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "seq"))
                .limit(size);
        return mongo.find(query, PrivateMessageDocument.class);
    }

    /**
     * 按 seq 区间拉取（补洞专用）：返回 (afterSeq, upToSeq] 内的消息，按 seq 升序。
     * <p>
     * 客户端发现 seq 跳号时调用，把中间缺失的消息补齐。
     * upToSeq 为空表示不设上界。
     */
    public List<PrivateMessageDocument> selectSeqRange(Long conversationId, long afterSeq,
                                                       Long upToSeq, int size) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId)
                .and("seq").gt(afterSeq);
        if (upToSeq != null) {
            criteria = criteria.and("seq").lte(upToSeq);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "seq"))
                .limit(size);
        return mongo.find(query, PrivateMessageDocument.class);
    }

    /**
     * 上线补拉：查接收方所有会话中 id 大于游标的消息，按 id 升序。
     * <p>
     * 为什么按 id 而不是 seq：不同会话的 seq 各自从 1 开始，无法用一个 seq
     * 判断"跨会话的新旧"；id 全局单调递增，天然适合做跨会话游标。
     * seq 仍随消息返回，供前端做会话内排序。
     * <p>
     * 走 idx_receiver_id(receiverId, id) 索引。
     */
    public List<PrivateMessageDocument> selectOfflineSinceMsgId(Long userId, long afterMsgId, int size) {
        Query query = new Query(Criteria.where("receiverId").is(userId)
                .and("id").gt(afterMsgId))
                .with(Sort.by(Sort.Direction.ASC, "id"))
                .limit(size);
        return mongo.find(query, PrivateMessageDocument.class);
    }

    // ==================== 更新：状态回写 ====================

    /**
     * 单条回写送达时间：仅当尚未设置时更新，重复回执不改变原值。
     */
    public void markDelivered(Long id) {
        Query query = new Query(Criteria.where("id").is(id).and("deliveredAt").isNull());
        Update update = new Update().set("deliveredAt", LocalDateTime.now());
        mongo.updateFirst(query, update, PrivateMessageDocument.class);
    }

    /**
     * 批量回写送达状态（按 seq 区间）：把该会话中接收方 seq 未超过游标的消息标记送达。
     * <p>
     * 一条 updateMany 处理整个区间，避免逐条更新带来的 N 次往返。
     */
    public void markDeliveredUpToSeq(Long conversationId, Long userId, Long deliveredSeq) {
        if (deliveredSeq == null || deliveredSeq <= 0) {
            return;
        }
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("receiverId").is(userId)
                .and("deliveredAt").isNull()
                .and("seq").lte(deliveredSeq));
        Update update = new Update().set("deliveredAt", LocalDateTime.now());
        mongo.updateMulti(query, update, PrivateMessageDocument.class);
    }

    /**
     * 批量标记已读：把该会话中接收方 seq 未超过游标的消息全部置为已读。
     * <p>
     * 按 seq 而非 id 比较：seq 是会话内有序的，id 是全局的，
     * 用 id 判断"读到哪"在跨会话时没有意义。
     */
    public void markReadUpToSeq(Long conversationId, Long userId, Long lastReadSeq) {
        if (lastReadSeq == null || lastReadSeq <= 0) {
            return;
        }
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("receiverId").is(userId)
                .and("readAt").isNull()
                .and("seq").lte(lastReadSeq));
        Update update = new Update().set("readAt", LocalDateTime.now());
        mongo.updateMulti(query, update, PrivateMessageDocument.class);
    }

    /**
     * 统计某会话内接收方的未读条数（备用，可用于校验会话摘要中的未读数）。
     */
    public long countUnread(Long conversationId, Long userId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("receiverId").is(userId)
                .and("readAt").isNull());
        return mongo.count(query, PrivateMessageDocument.class);
    }

    /**
     * 删除某条消息（仅用于测试数据清理，业务上不提供删除接口）。
     */
    public void deleteById(Long id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongo.remove(query, PrivateMessageDocument.class);
    }
}