package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.module.privatechat.repository.MessageCounterMongoRepository;
import org.springframework.stereotype.Service;

/**
 * 全局消息 ID 生成器。
 * <p>
 * MongoDB 没有关系型数据库的 AUTO_INCREMENT，因此由 message_counter 集合中的
 * currentId 原子自增生成全局单调递增的 Long 型消息 ID。
 * <p>
 * id 用于跨会话新旧判断和离线补拉；会话内排序仍由 Redis 分配的 seq 负责。
 */
@Service
public class MessageIdGenerator {

    private final MessageCounterMongoRepository counterRepository;

    public MessageIdGenerator(MessageCounterMongoRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * 分配下一个全局消息 ID。
     *
     * @return 全局单调递增的消息 ID
     */
    public long nextId() {
        return counterRepository.nextValue();
    }
}
