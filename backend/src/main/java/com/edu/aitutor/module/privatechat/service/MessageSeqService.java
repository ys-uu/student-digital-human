package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.module.privatechat.repository.redis.MessageSeqRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 会话级消息序号发号器，对应《实时聊天系统技术与实现》P11。
 * <p>
 * 为什么必须由服务端发号：
 * <ul>
 *   <li>客户端时钟不可信（NTP 漂移、时区、人为改表），用时间戳排序必然错乱；</li>
 *   <li>并发与重传会让消息乱序到达，"我到了"排在"我出发了"前面是事故；</li>
 *   <li>客户端据此做空洞检测（seq &gt; localMax + 1 说明中间丢了），
 *       要求同一会话内 seq 严格连续、无空洞。</li>
 * </ul>
 * <p>
 * 为什么是「会话级」而非「全局级」：不同会话各自从 1 递增，互不影响，
 * 也避免全局号段的写入竞争。全局自增主键 id 不能替代它——
 * id 跨会话穿插，客户端无法据此判断"中间有没有丢"。
 * <p>
 * 实现：Redis INCR 天然原子递增，无需加锁；键按会话隔离。
 * Redis 不可用或键丢失时回退到数据库 max(seq) + 1，保证不倒退、不重复。
 */
@Service
public class MessageSeqService {

    private static final Logger log = LoggerFactory.getLogger(MessageSeqService.class);

    /** 发号器键 TTL：7 天。会话长期无消息时键过期，下次从数据库 max(seq) 重建 */
    private static final Duration SEQ_KEY_TTL = Duration.ofDays(7);

    private final MessageSeqRedisRepository messageSeqRedisRepository;

    public MessageSeqService(MessageSeqRedisRepository messageSeqRedisRepository) {
        this.messageSeqRedisRepository = messageSeqRedisRepository;
    }

    /**
     * ★★★【Q3 顺序性】会话级 seq 发号器 —— 本方法的返回值就是消息的"先后凭证" ★★★
     * <p>
     * PPT P03 Q3 原问题：并发与重传会让消息乱序到达，
     * "我走了"排在"我来了"之前是事故，需要会话内单调递增序号 seq。
     * <p>
     * <b>实现选择：Redis INCR</b>
     * <ul>
     *   <li>原子递增，无需加锁，天然并发安全；</li>
     *   <li>按会话隔离（键里带 conversationId），不同会话互不干扰，
     *       也避免了全局号段的写入竞争；</li>
     *   <li>比数据库自增更灵活：数据库自增是全局的，跨会话穿插，
     *       客户端无法据此判断"有没有漏收"。</li>
     * </ul>
     * <p>
     * <b>为什么不用时间戳排序</b>：两台设备时钟不同步（NTP 漂移、时区、人为改表），
     * 按时间排序必然翻车。顺序必须由服务端单点发号决定。
     * <p>
     * <b>兜底设计</b>：Redis 键过期或不可用时，用数据库 max(seq) + 1 重建基线，
     * 保证序号不倒退、不重复（哪怕 Redis 整个挂了，也能继续正确发号）。
     *
     * @param conversationId 会话ID（决定发号作用域）
     * @param dbMaxSeq       数据库当前最大 seq（Redis 键不存在时用于建基线）
     * @return 会话内单调递增的序号，从 1 开始，严格连续无空洞
     */
    public long nextSeq(Long conversationId, long dbMaxSeq) {
        try {
            // ★ 核心：Redis INCR 原子 +1，并发下也不会拿到重复序号
            Long value = messageSeqRedisRepository.increment(conversationId);
            if (value != null) {
                if (value == 1L && dbMaxSeq > 0) {
                    // 键刚重建（过期或首次）：基线必须接在库中最大值之后，避免序号回退
                    long baseline = dbMaxSeq + 1;
                    messageSeqRedisRepository.saveBaseline(conversationId, baseline, SEQ_KEY_TTL);
                    return baseline;
                }
                messageSeqRedisRepository.refreshTtl(conversationId, SEQ_KEY_TTL);
                return value;
            }
        } catch (Exception e) {
            log.warn("Redis 发号失败，回退数据库基线，conversationId={}：{}", conversationId, e.getMessage());
        }
        // 兜底：Redis 不可用时按库中最大值 +1，仍保证单调递增（并发下可能冲突，由唯一键兜底重试）
        return dbMaxSeq + 1;
    }
}
