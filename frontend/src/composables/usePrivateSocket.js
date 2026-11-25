  import { onMounted, onUnmounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 私信实时通道 /ws/private，按《实时聊天系统技术与实现》实现完整可靠性链路。
 *
 * ============================================================================
 * 【PPT P03 四问在前端的落点】—— 按此索引阅读代码
 * ----------------------------------------------------------------------------
 * 【Q1 实时性】服务端怎么主动推到我这
 *   · connect()          —— 建立 WebSocket 长连接（new WebSocket）
 *   · ws.onmessage       —— 服务端随时推过来，这里接收
 *   · handlePacket()     —— 按报文 type 分发
 *
 * 【Q2 可靠性】我发的消息怎么确保不丢
 *   · sendWithRetry()    —— ★3s 未收到 ACK 自动重发，最多 3 次
 *   · clearPending()     —— 收到 ACK 后停止重发
 *   · handlePacket('private_ack') —— ACK 到达的处理
 *
 * 【Q3 顺序性】消息怎么保持正确顺序
 *   · trackSeq()         —— ★维护会话级 localMax，判断消息是否连续
 *   · detectGap()        —— seq > localMax+1 说明中间漏了
 *   · requestResync()    —— ★发 private_resync 拉取缺失区间（补洞）
 *
 * 【Q4 状态与多端】断线期间的消息怎么办
 *   · ws.onopen          —— ★重连成功后立即发 sync，补拉断线期消息
 *   · sendRead()         —— 已读游标上报（服务端统一维护，多端一致）
 *   · lastMessageId      —— 跨会话补拉游标
 *
 * 补充：心跳与重连（PPT P07）——startHeartbeat / scheduleReconnect
 * ============================================================================
 *
 * 与其他 composable 的差异：
 *   1) token 从 Pinia 读（本系统 access token 不落 localStorage）；
 *   2) 状态按会话维度维护 seq 游标，而非全局一个值；
 *   3) 自己发出的消息在 ACK 前被重发时，靠 clientMsgId 去重，不会产生两条。
 */
