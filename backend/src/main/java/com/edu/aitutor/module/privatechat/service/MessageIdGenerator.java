package com.edu.aitutor.module.privatechat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局消息 ID 生成器。
 *
 * ============================================================================
 * 【为什么需要它】
 * ----------------------------------------------------------------------------
 * 迁移前：MySQL 的 {@code AUTO_INCREMENT} 自动分配主键 id，天然全局单调递增。
 * 迁移后：MongoDB 无自增主键，若用 ObjectId 则无法保证"后写的 id 一定更大"，
 *        而离线补拉（sync）正是靠 {@code id > 游标} 判断跨会话的新旧。
 *
 * 因此必须保留一个【全局单调递增】的 id 分配器。
 *
 * ============================================================================
 * 【实现方案：MySQL 序列表集中分配】
 * ----------------------------------------------------------------------------
 * 用一张单行的序列表，每次 UPDATE 自增并读回：
 *
 *   UPDATE message_id_seq SET current_id = LAST_INSERT_ID(current_id + 1) WHERE id = 1;
 *   SELECT LAST_INSERT_ID();
 *
 * 关键点：
 * <ol>
 *   <li><b>LAST_INSERT_ID(expr)</b>：MySQL 特有语法，把 expr 赋给会话变量并返回，
 *       整条 UPDATE 在行锁保护下原子完成，并发安全；</li>
 *   <li><b>不用 SELECT MAX(id)+1</b>：那是典型的竞态写法，并发下会拿到重复 id；</li>
 *   <li><b>指定 REQUIRES_NEW</b>：id 一旦分配出去就不应因外层事务回滚而重复使用，
 *       否则可能出现"同一 id 被两条不同消息占用"；</li>
 *   <li><b>不入 Redis</b>：Redis 键可能丢失（过期/淘汰），而 id 必须严格不回退，
 *       持久化到 MySQL 才有保障；seq 可以接受 Redis 发号，id 不行。</li>
 * </ol>
 *
 * ============================================================================
 * 【与 seq 的分工（答辩必问）】
 * ----------------------------------------------------------------------------
 * <pre>
 * 字段  作用域    分配方式          用途
 * ────────────────────────────────────────────────────────────────
 * id    全局       MySQL 序列表     跨会话新旧判断、离线补拉游标
 * seq   会话内     Redis INCR       会话内排序、空洞检测、已读/送达游标
 * </pre>
 * 两者职责不重叠：seq 无法跨会话比较（各会话都从 1 开始），
 * id 无法用于会话内排序（跨会话穿插、有空洞）。
 * ============================================================================
 */
@Service
public class MessageIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(MessageIdGenerator.class);

    /** 序列表固定单行，主键恒为 1 */
    private static final long SEQ_ROW_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    public MessageIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分配下一个全局消息 ID。
     * <p>
     * 在 MySQL 行锁保护下原子自增，并发调用不会拿到重复值。
     * 使用 REQUIRES_NEW 保证即使外层业务事务回滚，已分配的 id 也不会被重复使用
     * （宁可留空洞，也不能重复）。
     *
     * @return 全局单调递增的消息 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextId() {
        // LAST_INSERT_ID(expr) 会把 expr 的值记入当前连接会话，并让后续
        // SELECT LAST_INSERT_ID() 读到它；整条语句在行锁内原子完成。
        jdbcTemplate.update(
                "update message_id_seq set current_id = last_insert_id(current_id + 1) where id = ?",
                SEQ_ROW_ID);
        Long value = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (value == null) {
            // 理论上不会发生；兜底抛错，避免把 null 写进 _id
            throw new IllegalStateException("消息ID分配失败：last_insert_id() 返回空");
        }
        log.debug("分配全局消息ID id={}", value);
        return value;
    }
}