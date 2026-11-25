# PPT P03 四问 · 代码位置索引

> 对应《实时聊天系统技术与实现》P03「技术逻辑拆解：聊天 App 到底在解什么问题」
> 代码中已用 `★【Q1】`、`★【Q2】` 等标记，可在 IDEA 中搜索 `★【Q` 快速定位所有关键点。

---

## Q1 实时性 —— 对方怎么"立刻"收到？

**PPT 原话**：HTTP 请求-响应是一问一答，服务端无法主动找客户端；必须建立一条服务端可以主动推数据的通道。

| 层次 | 文件 | 关键位置 | 说明 |
|---|---|---|---|
| 通道注册 | `framework/config/WebSocketConfig.java` | `registerWebSocketHandlers()` L45 | ★ 注册 `/ws/private` |
| 连接建立 | `module/privatechat/websocket/PrivateWebSocketHandler.java` | `afterConnectionEstablished()` L52 | 连接建立回调 |
| 报文分发 | 同上 | `handleTextMessage()` L70 | 按 type 路由 |
| **主动推送出口** | 同上 | `send()` | ★ 所有下行报文的唯一出口 |
| 推送实现 | `service/PrivatePushService.java` | `pushMessage()` L59 | 查在线表 → 推本机 → 广播其他实例 |
| 前端建连 | `composables/usePrivateSocket.js` | `connect()` L127 | `new WebSocket(...)` |
| 前端接收 | 同上 | `ws.onopen` / `ws.onmessage` | 服务端随时推过来 |

**技术上用了什么**：WebSocket 全双工长连接（Spring `TextWebSocketHandler` + 浏览器原生 API）

---

## Q2 可靠性 —— 消息会不会丢？

**PPT 原话**：网络随时断、进程随时崩。没有确认机制的消息等于没发。需要 ACK 确认 + 超时重发 + 去重兜底。

| 机制 | 文件 | 关键位置 | 说明 |
|---|---|---|---|
| **① ACK 确认** | `websocket/PrivateWebSocketHandler.java` | `handleSendMessage()` L209 | ★ `send(PRIVATE_ACK, ack)` |
| **② 超时重发** | `composables/usePrivateSocket.js` | `sendWithRetry()` | ★ 3s 未收 ACK 重发，最多 3 次 |
| 重发参数 | 同上 | `ACK_TIMEOUT` / `MAX_RETRY` L55-56 | 3000ms / 3 次 |
| 停止重发 | 同上 | `clearPending()` | 收到 ACK 后清除定时器 |
| **③ 去重兜底** | `mapper/PrivateMessageMapper.java` | `insertIgnore()` L65 | ★ `uk_client(sender_id, client_msg_id)` |
| 重复回查 | 同上 | `selectByClientId()` L72 | 命中唯一键时返回原记录 |
| 业务层判重 | `service/PrivateMessageService.java` | `send()` 步骤③ | 幂等前置判断 |

**技术上用了什么**：JSON 报文 + MySQL 唯一索引 + 客户端定时器

**PPT P10 强调**：重发与去重永远是一对，单写一个必出事 —— **我们两个都写了**。

---

## Q3 顺序性 —— 先说的怎么排在后面？

**PPT 原话**：并发与重传会让消息乱序到达。"我走了"排在"我来了"之前是事故。需要会话内单调递增序号 seq。

| 环节 | 文件 | 关键位置 | 说明 |
|---|---|---|---|
| **服务端发号** | `service/MessageSeqService.java` | `nextSeq()` L50 | ★ Redis INCR，会话级 |
| 发号键 | 同上 | `SEQ_KEY_PREFIX` L32 | `ai_tutor:seq:conv:{id}` |
| 兜底重建 | 同上 | `nextSeq()` 内部 | 键丢失按 `max(seq)+1` 重建 |
| 调用发号 | `service/PrivateMessageService.java` | `send()` 步骤④ | `seqService.nextSeq(...)` |
| 落库带 seq | 同上 | `send()` 步骤⑤ | `message.setSeq(seq)` |
| 数据库约束 | `v1.8_message_seq.sql` | `uk_conv_seq` | 同会话 seq 唯一 |
| 完整性防护 | `v1.8.1_seq_guard.sql` | `chk_seq_positive` | seq >= 1 |
| **客户端排序** | `views/chat/index.vue` | `insertBySeq()` | ★ 按 seq 插入，不用时间戳 |
| **空洞检测** | `composables/usePrivateSocket.js` | `trackSeq()` L290 | ★ `seq > localMax+1` 判定 |
| 触发补洞 | 同上 | `requestResync()` L323 | 发 `private_resync` |
| 补洞接口 | `controller/PrivateChatController.java` | `listSeqRange()` | `GET .../messages/range` |

