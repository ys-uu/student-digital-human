import { onMounted, onUnmounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { listConversations } from '@/api/v1/private'

/**
 * 私信 WebSocket 单例。
 * 全局只保留一条 /ws/private 长连接，页面切换不会关闭；聊天页和工作台共享状态。
 */
const status = ref('idle')
const lastMessageId = ref(0)
const seqMap = ref({})
const unreadCount = ref(0)
const conversationUnread = ref({})

let ws = null
let retry = 0
let retryTimer = null
let heartbeatTimer = null
let pongTimer = null
let stopped = false
let fallbackNotified = false
let activeConversationId = null

const pendingAcks = new Map()
const listeners = new Set()

const ACK_TIMEOUT = 3000
const MAX_RETRY = 3
const HEARTBEAT = 30000
const PONG_TIMEOUT = 10000

function emit(name, payload) {
  listeners.forEach((listener) => listener?.[name]?.(payload))
}

function setStatus(value) {
  status.value = value
}

function buildUrl(token) {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${location.host}/ws/private?token=${encodeURIComponent(token)}`
}

function connect() {
  const userStore = useUserStore()
  if (!userStore.token) {
    setStatus('idle')
    return
  }
  if (ws) return

  stopped = false
  setStatus('connecting')
  try {
    ws = new WebSocket(buildUrl(userStore.token))
  } catch (e) {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    retry = 0
    fallbackNotified = false
    setStatus('live')
    startHeartbeat()
    send('sync', { last_msg_id: String(lastMessageId.value) })
    refreshUnread()
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
    if (stopped) {
      setStatus('idle')
      return
    }
    if (event?.code === 1008) {
      stopped = true
      notifyFallback()
      return
    }
    if (retry >= 2) notifyFallback()
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (stopped) return
  const delay = Math.min(1000 * 2 ** retry, 15000)
  retry += 1
  clearTimeout(retryTimer)
  retryTimer = setTimeout(connect, delay)
}

function notifyFallback() {
  if (fallbackNotified) return
  fallbackNotified = true
  setStatus('fallback')
  emit('onFallback')
}

function normalizeMessage(message) {
  if (!message || typeof message !== 'object') return message
  const normalized = { ...message }
  const aliases = {
    message_id: 'messageId',
    conversation_id: 'conversationId',
    client_msg_id: 'clientMsgId',
    msg_type: 'msgType',
    media_url: 'mediaUrl',
    media_size: 'mediaSize',
    media_duration: 'mediaDuration',
    media_name: 'mediaName',
    from_name: 'fromName',
    create_time: 'createTime'
  }
  Object.entries(aliases).forEach(([from, to]) => {
    if (normalized[from] !== undefined) normalized[to] = normalized[from]
  })
  if (normalized.msgType !== undefined) normalized.msgType = Number(normalized.msgType)
  if (normalized.mediaSize !== undefined) normalized.mediaSize = Number(normalized.mediaSize)
  if (normalized.mediaDuration !== undefined) normalized.mediaDuration = Number(normalized.mediaDuration)
  return normalized
}

function handlePacket(packet) {
  const type = packet?.type
  const rawData = packet?.data

  switch (type) {
    case 'connected':
      break

    case 'pong':
      clearTimeout(pongTimer)
      break

    case 'private_recv': {
      const data = normalizeMessage(rawData)
      trackMessageId(data?.message_id)
      if (data?.conversation_id && data?.seq != null) {
        send('private_delivered', {
          conversation_id: String(data.conversation_id),
          delivered_seq: String(data.seq)
        })
      }
      detectGap(data)
      if (String(activeConversationId) !== String(data.conversation_id)) {
        increaseUnread(data.conversation_id)
      }
      emit('onMessage', data)
      break
    }

    case 'private_ack': {
      const data = normalizeMessage(rawData)
      trackMessageId(data?.message_id)
      clearPending(data?.client_msg_id)
      // ACK 是自己刚发送消息的确认，不据此触发“补洞”。
      trackSeq(data?.conversation_id, data?.seq, { skipGapCheck: true })
      emit('onAck', data)
      break
    }

    case 'private_read':
      emit('onRead', normalizeMessage(rawData))
      break

    case 'private_delivered':
      emit('onDelivered', normalizeMessage(rawData))
      break

    case 'notify':
      emit('onNotify', normalizeMessage(rawData))
      break

    case 'sync':
    case 'private_resync': {
      const messages = rawData?.messages || []
      messages.forEach((item) => {
        const data = normalizeMessage(item)
        trackMessageId(data?.message_id)
        if (data?.conversation_id && data?.seq != null) {
          trackSeq(String(data.conversation_id), Number(data.seq), { skipGapCheck: true })
        }
        emit('onMessage', data)
      })
      emit(type === 'sync' ? 'onSync' : 'onResync', rawData)
      refreshUnread()
      break
    }

    case 'error':
      emit('onError', rawData)
      break

    default:
      break
  }
}

function trackMessageId(id) {
  const value = Number(id)
  if (Number.isFinite(value) && value > lastMessageId.value) {
    lastMessageId.value = value
  }
}

function trackSeq(conversationId, seq, options = {}) {
  if (conversationId == null || seq == null) return
  const cid = String(conversationId)
  const value = Number(seq)
  if (!Number.isFinite(value)) return

  const localMax = seqMap.value[cid] || 0
  if (options.skipGapCheck) {
    if (value > localMax) seqMap.value = { ...seqMap.value, [cid]: value }
    return
  }

  if (value > localMax + 1 && localMax > 0) {
    requestResync(cid, localMax, value)
  }
  if (value > localMax) {
    seqMap.value = { ...seqMap.value, [cid]: value }
  }
}

function detectGap(message) {
  if (!message?.conversation_id || message?.seq == null) return
  trackSeq(String(message.conversation_id), Number(message.seq))
}

function requestResync(conversationId, afterSeq, upToSeq) {
  send('private_resync', {
    conversation_id: String(conversationId),
    after_seq: String(afterSeq),
    up_to_seq: upToSeq != null ? String(upToSeq) : null
  })
}

function send(type, data = null) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return false
  try {
    ws.send(JSON.stringify({ type, data }))
    return true
  } catch (e) {
    return false
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (!send('ping')) return
    clearTimeout(pongTimer)
    pongTimer = setTimeout(() => ws?.close(), PONG_TIMEOUT)
  }, HEARTBEAT)
}

function stopHeartbeat() {
  clearInterval(heartbeatTimer)
  clearTimeout(pongTimer)
  heartbeatTimer = null
  pongTimer = null
}

function sendWithRetry(payload) {
  const ok = send('private_msg', payload)
  if (!ok) return false

  const key = payload.client_msg_id
  const existing = pendingAcks.get(key)
  if (existing) clearTimeout(existing.timer)

  const entry = {
    payload,
    tries: existing?.tries || 0,
    timer: null
  }

  entry.timer = setTimeout(function retryTick() {
    const current = pendingAcks.get(key)
    if (!current) return
    current.tries += 1
    if (current.tries > MAX_RETRY) {
      pendingAcks.delete(key)
      emit('onError', { code: 'ACK_TIMEOUT', msg: '消息发送超时', client_msg_id: key })
      return
    }
    if (send('private_msg', current.payload)) {
      current.timer = setTimeout(retryTick, ACK_TIMEOUT)
    } else {
      pendingAcks.delete(key)
    }
  }, ACK_TIMEOUT)

  pendingAcks.set(key, entry)
  return true
}

function clearPending(clientMsgId) {
  const entry = pendingAcks.get(clientMsgId)
  if (!entry) return
  clearTimeout(entry.timer)
  pendingAcks.delete(clientMsgId)
}

function sendRead(conversationId, lastReadSeq) {
  if (conversationId == null) return false
  return send('private_read', {
    conversation_id: String(conversationId),
    last_read_seq: lastReadSeq != null ? String(lastReadSeq) : null
  })
}

function seedCursor(msgId, conversationId, seq) {
  const messageId = Number(msgId)
  if (Number.isFinite(messageId) && messageId > lastMessageId.value) {
    lastMessageId.value = messageId
  }
  if (conversationId != null && seq != null) {
    const cid = String(conversationId)
    const value = Number(seq)
    if (Number.isFinite(value) && value > (seqMap.value[cid] || 0)) {
      seqMap.value = { ...seqMap.value, [cid]: value }
    }
  }
}

function getSeq(conversationId) {
  return seqMap.value[String(conversationId)] || 0
}

function recalcUnread() {
  unreadCount.value = Object.values(conversationUnread.value)
    .reduce((sum, value) => sum + (Number(value) || 0), 0)
}

function setUnreadList(list) {
  const next = {}
  ;(list || []).forEach((item) => {
    next[String(item.conversationId)] = Number(item.unreadCount) || 0
  })
  conversationUnread.value = next
  recalcUnread()
}

async function refreshUnread() {
  try {
    const res = await listConversations({ page: 1, page_size: 100 })
    if (res.code === 200) setUnreadList(res.data?.list || [])
  } catch (e) {
    // 保留现有未读数，等待下次重连刷新
  }
}

function increaseUnread(conversationId) {
  const cid = String(conversationId)
  conversationUnread.value = {
    ...conversationUnread.value,
    [cid]: (conversationUnread.value[cid] || 0) + 1
  }
  recalcUnread()
}

function clearUnread(conversationId) {
  const cid = String(conversationId)
  if (!conversationUnread.value[cid]) return
  conversationUnread.value = { ...conversationUnread.value, [cid]: 0 }
  recalcUnread()
}

function setActiveConversation(conversationId) {
  activeConversationId = conversationId == null ? null : String(conversationId)
}

function disconnect() {
  stopped = true
  clearTimeout(retryTimer)
  stopHeartbeat()
  pendingAcks.forEach((entry) => clearTimeout(entry.timer))
  pendingAcks.clear()
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  activeConversationId = null
  setStatus('idle')
}

function reconnect() {
  disconnect()
  stopped = false
  retry = 0
  fallbackNotified = false
  connect()
}

export function usePrivateSocket(options = {}) {
  onMounted(() => {
    listeners.add(options)
    connect()
  })
  onUnmounted(() => listeners.delete(options))

  return {
    status,
    lastMessageId,
    seqMap,
    unreadCount,
    conversationUnread,
    getSeq,
    send,
    sendWithRetry,
    sendRead,
    seedCursor,
    connect,
    disconnect,
    reconnect,
    refreshUnread,
    setUnreadList,
    increaseUnread,
    clearUnread,
    setActiveConversation
  }
}
