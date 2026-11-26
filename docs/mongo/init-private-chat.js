// ============================================================
// MongoDB 初始化：私信消息与消息 ID 计数器
// 数据库：ai_tutor
// 说明：
//   1. private_message 存储私信消息文档；
//   2. message_counter 存储全局递增消息 ID；
//   3. 本脚本可重复执行，已存在的集合和索引不会重复创建。
// ============================================================

db = db.getSiblingDB('ai_tutor');

// ------------------------------------------------------------
// 1. 私信消息集合
// ------------------------------------------------------------
if (!db.getCollectionNames().includes('private_message')) {
  db.createCollection('private_message');
}

// 客户端幂等：同一发送者 + 同一 clientMsgId 只能有一条消息
db.private_message.createIndex(
  { senderId: 1, clientMsgId: 1 },
  { name: 'uk_client', unique: true }
);

// 会话内顺序：同一会话 + 同一 seq 只能有一条消息
db.private_message.createIndex(
  { conversationId: 1, seq: 1 },
  { name: 'uk_conv_seq', unique: true }
);

// 离线补拉：按接收者和全局消息 ID 游标查询
db.private_message.createIndex(
  { receiverId: 1, _id: 1 },
  { name: 'idx_receiver_id' }
);

// ------------------------------------------------------------
// 2. 消息 ID 计数器集合
// ------------------------------------------------------------
if (!db.getCollectionNames().includes('message_counter')) {
  db.createCollection('message_counter');
}

const counterId = 'private_message';
if (!db.message_counter.findOne({ _id: counterId })) {
  db.message_counter.insertOne({
    _id: counterId,
    name: '私信消息ID计数器',
    currentId: 0
  });
}

// ------------------------------------------------------------
// 3. 验证输出
// ------------------------------------------------------------
print('databases/collections initialized: ai_tutor');
printjson(db.private_message.getIndexes());
printjson(db.message_counter.findOne({ _id: counterId }));
