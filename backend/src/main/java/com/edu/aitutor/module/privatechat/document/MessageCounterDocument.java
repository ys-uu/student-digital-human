package com.edu.aitutor.module.privatechat.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 消息 ID 计数器文档。
 * <p>
 * 每个业务计数器对应一个文档，currentId 保存当前已分配的最大 ID。
 */
@Data
@Document(collection = "message_counter")
public class MessageCounterDocument {

    /** 计数器业务标识，例如 private_message */
    @Id
    @Field("_id")
    private String id;

    /** 计数器名称 */
    @Field("name")
    private String name;

    /** 当前已分配的最大 ID */
    @Field("currentId")
    private Long currentId;
}
