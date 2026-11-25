package com.edu.aitutor.module.privatechat.migration;

import com.edu.aitutor.module.privatechat.document.PrivateMessageDocument;
import com.edu.aitutor.module.privatechat.repository.PrivateMessageMongoRepository;
import com.edu.aitutor.module.privatechat.service.MessageIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * MySQL → MongoDB 消息迁移器（v1.9）。
 *
 * ============================================================================
 * 【作用】
 * ----------------------------------------------------------------------------
 * 把 MySQL 旧表 private_message 中的存量消息，一次性搬到 MongoDB 集合，
 * 保证升级后历史聊天记录不丢。
 *
 * ============================================================================
 * 【为什么做成可开关的启动任务】
 * ----------------------------------------------------------------------------
 * 通过 {@code privatechat.migration.enabled=true} 开启，默认关闭。
 * 理由：
 *   · 迁移只应执行一次，不能每次启动都跑（虽然幂等，但会浪费启动时间）；
 *   · 生产环境通常由运维在维护窗口手动触发，而不是应用自动执行；
 *   · 迁移出问题时可以随时关闭，不影响主流程启动。
 *
 * ============================================================================
 * 【幂等设计（可重复执行）】
 * ----------------------------------------------------------------------------
 * 判据是 (senderId, clientMsgId) —— 也就是 uk_client 唯一索引。
 * 每条消息迁移前先查 Mongo 是否已存在，存在则跳过。
 * 因此中途失败后再次执行不会产生重复数据。
 *
 * ============================================================================
 * 【id 保持不变】
 * ----------------------------------------------------------------------------
 * 迁移时沿用原 MySQL 的自增 id，而不是重新分配。
 * 原因：客户端的 lastMessageId 游标指向这些 id，
 *       若改变，老用户下次上线 sync 会因 id 变小而漏拉历史消息。
 * 迁移完成后由 MessageIdGenerator 从 max(id) 继续发号。
 * ============================================================================
 */
@Component
@Order(100)  // 在其他 Runner 之后执行，确保数据源与 Mongo 都已就绪
@ConditionalOnProperty(name = "privatechat.migration.enabled", havingValue = "true")
public class PrivateMessageMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivateMessageMigrationRunner.class);

    /** 分批迁移的批大小，避免一次性把大量数据读进内存 */
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final PrivateMessageMongoRepository mongoRepository;
    private final MessageIdGenerator idGenerator;

    public PrivateMessageMigrationRunner(JdbcTemplate jdbcTemplate,
                                         PrivateMessageMongoRepository mongoRepository,
                                         MessageIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoRepository = mongoRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== 开始迁移 MySQL 消息到 MongoDB ==========");
        long started = System.currentTimeMillis();

        // ① 先确认旧表是否存在，不存在则无需迁移（全新环境）
        if (!legacyTableExists()) {
            log.info("旧表 private_message 不存在，跳过迁移（可能是全新环境）");
            return;
        }

        long total = countLegacy();
        log.info("旧表共有 {} 条消息待检查", total);
        if (total == 0) {
            log.info("旧表为空，无需迁移");
            return;
        }

        long lastId = 0L;
        int migrated = 0;
        int skipped = 0;

        // ② 按 id 升序分批读取，每批处理完再读下一批
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, conversation_id, seq, sender_id, receiver_id, content, msg_type, " +
                    "media_url, client_msg_id, delivered_at, read_at, recall_status, report_status, create_time " +
                    "from private_message where id > ? order by id asc limit ?",
                    lastId, BATCH_SIZE);

            if (rows.isEmpty()) {
                break;
            }

            for (Map<String, Object> row : rows) {
                Long id = asLong(row.get("id"));
                if (id == null) {
                    continue;
                }
                lastId = id;

                // ③ 幂等判断：已迁移过就跳过
                PrivateMessageDocument existing = mongoRepository.selectByClientId(
                        asLong(row.get("sender_id")), asString(row.get("client_msg_id")));
                if (existing != null) {
                    skipped++;
                    continue;
                }

                try {
                    mongoRepository.insert(toDocument(row));
                    migrated++;
                } catch (Exception e) {
                    // 单条失败不阻断整体迁移，记录后继续，便于事后人工核查
                    log.warn("迁移单条消息失败 id={}：{}", id, e.getMessage());
                }
            }
            log.info("已处理到 id={}，本批 {} 条（累计迁移 {}，跳过 {}）",
                    lastId, rows.size(), migrated, skipped);
        }

        // ④ 把序列推进到旧表最大值，保证后续新消息 id 不与历史冲突
        // 注意用包装类型 Long 接收：queryForObject 指定 Long.class 时若用基本类型 long，
        // 一旦返回 null 会直接拆箱抛 NullPointerException。
        Long maxIdRow = jdbcTemplate.queryForObject(
                "select coalesce(max(id), 0) from private_message", Long.class);
        long maxId = maxIdRow == null ? 0L : maxIdRow;
        jdbcTemplate.update(
                "update message_id_seq set current_id = greatest(current_id, ?) where id = 1",
                maxId);

        long cost = System.currentTimeMillis() - started;
        log.info("========== 迁移完成：迁移 {} 条，跳过 {} 条，耗时 {} ms，序列已推进到 {} ==========",
                migrated, skipped, cost, maxId);
    }

    /** 旧表是否存在 */
    private boolean legacyTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables " +
                "where table_schema = database() and table_name = 'private_message'",
                Integer.class);
        return count != null && count > 0;
    }

    /** 旧表总条数 */
    private long countLegacy() {
        Long count = jdbcTemplate.queryForObject("select count(*) from private_message", Long.class);
        return count == null ? 0L : count;
    }

    /** MySQL 行 → MongoDB 文档 */
    private PrivateMessageDocument toDocument(Map<String, Object> row) {
        PrivateMessageDocument doc = new PrivateMessageDocument();
        // ★ 沿用原 id，保证客户端游标不失效
        doc.setId(asLong(row.get("id")));
        doc.setConversationId(asLong(row.get("conversation_id")));
        doc.setSeq(asLong(row.get("seq")));
        doc.setSenderId(asLong(row.get("sender_id")));
        doc.setReceiverId(asLong(row.get("receiver_id")));
        doc.setContent(asString(row.get("content")));
        doc.setMsgType(asInt(row.get("msg_type")));
        doc.setMediaUrl(asString(row.get("media_url")));
        doc.setClientMsgId(asString(row.get("client_msg_id")));
        doc.setDeliveredAt(asDateTime(row.get("delivered_at")));
        doc.setReadAt(asDateTime(row.get("read_at")));
        doc.setRecallStatus(asInt(row.get("recall_status")));
        doc.setReportStatus(asInt(row.get("report_status")));
        doc.setCreateTime(asDateTime(row.get("create_time")));
        // 旧表没有媒体元数据字段，迁移后为空；前端按 msgType 兼容处理
        return doc;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer asInt(Object value) {
        Long l = asLong(value);
        return l == null ? null : l.intValue();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private java.time.LocalDateTime asDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime dt) {
            return dt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}