export function usePrivateSocket(options = {}) {
  const {
    onMessage,      // 收到对方消息 private_recv
    onAck,          // 自己发出的消息被服务端确认 private_ack
    onRead,         // 已读回执 private_read
    onDelivered,    // 送达回执 private_delivered
    onNotify,       // 系统通知 notify
    onError,        // 业务异常 error
    onSync,         // 补拉结果 sync
    onResync,       // 补洞结果 private_resync
    onFallback,     // 连续重连失败，降级 HTTP
    onStatusChange  // 连接状态变化
  } = options

  const userStore = useUserStore()

  const status = ref('idle') // idle | connecting | live | fallback
  /** 全局消息 id 游标（跨会话，用于 sync 补拉） */
  const lastMessageId = ref(0)
  /** 会话级 seq 游标：conversationId → 已收到的最大 seq（PPT P11） */
  const seqMap = ref({})

  let ws = null
  let retry = 0
  let retryTimer = null
  let heartbeatTimer = null
  let pongTimer = null
  let stopped = false
  let fallbackNotified = false
  /** 待确认消息：clientMsgId → { payload, timer, tries }，用于超时重发（PPT P10） */
  const pendingAcks = new Map()

  const ACK_TIMEOUT = 3000     // 3s 未收到 ack 即重发
  const MAX_RETRY = 3          // 每条消息最多重发 3 次
  const HEARTBEAT = 30000      // 30s 心跳
  const PONG_TIMEOUT = 10000   // 10s 未收到 pong 判定连接假死

  const buildUrl = () => {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const token = userStore.token || ''
    return `${protocol}://${location.host}/ws/private?token=${encodeURIComponent(token)}`
  }

  const setStatus = (v) => {
    status.value = v
    onStatusChange?.(v)
  }

  const notifyFallback = () => {
    if (fallbackNotified) return
    fallbackNotified = true
    setStatus('fallback')
    onFallback?.()
  }

  /** 原始发送，连接不可用时返回 false */
  const send = (type, data = null) => {
    if (!ws || ws.readyState !== WebSocket.OPEN) return false
    try {
      ws.send(JSON.stringify({ type, data }))
      return true
    } catch (e) {
      return false
    }
  }

  // ==================== 心跳（PPT P07） ====================

  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (!send('ping')) return
      // 发出 ping 后启动 pong 超时：超时说明连接半开，主动断开触发重连
      clearTimeout(pongTimer)
      pongTimer = setTimeout(() => {
        ws?.close()
      }, PONG_TIMEOUT)
    }, HEARTBEAT)
  }

  const stopHeartbeat = () => {
    clearInterval(heartbeatTimer)
    clearTimeout(pongTimer)
    heartbeatTimer = null
    pongTimer = null
  }

  // ==================== 连接与重连（PPT P07） ====================

  const connect = () => {
    if (stopped) return
    if (!userStore.token) {
      setStatus('idle')
      return
    }
    setStatus('connecting')

    try {
      ws = new WebSocket(buildUrl())
    } catch (e) {
      notifyFallback()
      scheduleReconnect()
      return
    }

    // ★【Q1 实时性】连接建立成功 —— 这是"服务端可以主动推给我"的起点
    // 从这里开始，服务端可在任意时刻通过 ws.onmessage 推数据过来，
    // 不再需要我这边发请求（这是 WebSocket 与 HTTP 轮询的本质区别）。
    ws.onopen = () => {
      retry = 0
      fallbackNotified = false
      setStatus('live')
      startHeartbeat()
      // ★【Q4 状态与多端】重连成功后第一件事：补拉断线期间的消息
      //
      // 为什么必须做：断线期间对方发的消息，本端一条都没收到（onmessage 没触发）。
      // 这些消息已经在服务端 MySQL 里，只需带上本地游标问服务端要增量即可。
      // 这正是 PPT P12 的核心思想："推"是加速，"拉"才是保证不丢的兜底。
      send('sync', { last_msg_id: String(lastMessageId.value) })
    }

    ws.onmessage = (event) => {
      let packet
      try {
        packet = JSON.parse(event.data)
      } catch (e) {
        return
      }
      handlePacket(packet)
    }

    ws.onerror = () => ws?.close()

    ws.onclose = (event) => {
      ws = null
      stopHeartbeat()
      if (stopped) return
      // 1008：服务端判定身份不可用，终结态不再重连
      if (event && event.code === 1008) {
        stopped = true
        notifyFallback()
        return
      }
      if (retry >= 2) notifyFallback()
      scheduleReconnect()
    }
  }

  const scheduleReconnect = () => {
    if (stopped) return
    // PPT P07：1s → 2s → 4s … 封顶 15s，避免重连风暴
    const delay = Math.min(1000 * 2 ** retry, 15000)
    retry += 1
    clearTimeout(retryTimer)
    retryTimer = setTimeout(connect, delay)
  }

  // ==================== 报文分发 ====================

  const handlePacket = (packet) => {
    const data = packet?.data
    switch (packet?.type) {
      case 'connected':
        break

      case 'pong':
        clearTimeout(pongTimer)
        break

      case 'private_recv':
        // ★【Q1 实时性】★【Q2】★【Q3】收到对方消息 —— 三个问题在这一分支汇聚
        //
        // Q1：这条报文是【服务端主动推】过来的，不是我们请求的响应。
        //     这正是 WebSocket 相对 HTTP 的核心优势。
        //
        // Q2：立即回执 private_delivered，告诉服务端"我收到了"，
        //     服务端据此推进送达游标 → 发送方界面变成"已送达"。
        //     这是三态中的第三态，证明消息真的到了对端设备。
        //
        // Q3：detectGap 检查 seq 是否连续，跳号说明中间漏了，触发补洞。
        trackMessageId(data?.message_id)
        // 回执送达：告诉服务端「我收到 seq 几」，驱动发送方三态（PPT P10）
        if (data?.conversation_id && data?.seq != null) {
          send('private_delivered', {
            conversation_id: String(data.conversation_id),
            delivered_seq: String(data.seq)
          })
        }
        // PPT P11：顺序检测 —— 先判断空洞，再回调业务
        detectGap(data)
        onMessage?.(data)
        break

      case 'private_ack':
        // ★【Q2 可靠性】发送确认到达 —— 停止重发
        //
        // 客户端发出消息后启动了 3s 定时器，若此时收到 ACK：
        //   · clearPending 清掉定时器 → 不再重发
        //   · 服务端已落库，消息安全了
        //
        // 若 3s 内没收到这条报文，说明消息可能丢了，
        // 定时器会自动重发（服务端靠 clientMsgId 去重，不会产生两条）。
        trackMessageId(data?.message_id)
        clearPending(data?.client_msg_id)
        // ★【Q3】ACK 带回 seq，客户端据此维护会话内游标
        trackSeq(data?.conversation_id, data?.seq)
        onAck?.(data)
        break

      case 'private_read':
        // ★【Q4 状态与多端】对方读了你的消息 → 界面显示"已读"
        // 由服务端在 markRead 后推送给发送方，与已读游标是同一份数据。
        onRead?.(data)
        break

      case 'private_delivered':
        // ★【Q2 可靠性】对方确认收到 → 界面从"已发送"变为"已送达"
        // 注意这【不是】已读，只说明消息进了对方客户端。
        onDelivered?.(data)
        break

      case 'notify':
        onNotify?.(data)
        break

      case 'sync':
        (data?.messages || []).forEach((m) => {
          trackMessageId(m?.message_id)
          if (m?.conversation_id && m?.seq != null) {
            // 补拉不触发空洞检测：整批已经是完整区间
            trackSeq(String(m.conversation_id), Number(m.seq), { skipGapCheck: true })
          }
          onMessage?.(m)
        })
        onSync?.(data)
        break

      case 'private_resync':
        (data?.messages || []).forEach((m) => {
          trackMessageId(m?.message_id)
          if (m?.conversation_id && m?.seq != null) {
            trackSeq(String(m.conversation_id), Number(m.seq), { skipGapCheck: true })
          }
          onMessage?.(m)
        })
        onResync?.(data)
        break

      case 'error':
        onError?.(data)
        break

      default:
        break
    }
  }

  // ==================== seq 顺序与空洞检测（PPT P11） ====================

  /** 记录跨会话的消息 id 游标（供 sync 使用） */
  const trackMessageId = (id) => {
    const v = Number(id)
    if (Number.isFinite(v) && v > lastMessageId.value) {
      lastMessageId.value = v
    }
  }

  /**
   * ★★★【Q3 顺序性】维护会话级 seq 游标，并在发现跳号时请求补洞 ★★★
     *
     * 这是 PPT P11「空洞检测」的核心实现。判定规则：
     * <pre>
     * 收到 seq = localMax + 1  → 正常接上，直接推进游标
     * 收到 seq >  localMax + 1  → ★有空洞！说明中间某条没收到，触发 pullMissing
     * 收到 seq <= localMax      → 重复消息（补洞与实时推送撞车），业务层去重
     * </pre>
     *
     * <b>为什么需要空洞检测</b>：
     * <br>
     * WebSocket 理论上保证 TCP 有序，但实际中消息仍可能"看起来跳号"：
     *   · 服务端推送时本端恰好断线重连，中间几条没收到；
     *   · 多实例部署时两台机器的推送到达顺序不一致；
     *   · 消息在事务提交后推送，并发写入时 seq 分配与推送顺序可能错位。
     * 不做检测的话，界面会"静默丢消息"，用户只看到对话跳了一句。
     *
     * <b>为什么不直接用 id 判断</b>：
     * <br>
     * id 是全局自增，跨会话穿插（A 会话 1~100、B 会话 150），
     * 用它判断会话内连续性永远不成立，所以必须有会话级的 seq。
     *
     * <b>skipGapCheck 的作用</b>：
     * <br>
     * sync 补拉和 private_resync 返回的是完整区间，本身就应该连续，
     * 不需要再判空洞（否则会无限递归请求补洞）。
     */
    const trackSeq = (conversationId, seq, opts = {}) => {
        if (conversationId == null || seq == null) return
        const cid = String(conversationId)
        const value = Number(seq)
        if (!Number.isFinite(value)) return

        const localMax = seqMap.value[cid] || 0

        if (opts.skipGapCheck) {
            // 补拉/ACK 路径：直接推进游标，不判空洞
            if (value > localMax) seqMap.value = { ...seqMap.value, [cid]: value }
            return
        }

        if (value > localMax + 1 && localMax > 0) {
            // ★ 空洞：seq 跳号了，说明 (localMax, value) 区间有消息漏收
            // 立刻向服务端请求补齐，避免用户看到"对话断层"
            requestResync(cid, localMax, value)
        }
        if (value > localMax) {
            seqMap.value = { ...seqMap.value, [cid]: value }
        }
    }

  /** 检测并处理空洞；供外部在切换会话时主动调用 */
  const detectGap = (message) => {
    // 报文里没有会话 id 或 seq 时无法做连续性判断，直接跳过
    if (!message?.conversation_id || message?.seq == null) return
    // 交给 trackSeq 统一处理：连续则推进游标，跳号则触发补洞
    trackSeq(String(message.conversation_id), Number(message.seq))
  }

  /**
   * 请求补洞（PPT P11 pullMissing）。
   * 服务端返回 (after_seq, up_to_seq] 区间消息，结果按 private_resync 报文回来。
   */
  const requestResync = (conversationId, afterSeq, upToSeq) => {
    // 发 private_resync 报文，向服务端索要缺失的区间
    send('private_resync', {
      // 会话 id 统一转字符串：后端按 string 解析，避免大数精度丢失
      conversation_id: String(conversationId),
      // 本地已有的最大 seq，服务端从它的下一条开始返回（左开区间）
      after_seq: String(afterSeq),
      // 上界 seq（含）：跳号时传入当前收到的 seq，正常补洞时可为 null
      up_to_seq: upToSeq != null ? String(upToSeq) : null
    })
  }

  // ==================== 发送与超时重发（PPT P10） ====================

  /**
   * ★★★【Q2 可靠性】发送消息并启动 3s 超时重发 ★★★
   *
   * 这是 PPT P10「ACK + 超时重发 + 去重」在前端的实现。
   *
   * 为什么必须重发：
   *   WebSocket 的 send() 只保证"交给内核缓冲区"，不保证对方收到。
   *   网络抖动、服务端瞬时不可用、连接半开（TCP 还在但实际断了）都会导致消息石沉大海。
   *   没有重发机制，用户就是"发出去了但没人收到"，而且界面无法察觉。
   *
   * 为什么重发是安全的（不会造成重复消息）：
   *   服务端有 uk_client(sender_id, client_msg_id) 唯一索引：
   *   同一个 clientMsgId 重复到达时，只补回执、不重复投递。
   *   这正是 PPT P10 强调的「重发与去重永远是一对，单写一个必出事」。
   *
   * 超时时间为什么是 3 秒：
   *   太短（1s）会在正常网络波动时疯狂重发，浪费带宽；
   *   太长（10s）用户会觉得"卡住了"。3s 是体验与可靠性的平衡点。
   *
   * @returns {boolean} 是否成功交给连接发送（false 表示连接不可用，调用方应走 HTTP 兜底）
   */
  const sendWithRetry = (payload) => {
    // ── ① 第一次发送：立刻把消息交给 WebSocket 连接 ──
    // 注意 send() 只返回"是否成功交给连接"，不代表对方收到。
    // 返回 false 说明连接不可用（断开/未建立），调用方会改走 HTTP 兜底。
    const ok = send('private_msg', payload)
    // 连接不可用：直接返回 false，让调用方（submit）走 HTTP 接口发消息
    if (!ok) return false

    // ── ② 取出幂等键：整个重发机制都以 clientMsgId 为索引 ──
    // 同一个 clientMsgId 才能对应到同一条待确认消息（PPT P10 幂等锚点）
    const key = payload.client_msg_id
    // 查这张"待确认表"里是否已有同一条消息的记录（用户快速连点时会存在）
    const existing = pendingAcks.get(key)
    // 若已存在：先清掉它上一次的定时器，避免两个定时器同时跑导致重复重发
    if (existing) clearTimeout(existing.timer)

    // ── ③ 构造待确认条目，登记进表中 ──
    const entry = {
      // 原样保存消息体：重发时必须一字不差地重发（尤其是 clientMsgId 不能变）
      payload,
      // 继承已有重试次数（连点场景下不清零），没有则从 0 开始
      tries: existing?.tries || 0,
      // 定时器句柄，初始为空，下一行赋值
      timer: null
    }
    // ── ④ 挂载 3 秒定时器：3 秒后若仍未收到 ACK 就执行 retryTick 重发 ──
    // ACK_TIMEOUT = 3000ms（见文件顶部常量）
    entry.timer = setTimeout(function retryTick() {
      // 从待确认表里重新取一次（不直接用 entry，因为可能已被 clearPending 删除）
      const cur = pendingAcks.get(key)
      // ★关键判断：取不到说明收到 ACK 后 clearPending 已删除记录 → 重发任务取消
      if (!cur) return   // 已收到 ack，无需重发
      // 重试次数 +1（在判断上限之前累加，保证计数准确）
      cur.tries += 1
      // 判断是否超过最大重试次数（MAX_RETRY = 3）
      if (cur.tries > MAX_RETRY) {
        // 超过上限：从表中移除，避免之后被误清
        pendingAcks.delete(key)
        // 通知上层"消息发送超时"，界面把气泡标记为失败态
        onError?.({ code: 'ACK_TIMEOUT', msg: '消息发送超时', client_msg_id: key })
        // 放弃重发，直接结束本次 retryTick
        return
      }
      // ── ⑤ 重发：用保存的原始 payload 再发一次，clientMsgId 保持不变 ──
      // 服务端靠 uk_client(sender_id, client_msg_id) 去重，
      // 所以即使重发多次，数据库里也只有一条消息（PPT P10）
      if (send('private_msg', cur.payload)) {
        // 重发成功：再挂一个 3 秒定时器，形成"每 3 秒检查一次"的循环
        // 若这一次收到 ACK，clearPending 会在下一轮把 cur 删掉，循环自然终止
        cur.timer = setTimeout(retryTick, ACK_TIMEOUT)
      } else {
        // 连接已断：由重连后的 sync 兜底，不再本地堆积
        // 此时消息已在服务端落库（若先前那次送达），重连后 sync 会拉回来
        pendingAcks.delete(key)
      }
      // 定时器到期时间：3 秒（首轮挂载与后续续挂都用这个值）
    }, ACK_TIMEOUT)

    // ── ⑥ 把条目登记进待确认表，供 clearPending 在收到 ACK 时定位删除 ──
    pendingAcks.set(key, entry)
    // 返回 true 表示消息已成功交给连接（是否真的送达由 ACK 决定）
    return true
  }

  /**
   * ★【Q2 可靠性】清除待确认记录 —— 收到 ACK 时调用，作用是"停止重发"。
   *
   * 与 sendWithRetry 是一对：一个负责"没人应就重发"，一个负责"有人应就收手"。
   * 少了这个，消息即使送达也会被无脑重发 3 次。
   */
  const clearPending = (clientMsgId) => {
    // 按 clientMsgId 从待确认表里取出对应条目
    const entry = pendingAcks.get(clientMsgId)
    // 条目存在才处理（可能已被前一次 ACK 或超时逻辑删掉）
    if (entry) {
      // ★清掉 3 秒定时器：retryTick 不会再被触发，重发彻底停止
      clearTimeout(entry.timer)
      // ★从表中删除：即使定时器已在执行中途，retryTick 里的 if(!cur) 也会 return
      pendingAcks.delete(clientMsgId)
    }
  }

  // ==================== 对外方法 ====================

  /** 发送已读（PPT P12：已读用游标记录） */
  const sendRead = (conversationId, lastReadSeq) => {
    if (conversationId == null) return false
    return send('private_read', {
      conversation_id: String(conversationId),
      last_read_seq: lastReadSeq != null ? String(lastReadSeq) : null
    })
  }

  /** 手动把游标推到某个值（首屏加载历史后调用，避免重连重复补拉） */
  const seedCursor = (msgId, conversationId, seq) => {
    const v = Number(msgId)
    if (Number.isFinite(v) && v > lastMessageId.value) lastMessageId.value = v
    if (conversationId != null && seq != null) {
      const cid = String(conversationId)
      const s = Number(seq)
      if (Number.isFinite(s) && s > (seqMap.value[cid] || 0)) {
        seqMap.value = { ...seqMap.value, [cid]: s }
      }
    }
  }

  const getSeq = (conversationId) => seqMap.value[String(conversationId)] || 0

  const close = () => {
    stopped = true
    clearTimeout(retryTimer)
    stopHeartbeat()
    pendingAcks.forEach((e) => clearTimeout(e.timer))
    pendingAcks.clear()
    if (ws) {
      ws.onclose = null
      ws.close()
      ws = null
    }
    setStatus('idle')
  }

  const reconnect = () => {
    close()
    stopped = false
    retry = 0
    fallbackNotified = false
    connect()
  }

  onMounted(connect)
  onUnmounted(close)

  return {
    status,
    lastMessageId,
    seqMap,
    getSeq,
    send,
    sendWithRetry,
    sendRead,
    requestResync,
    seedCursor,
    close,
    reconnect
  }
}