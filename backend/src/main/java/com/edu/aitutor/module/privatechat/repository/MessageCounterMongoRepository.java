package com.edu.aitutor.module.privatechat.repository;

import com.edu.aitutor.module.privatechat.document.MessageCounterDocument;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * 消息计数器仓储，基于 MongoDB 原子操作生成全局递增 ID。
 */
@Repository
public class MessageCounterMongoRepository {

    /** 私信消息计数器标识 */
    private static final String PRIVATE_MESSAGE_COUNTER = "private_message";

    private final MongoTemplate mongo;

    public MessageCounterMongoRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * 原子获取下一个消息 ID。
     * <p>
     * findAndModify 配合 upsert 保证并发下只更新一个文档，且每次 currentId 增加 1。
     *
     * @return 新分配的消息 ID
     */
    public long nextValue() {
        Query query = Query.query(Criteria.where("_id").is(PRIVATE_MESSAGE_COUNTER));
        Update update = new Update()
                .inc("currentId", 1L)
                .setOnInsert("name", "私信消息ID计数器");

        MessageCounterDocument counter = mongo.findAndModify(
                query,
                update,
                new FindAndModifyOptions().upsert(true).returnNew(true),
                MessageCounterDocument.class
        );

        if (counter == null || counter.getCurrentId() == null) {
            throw new IllegalStateException("消息ID计数器自增失败：未返回 currentId");
        }
        return counter.getCurrentId();
    }
}
