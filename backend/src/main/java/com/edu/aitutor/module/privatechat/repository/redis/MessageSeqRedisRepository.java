package com.edu.aitutor.module.privatechat.repository.redis;

import com.edu.aitutor.common.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 会话级消息序号发号器 Redis 操作：key 为 {@link RedisKeys#MESSAGE_SEQ}，
 * 基于 INCR 原子递增，发号编排与数据库兜底由 MessageSeqService 决定。
 */
@Repository
public class MessageSeqRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public MessageSeqRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子递增发号器并返回递增后的值，key 不存在时从 1 开始。
     */
    public Long increment(Long conversationId) {
        return redisTemplate.opsForValue().increment(seqKey(conversationId));
    }

    /**
     * 键刚重建时写入数据库基线值，保证序号接在库中最大值之后。
     */
    public void saveBaseline(Long conversationId, long baseline, Duration ttl) {
        redisTemplate.opsForValue().set(seqKey(conversationId), String.valueOf(baseline));
        redisTemplate.expire(seqKey(conversationId), ttl);
    }

    /**
     * 续期发号器 TTL。
     */
    public void refreshTtl(Long conversationId, Duration ttl) {
        redisTemplate.expire(seqKey(conversationId), ttl);
    }

    private String seqKey(Long conversationId) {
        return String.format(RedisKeys.MESSAGE_SEQ, conversationId);
    }
}