**技术上用了什么**：Redis INCR（原子发号）+ MySQL 唯一索引 + CHECK 约束

**踩坑记录**：seq 曾因旧代码 INSERT 不含该列而被 MySQL 填入 0，导致排序错乱。后加 `chk_seq_positive` 约束让漏填**显式报错**。

---

## Q4 状态与多端 —— 离线消息谁保管？

**PPT 原话**：对方不在线时消息必须先落库，上线后补投；同一账号多设备登录还要各端看到一致的历史。

| 要求 | 文件 | 关键位置 | 说明 |
|---|---|---|---|
| **① 离线先落库** | `service/PrivateMessageService.java` | `send()` 步骤⑤ | ★ 先写 MySQL，再谈推送 |
| **② 事务后推送** | 同上 | `send()` 步骤⑦ | `registerAfterCommit(...)` |
| **③ 上线补投** | 同上 | `syncOffline()` L353 | ★ 按游标拉取缺失消息 |
| 补拉报文 | `websocket/PrivateWebSocketHandler.java` | `handleSync()` | 处理 `sync` 报文 |
| 前端重连补拉 | `composables/usePrivateSocket.js` | `ws.onopen` L143 | ★ 重连即发 `sync` |
| **④ 多端并存** | `service/OnlineUserService.java` | `localSessions` L44 | ★ `Map<Long, Map<sessionId, session>>` |
| 在线登记 | 同上 | `online()` L53 | 写入本地 + Redis |
| 在线判断 | 同上 | `isOnline()` L105 | 查 Redis（跨实例准确） |
| TTL 防僵尸 | 同上 | `ONLINE_TTL` | 60s，心跳续期 |
| **⑤ 跨实例投递** | `service/PrivatePushService.java` | `pushMessage()` L83 | ★ Redis Pub/Sub |
| 广播接收 | 同上 | `onMessage()` L126 | 订阅回调，originId 去重 |

**技术上用了什么**：MySQL（持久化）+ Redis Set（在线表）+ Redis Pub/Sub（跨实例）

**PPT P12 核心思想**：推是加速，拉是兜底 —— **我们的实现完全遵循**。

**已知差异**：已读状态的跨端**实时**同步未做（数据一致，只是不实时推送）。

---

## 快速定位：IDEA 搜索快捷键

| 搜索内容 | 快捷键 | 能定位到 |
|---|---|---|
| `★【Q1` | Ctrl+Shift+F | 所有实时性相关代码 |
| `★【Q2` | Ctrl+Shift+F | 所有可靠性相关代码 |
| `★【Q3` | Ctrl+Shift+F | 所有顺序性相关代码 |
| `★【Q4` | Ctrl+Shift+F | 所有状态与多端相关代码 |

---

## 一次消息的完整代码路径（建议按此顺序演示）

```
① 用户点发送
   └─ views/chat/index.vue  submit()
       └─ usePrivateSocket.js  sendWithRetry()        ← 【Q2】启动 3s 超时重发

② 服务端收到
   └─ PrivateWebSocketHandler.handleTextMessage()     ← 【Q1】报文分发
       └─ handleSendMessage()
           └─ PrivateMessageService.send()
               ├─ ① 权限校验
               ├─ ② 找/建会话
               ├─ ③ 幂等判重                          ← 【Q2】
               ├─ ④ MessageSeqService.nextSeq()       ← 【Q3】Redis 发号
               ├─ ⑤ 落库                              ← 【Q4】持久化
               ├─ ⑥ 更新会话摘要
               └─ ⑦ 事务提交后推送                     ← 【Q1/Q4】
                   └─ PrivatePushService.pushMessage()
                       ├─ isOnline()? 查 Redis 在线表   ← 【Q4】
                       ├─ deliverLocal()  推本机连接     ← 【Q1】
                       └─ convertAndSend() 广播其他实例   ← P17

③ 客户端收到
   └─ PrivateWebSocketHandler.send(PRIVATE_ACK)      ← 【Q2】ACK 回执
       └─ 前端 handleAck() → 清除重发定时器

④ 接收方收到
   └─ 前端 ws.onmessage → handlePacket()
       ├─ trackSeq()                                   ← 【Q3】空洞检测
       ├─ 回 private_delivered                         ← 三态"已送达"
       └─ 回 private_read                              ← 【Q4】已读游标
```