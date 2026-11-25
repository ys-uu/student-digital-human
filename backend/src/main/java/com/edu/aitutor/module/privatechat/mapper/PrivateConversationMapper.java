package com.edu.aitutor.module.privatechat.mapper;

import com.edu.aitutor.module.privatechat.entity.PrivateConversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 私信会话 Mapper，对应表36 private_conversation。
 * <p>
 * 查询与更新均围绕「A/B 归一化」展开：调用方需保证传入的 a 与 b 已按 min/max 排好序。
 */
@Mapper
public interface PrivateConversationMapper {

    String COLUMNS = "id, conversation_no, user_a_id, user_b_id, last_message_id, last_message_at, " +
            "last_message_preview, a_unread_count, b_unread_count, a_last_read_id, b_last_read_id, " +
            "a_delivered_seq, b_delivered_seq, " +
            "create_time, update_time, is_deleted";

    /**
     * 按归一化后的一对用户查询会话，a 必须小于 b。
     */
    @Select("select " + COLUMNS + " from private_conversation " +
            "where user_a_id = #{a} and user_b_id = #{b} and is_deleted = 0")
    PrivateConversation selectByPair(@Param("a") Long a, @Param("b") Long b);

    /**
     * 按主键查询会话。
     */
    @Select("select " + COLUMNS + " from private_conversation where id = #{id} and is_deleted = 0")
    PrivateConversation selectById(@Param("id") Long id);

    /**
     * 新建会话，回填主键。
     * 并发下若同一对用户同时建会话，uk_pair 会抛唯一键冲突，由业务层捕获后重查，保证最终只有一条。
     */
    @Insert("insert into private_conversation (conversation_no, user_a_id, user_b_id) " +
            "values (#{conversationNo}, #{userAId}, #{userBId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PrivateConversation conversation);

    /**
     * 发送消息后更新会话摘要，并给接收方未读数 +1。
     * <p>
     * 用 receiverIsA 标识接收方是否为 user_a：一条 SQL 同时处理摘要与未读，
     * 避免"先查会话再算未读字段"的额外往返，也避免并发覆盖。
     */
    @Update("update private_conversation set " +
            "last_message_id = #{msgId}, " +
            "last_message_at = #{sendAt}, " +
            "last_message_preview = #{preview}, " +
            "a_unread_count = a_unread_count + if(#{receiverIsA}, 1, 0), " +
            "b_unread_count = b_unread_count + if(#{receiverIsA}, 0, 1) " +
            "where id = #{id}")
    int updateAfterSend(@Param("id") Long id,
                        @Param("msgId") Long msgId,
                        @Param("sendAt") LocalDateTime sendAt,
                        @Param("preview") String preview,
                        @Param("receiverIsA") boolean receiverIsA);

    /**
     * 当前用户的会话列表，按最后消息时间倒序。
     * <p>
     * 用 case 把「我是 a 还是 b」折算成统一的 peer_id / unread，前端无需再判断方向。
     * <p>
     * 注意未读数的方向：a_unread_count 表示「A 的未读」，b_unread_count 表示「B 的未读」。
     * 因此「我是 A」时应取 a_unread_count，反之取 b_unread_count——
     * 与 peer_id 的取值方向正好相反（我是 A 则对方是 B）。
     */
    @Select("select c.id as conversation_id, " +
            "case when c.user_a_id = #{userId} then c.user_b_id else c.user_a_id end as peer_id, " +
            "case when c.user_a_id = #{userId} then c.a_unread_count else c.b_unread_count end as unread_count, " +
            "c.last_message_preview, c.last_message_at, c.last_message_id, " +
            "c.create_time " +
            "from private_conversation c " +
            "where c.is_deleted = 0 and (c.user_a_id = #{userId} or c.user_b_id = #{userId}) " +
            "order by c.last_message_at desc, c.id desc " +
            "limit #{offset}, #{size}")
    List<Map<String, Object>> selectMyConversations(@Param("userId") Long userId,
                                                    @Param("offset") int offset,
                                                    @Param("size") int size);

    /**
     * 当前用户的会话总数，与 selectMyConversations 的过滤条件保持一致。
     */
    @Select("select count(*) from private_conversation " +
            "where is_deleted = 0 and (user_a_id = #{userId} or user_b_id = #{userId})")
    long countMyConversations(@Param("userId") Long userId);

    /**
     * 标记已读：把接收方未读数清零，并推进其已读游标。
     * 只允许推进不后退，避免乱序到达的旧回执把游标改小。
     */
    @Update("update private_conversation set " +
            "a_unread_count = if(#{isA}, 0, a_unread_count), " +
            "a_last_read_id = if(#{isA}, greatest(a_last_read_id, #{lastReadId}), a_last_read_id), " +
            "b_unread_count = if(#{isA}, b_unread_count, 0), " +
            "b_last_read_id = if(#{isA}, b_last_read_id, greatest(b_last_read_id, #{lastReadId})) " +
            "where id = #{id}")
    int markRead(@Param("id") Long id,
                 @Param("isA") boolean isA,
                 @Param("lastReadId") Long lastReadId);

    /**
     * 标记已送达：把某方的「已送达到的 seq」推进到指定值，只进不退。
     * <p>
     * 用于三态消息中的"已送达"：对端在线收到推送后回执 delivered，
     * 发送方据此把状态从"发送中/已发送"升级为"已送达"。
     * 区别于 markRead：送达不代表看过，已读游标另行推进。
     */
    @Update("update private_conversation set " +
            "a_delivered_seq = if(#{isA}, greatest(a_delivered_seq, #{deliveredSeq}), a_delivered_seq), " +
            "b_delivered_seq = if(#{isA}, b_delivered_seq, greatest(b_delivered_seq, #{deliveredSeq})) " +
            "where id = #{id}")
    int markDelivered(@Param("id") Long id,
                      @Param("isA") boolean isA,
                      @Param("deliveredSeq") Long deliveredSeq);
}