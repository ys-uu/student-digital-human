package com.edu.aitutor.module.privatechat.mapper;

import com.edu.aitutor.module.privatechat.entity.PrivateMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 私信消息 Mapper，对应表37 private_message。
 * <p>
 * 序号与游标体系（见《实时聊天系统技术与实现》P11/P12）：
 * <ul>
 *   <li><b>seq</b>：会话内单调递增，从 1 开始连续，由 {@code MessageSeqService} 发号。
 *       客户端据此做顺序保证与空洞检测；</li>
 *   <li><b>id</b>：全局自增主键，仅用于唯一定位单条消息，<b>不参与会话内排序</b>——</li>
 * </ul>
 * 所有增量拉取一律按 seq 游标，不使用 id 或 offset：
 * 前者跨会话穿插会漏消息，后者在持续写入时翻页会错位。
 * <p>
 * 注意：本类中 {@code <} 与 {@code <=} 均写在非 &lt;script&gt; 的普通字符串 SQL 里，
 * 必须使用原生符号，不能写成 XML 转义形式（否则数据库会把 lt 当列名）。
 */
@Mapper
public interface PrivateMessageMapper {

    String COLUMNS = "id, conversation_id, seq, sender_id, receiver_id, content, msg_type, media_url, " +
            "client_msg_id, delivered_at, read_at, recall_status, report_status, create_time";

    /**
     * 取某会话当前最大 seq（Redis 发号器键丢失时用于重建基线）。
     */
    @Select("select coalesce(max(seq), 0) from private_message where conversation_id = #{conversationId}")
    long selectMaxSeq(@Param("conversationId") Long conversationId);

    /**
     * 幂等写入：命中 uk_client(sender_id, client_msg_id) 时返回 0，不抛异常。
     * <p>
     * 同时受 uk_conv_seq(conversation_id, seq) 保护：极端并发下两个请求拿到同一 seq 时，
     * 其中一个抛唯一键冲突，由业务层捕获后重新发号重试。
     */
    @Insert("insert into private_message " +
            "(conversation_id, seq, sender_id, receiver_id, content, msg_type, media_url, client_msg_id) " +
            "values (#{conversationId}, #{seq}, #{senderId}, #{receiverId}, #{content}, #{msgType}, " +
            "#{mediaUrl}, #{clientMsgId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PrivateMessage message);

    /**
     * 幂等写入（忽略重复）。
     * <p>
     * 使用 INSERT IGNORE 需注意：它会吞掉**所有**错误而非仅唯一键冲突，
     * 因此业务层在用本方法前必须先确认 seq 分配成功；并发冲突路径改用
     * {@link #insertRetry} 的方式单独处理。
     */
    @Insert("insert ignore into private_message " +
            "(conversation_id, seq, sender_id, receiver_id, content, msg_type, media_url, client_msg_id) " +
            "values (#{conversationId}, #{seq}, #{senderId}, #{receiverId}, #{content}, #{msgType}, " +
            "#{mediaUrl}, #{clientMsgId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(PrivateMessage message);

    /**
     * 按客户端幂等键回查已有消息，用于重复提交时返回首次落库的结果。
     */
    @Select("select " + COLUMNS + " from private_message " +
            "where sender_id = #{senderId} and client_msg_id = #{clientMsgId}")
    PrivateMessage selectByClientId(@Param("senderId") Long senderId,
                                    @Param("clientMsgId") String clientMsgId);

    @Select("select " + COLUMNS + " from private_message where id = #{id}")
    PrivateMessage selectById(@Param("id") Long id);

    /**
     * 会话内按 seq 游标分页：beforeSeq 为空时取最新一页，否则取更早的一页。
     * 按 seq 倒序返回，前端渲染时再反转成时间正序。
     */
    @Select("<script>" +
            "select " + COLUMNS + " from private_message " +
            "where conversation_id = #{conversationId} " +
            "<if test='beforeSeq != null'> and seq &lt; #{beforeSeq} </if>" +
            "order by seq desc limit #{size}" +
            "</script>")
    List<PrivateMessage> selectBySeqCursor(@Param("conversationId") Long conversationId,
                                           @Param("beforeSeq") Long beforeSeq,
                                           @Param("size") int size);

    /**
     * 按 seq 区间拉取（补洞专用）：返回 (afterSeq, upToSeq] 区间内的消息，按 seq 升序。
     * <p>
     * 客户端发现 seq 空洞时调用此查询，把中间缺失的消息补齐。
     * upToSeq 为空表示不设上界。
     */
    @Select("<script>" +
            "select " + COLUMNS + " from private_message " +
            "where conversation_id = #{conversationId} and seq &gt; #{afterSeq} " +
            "<if test='upToSeq != null'> and seq &lt;= #{upToSeq} </if>" +
            "order by seq asc limit #{size}" +
            "</script>")
    List<PrivateMessage> selectSeqRange(@Param("conversationId") Long conversationId,
                                        @Param("afterSeq") long afterSeq,
                                        @Param("upToSeq") Long upToSeq,
                                        @Param("size") int size);

    /**
     * 上线补拉：查接收方所有会话中 seq 大于游标的离线消息。
     * <p>
     * 注意跨会话场景：不同会话的 seq 各自从 1 开始，因此这里不能只用 seq 比较，
     * 必须限定在已知会话内。此处按消息 id 游标（全局自增，跨会话单调）作为
     * 「跨会话的新旧」判据，seq 则用于会话内排序——两者分工不同。
     */
    @Select("select " + COLUMNS + " from private_message " +
            "where receiver_id = #{userId} and id &gt; #{afterMsgId} " +
            "order by id asc limit #{size}")
    List<PrivateMessage> selectOfflineSinceMsgId(@Param("userId") Long userId,
                                                 @Param("afterMsgId") long afterMsgId,
                                                 @Param("size") int size);

    /**
     * 回写送达时间：仅当尚未设置时更新，重复回执不改变原值。
     */
    @Update("update private_message set delivered_at = now() " +
            "where id = #{id} and delivered_at is null")
    int markDelivered(@Param("id") Long id);

    /**
     * 批量已读：一条 UPDATE 把会话内接收方 seq 未读消息全部置为已读。
     * <p>
     * 按 seq 而非 id 比较：seq 是会话内有序的，id 是全局的，
     * 用 id 判断「读到哪」在跨会话时没有意义。
     */
    @Update("update private_message set read_at = now() " +
            "where conversation_id = #{conversationId} " +
            "and receiver_id = #{userId} and read_at is null and seq <= #{lastReadSeq}")
    int markReadUpToSeq(@Param("conversationId") Long conversationId,
                        @Param("userId") Long userId,
                        @Param("lastReadSeq") Long lastReadSeq);

    /**
     * 批量回写送达状态（按 seq 区间）：在线推送成功后，把接收方已收悉的消息标记送达。
     */
    @Update("update private_message set delivered_at = now() " +
            "where conversation_id = #{conversationId} " +
            "and receiver_id = #{userId} and delivered_at is null and seq <= #{deliveredSeq}")
    int markDeliveredUpToSeq(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId,
                             @Param("deliveredSeq") Long deliveredSeq);
}