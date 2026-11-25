<template>
  <div class="chat-page">
    <!-- ============ 左栏：联系人 / 会话列表 ============ -->
    <aside class="side">
      <header class="side-head">
        <div class="brand">
          <div class="logo">私</div>
          <div class="brand-txt">
            <strong>私信</strong>
            <span :class="['conn', status]">{{ statusText }}</span>
          </div>
        </div>
        <button class="back" title="返回工作台" @click="goBack">
          <Icon name="route" />
        </button>
      </header>

      <div class="tabs">
        <button :class="{ on: tab === 'sessions' }" @click="tab = 'sessions'">
          会话
          <em v-if="totalUnread > 0">{{ totalUnread > 99 ? '99+' : totalUnread }}</em>
        </button>
        <button :class="{ on: tab === 'contacts' }" @click="openContacts">联系人</button>
      </div>

      <!-- 会话列表 -->
      <div v-if="tab === 'sessions'" class="list">
        <p v-if="loadingSessions" class="hint">加载中…</p>
        <p v-else-if="!sessions.length" class="hint">还没有会话，去「联系人」发起一场对话</p>
        <article v-for="s in sessions" :key="s.conversationId" class="item"
                 :class="{ on: active?.conversationId === s.conversationId }"
                 @click="pickSession(s)">
          <div class="ava">{{ initial(s.peerName) }}</div>
          <div class="meta">
            <div class="row1">
              <strong>{{ s.peerName || '未知用户' }}</strong>
              <time>{{ shortTime(s.lastMessageAt) }}</time>
            </div>
            <div class="row2">
              <span class="preview">{{ s.lastMessagePreview || '暂无消息' }}</span>
              <em v-if="s.unreadCount > 0" class="badge">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</em>
            </div>
          </div>
        </article>
      </div>

      <!-- 联系人列表 -->
      <div v-else class="list">
        <p v-if="loadingContacts" class="hint">加载中…</p>
        <p v-else-if="!contacts.length" class="hint">暂无可私信联系人</p>
        <template v-for="group in contactGroups" :key="group.name">
          <p class="group-title">{{ group.name }}</p>
          <article v-for="c in group.items" :key="c.user_id" class="item"
                   @click="startChat(c)">
            <div class="ava">{{ initial(c.real_name) }}</div>
            <div class="meta">
              <div class="row1">
                <strong>{{ c.real_name }}</strong>
                <span class="role-tag" :class="c.role === 2 ? 'teacher' : 'student'">
                  {{ c.role === 2 ? '教师' : '学生' }}
                </span>
              </div>
              <div class="row2"><span class="preview">{{ c.user_no }}</span></div>
            </div>
          </article>
        </template>
      </div>
    </aside>

    <!-- ============ 右栏：聊天窗口 ============ -->
    <main class="main">
      <!-- 未选中会话 -->
      <div v-if="!active" class="empty">
        <div class="empty-ico">💬</div>
        <h2>选择一位联系人开始对话</h2>
        <p>私信支持与同学、以及你所在课程的老师实时沟通</p>
        <button class="primary" @click="openContacts">查看联系人</button>
      </div>

      <!-- 聊天区 -->
      <template v-else>
        <header class="chat-head">
          <div class="ava">{{ initial(active.peerName) }}</div>
          <div class="who">
            <strong>{{ active.peerName || '未知用户' }}</strong>
            <span>{{ active.peerRole === 2 ? '教师' : '学生' }} · <i :class="['dot', status]" /> {{ statusText }}</span>
          </div>
          <button class="more" title="拉取最新消息" @click="reloadMessages">
            <Icon name="clock" />
          </button>
        </header>

        <section ref="scrollRef" class="messages" @scroll="onScroll">
          <p v-if="hasMore" class="load-more" @click="loadEarlier">
            {{ loadingMore ? '加载中…' : '加载更早的消息' }}
          </p>
          <p v-else-if="messages.length" class="load-more done">已到最早</p>

          <div v-for="m in messages" :key="m.messageId || m.clientMsgId" class="msg" :class="{ mine: isMine(m) }">
            <div class="ava sm">{{ initial(isMine(m) ? myName : m.from_name) }}</div>
            <div class="bubble-wrap">
              <!--
                按 msgType 渲染三种形态（v1.9 新增图片/音频）：
                  msgType=1 文本 → 直接显示 content
                  msgType=2 图片 → <img> 缩略图，点击查看大图
                  msgType=4 音频 → <audio> 播放器
                  msgType=3 文件 → 显示文件名与下载链接
                媒体气泡不加白底和内边距（.bubble.media），避免图片被框住。
              -->
              <div class="bubble" :class="{ failed: m.state === 'failed', media: isVisualMedia(m) }">
                <!-- 图片：用 el-image 支持加载失败占位与点击预览大图 -->
                <el-image
                  v-if="m.msgType === 2 && m.mediaUrl"
                  class="msg-image"
                  :src="m.mediaUrl"
                  :preview-src-list="[m.mediaUrl]"
                  :preview-teleported="true"
                  fit="cover"
                  loading="lazy"
                >
                  <template #error>
                    <div class="img-fallback">图片加载失败</div>
                  </template>
                </el-image>

                <!-- 音频：原生 audio 控件，加 controls 即可播放 -->
                <div v-else-if="m.msgType === 4 && m.mediaUrl" class="msg-audio">
                  <audio :src="m.mediaUrl" controls preload="metadata" />
                  <!-- 时长来自消息元数据；部分浏览器无法读取时留空 -->
                  <span v-if="m.mediaDuration" class="dur">{{ m.mediaDuration }}″</span>
                </div>

                <!-- 文件：显示图标 + 文件名，点击下载 -->
                <a v-else-if="m.msgType === 3 && m.mediaUrl" class="msg-file"
                   :href="m.mediaUrl" target="_blank" rel="noopener">
                  <span class="file-icon">📄</span>
                  <span class="file-name">{{ m.mediaName || '文件' }}</span>
                </a>

                <!-- 文本：原有逻辑；媒体消息 content 为空时不渲染空 span -->
                <span v-else-if="m.content">{{ m.content }}</span>
              </div>
              <div class="stamp">
                <time>{{ fullTime(m.createTime) }}</time>
                <!-- 三态：发送中 → 已送达 → 已读（PPT P10） -->
                <em v-if="isMine(m)" class="state" :class="stateClass(m)">
                  {{ stateText(m) }}
                </em>
              </div>
            </div>
          </div>

          <p v-if="!messages.length" class="hint center">还没有消息，发第一条吧</p>
        </section>

        <footer class="composer">
          <!--
            工具栏：图片与音频上传入口（v1.9 新增）。
            用隐藏的 <input type="file"> 而非 el-upload：
            本场景是"选完立即上传并发送"，不需要多文件队列与进度条列表，
            原生 input 更轻，也便于精确控制 allow 类型。
          -->
          <div class="tools">
            <button class="tool-btn" title="发送图片" :disabled="uploading" @click="pickImage">
              <span v-if="!uploading">🖼️</span>
              <span v-else class="spin">⏳</span>
            </button>
            <button class="tool-btn" title="发送音频" :disabled="uploading" @click="pickAudio">🎤</button>
            <input ref="imageInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp"
                   hidden @change="onImageSelected" />
            <!--
                 同时写 MIME 与扩展名两种形式：
                 只用 MIME 时，部分浏览器/系统对某些音频后缀（如 .wav、.m4a）
                 的类型映射不一致，会出现文件明明存在却显示"没有匹配项"的情况。
                 补上扩展名后可直接选到 Windows 自带提示音（C:\Windows\Media\*.wav）。
               -->
            <input ref="audioInput" type="file"
                   accept="audio/*,.mp3,.m4a,.aac,.ogg,.wav,.webm"
                   hidden @change="onAudioSelected" />
          </div>
          <textarea v-model="draft" rows="1" placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                    @keydown.enter.exact.prevent="submit" />
          <button class="send" :disabled="!draft.trim()" @click="submit">发送</button>
        </footer>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { usePrivateSocket } from '@/composables/usePrivateSocket'
import {
  listConversations, listMessages, listContacts, createConversation, sendMessage,
  // v1.9 新增：媒体上传（预签名直传 MinIO）
  getUploadUrl, putFileToMinio
} from '@/api/v1/private'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const userStore = useUserStore()

/** 当前登录用户标识：用于区分"我发的"与"对方发的"消息 */
const myId = computed(() => userStore.userInfo?.userId || userStore.userInfo?.id || '')
const myName = computed(() => userStore.userInfo?.realName || '我')

const tab = ref('sessions')
const sessions = ref([])
const contacts = ref([])
const active = ref(null)          // 当前会话
const messages = ref([])
const draft = ref('')
const hasMore = ref(false)
const loadingSessions = ref(false)
const loadingContacts = ref(false)
const loadingMore = ref(false)
const scrollRef = ref(null)

/** 联系人按角色分组展示：教师在前，学生其次 */
const contactGroups = computed(() => {
  const teachers = contacts.value.filter((c) => c.role === 2)
  const students = contacts.value.filter((c) => c.role !== 2)
  const groups = []
  if (teachers.length) groups.push({ name: '我的老师', items: teachers })
  if (students.length) groups.push({ name: '同学', items: students })
  return groups
})

const totalUnread = computed(() =>
  sessions.value.reduce((sum, s) => sum + (s.unreadCount || 0), 0)
)

// ==================== WebSocket ====================

const {
  status,
  send,
  sendWithRetry,
  sendRead,
  seedCursor,
  getSeq
} = usePrivateSocket({
  onMessage: handleIncoming,
  onAck: handleAck,
  onRead: handleRead,
  onDelivered: handleDelivered,
  onNotify: handleNotify,
  onError: (e) => ElMessage.error(e?.msg || '操作失败'),
  onResync: (data) => {
    if (data?.messages?.length) {
      ElMessage.info(`已补齐 ${data.messages.length} 条缺失消息`)
    }
  },
  onFallback: () => ElMessage.warning('实时连接中断，已切换为手动刷新模式')
})

const STATUS_TEXT = {
  idle: '未连接',
  connecting: '连接中',
  live: '实时在线',
  fallback: '已降级'
}
const statusText = computed(() => STATUS_TEXT[status.value] || '未连接')

/** 三态文案与样式（PPT P10：发送中 → 已送达 → 已读） */
function stateText(m) {
  if (m.state === 'failed') return '发送失败'
  if (m.readAt) return '已读'
  if (m.delivered || m.state === 'delivered') return '已送达'
  if (!m.seq) return '发送中…'
  return '已发送'
}

function stateClass(m) {
  return {
    failed: m.state === 'failed',
    read: !!m.readAt,
    delivered: !m.readAt && (m.delivered || m.state === 'delivered'),
    sending: !m.seq && m.state !== 'failed'
  }
}

/** 按 seq 插入并保持升序（PPT P11：顺序由服务端 seq 决定） */
function insertBySeq(list, msg) {
  const seq = Number(msg.seq)
  if (!Number.isFinite(seq)) {
    list.push(msg)
    return
  }
  let i = list.length
  while (i > 0 && Number(list[i - 1].seq || 0) > seq) i--
  list.splice(i, 0, msg)
}

/** 收到新消息：更新会话摘要与未读，属于当前会话则按 seq 插入 */
function handleIncoming(data) {
  if (!data) return
  const convId = String(data.conversation_id)
  const msgId = data.messageId || data.message_id

  if (active.value && String(active.value.conversationId) === convId) {
    // 去重：同一 messageId 或同一 seq 只保留一条（补洞与实时推送可能重复到达）
    const exists = messages.value.some(
      (m) => (msgId && m.messageId === msgId) || (data.seq && Number(m.seq) === Number(data.seq))
    )
    if (!exists) {
      insertBySeq(messages.value, data)
      scrollToBottom()
    }
    // 正在看这条会话，立即回执已读（seq 游标）
    const maxSeq = messages.value.reduce(
      (max, m) => Math.max(max, Number(m.seq) || 0), 0
    )
    if (maxSeq > 0) sendRead(convId, maxSeq)
    return
  }
  bumpSession(convId, data)
}

/** 自己发出的消息被服务端确认：补上真实 messageId 与 seq，状态从"发送中"转"已发送" */
function handleAck(data) {
  if (!active.value || String(active.value.conversationId) !== String(data.conversation_id)) return
  const temp = messages.value.find((m) => m.clientMsgId === data.client_msg_id)
  if (temp) {
    temp.messageId = String(data.message_id)
    temp.seq = Number(data.seq)
    temp.createTime = data.create_time
    temp.state = 'sent'
    // ACK 带回 seq 后需要重新排序，保证与其他消息顺序一致
    messages.value = messages.value.slice().sort((a, b) => (Number(a.seq) || 0) - (Number(b.seq) || 0))
  }
  bumpSession(String(data.conversation_id), {
    content: temp?.content || '',
    create_time: data.create_time,
    from_name: myName.value
  })
}

/** 对方读了消息：按 seq 游标更新自己的消息状态 */
function handleRead(data) {
  if (!active.value || String(active.value.conversationId) !== String(data.conversation_id)) return
  const cursor = Number(data.last_read_seq ?? data.last_read_id ?? 0)
  if (!Number.isFinite(cursor)) return
  messages.value.forEach((m) => {
    if (Number(m.seq) > 0 && Number(m.seq) <= cursor) {
      m.readAt = m.readAt || new Date().toISOString()
      m.delivered = true
    }
  })
}

/** 对方已送达：按 seq 游标更新"已送达"态 */
function handleDelivered(data) {
  if (!active.value || String(active.value.conversationId) !== String(data.conversation_id)) return
  const cursor = Number(data.delivered_seq ?? 0)
  if (!Number.isFinite(cursor)) return
  messages.value.forEach((m) => {
    if (Number(m.seq) > 0 && Number(m.seq) <= cursor) {
      m.delivered = true
      if (m.state === 'sent') m.state = 'delivered'
    }
  })
}

function handleNotify(data) {
  ElMessage.info(data?.title || '收到新通知')
}

/** 更新或新建会话摘要，并累加未读 */
function bumpSession(convId, msg) {
  const idx = sessions.value.findIndex((s) => String(s.conversationId) === convId)
  if (idx >= 0) {
    const s = sessions.value[idx]
    s.lastMessagePreview = msg.content || '[消息]'
    s.lastMessageAt = msg.create_time || new Date().toISOString()
    s.unreadCount = (s.unreadCount || 0) + 1
    // 置顶
    sessions.value.splice(idx, 1)
    sessions.value.unshift(s)
  } else {
    // 陌生会话（对方先发起），重新拉一次列表补齐对方信息
    loadSessions()
  }
}

// ==================== 数据加载 ====================

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res = await listConversations({ page: 1, page_size: 50 })
    if (res.code === 200) {
      sessions.value = res.data?.list || []
    }
  } finally {
    loadingSessions.value = false
  }
}

async function openContacts() {
  tab.value = 'contacts'
  if (contacts.value.length) return
  loadingContacts.value = true
  try {
    const res = await listContacts()
    if (res.code === 200) {
      contacts.value = res.data || []
    }
  } finally {
    loadingContacts.value = false
  }
}

/** 从联系人发起对话：先建会话，再切换过去 */
async function startChat(contact) {
  try {
    const res = await createConversation(Number(contact.user_id))
    if (res.code !== 200) {
      ElMessage.error(res.msg || '无法发起对话')
      return
    }
    const convId = res.data.conversation_id
    const existing = sessions.value.find((s) => String(s.conversationId) === String(convId))
    const session = existing || {
      conversationId: convId,
      peerId: contact.user_id,
      peerName: contact.real_name,
      peerRole: contact.role,
      lastMessagePreview: '',
      lastMessageAt: null,
      unreadCount: 0
    }
    if (!existing) sessions.value.unshift(session)
    tab.value = 'sessions'
    await pickSession(session)
  } catch (e) {
    // 权限不足由后端返回 1015/1016/1017，统一在拦截器提示
  }
}

async function pickSession(session) {
  active.value = session
  messages.value = []
  hasMore.value = false
  session.unreadCount = 0
  await reloadMessages()

  // 进入会话即视为已读。
  // 游标取「本页消息中最大的 seq」，而不是数组最后一条——
  // 消息按 seq 倒序加载后再翻转，若列表不完整，末位不一定是会话内最新。
  if (!messages.value.length) return
  const maxSeq = messages.value.reduce((max, m) => {
    const v = Number(m.seq)
    return Number.isFinite(v) && v > max ? v : max
  }, 0)
  if (maxSeq > 0) sendRead(String(session.conversationId), maxSeq)
}

async function reloadMessages() {
  if (!active.value) return
  const res = await listMessages(active.value.conversationId, { limit: 30 })
  if (res.code !== 200) return
  // 后端按 seq 倒序返回，前端翻转为时间正序
  const list = (res.data || []).slice().reverse()
  messages.value = list
  hasMore.value = list.length >= 30
  if (list.length) {
    const newest = list[list.length - 1]
    // 首屏加载后同步游标，避免重连时把已看过的消息再补一遍
    seedCursor(newest.messageId, String(active.value.conversationId), Number(newest.seq))
  }
  scrollToBottom()
}

async function loadEarlier() {
  if (!active.value || loadingMore.value || !messages.value.length) return
  loadingMore.value = true
  try {
    const oldestSeq = Number(messages.value[0].seq) || 0
    const res = await listMessages(active.value.conversationId, {
      before_seq: oldestSeq || undefined,
      limit: 30
    })
    if (res.code === 200) {
      const earlier = (res.data || []).slice().reverse()
      messages.value = [...earlier, ...messages.value]
      hasMore.value = earlier.length >= 30
    }
  } finally {
    loadingMore.value = false
  }
}

// ==================== 发送 ====================

/**
 * 文本消息发送入口。
 */
function submit() {
  const text = draft.value.trim()
  if (!text || !active.value) return
  // 文本消息：msgType=1，走通用的 dispatchMessage
  dispatchMessage({
    msgType: 1,
    content: text,
    mediaUrl: null
  })
  draft.value = ''
}

/**
 * 通用发送方法（v1.9 重构）。
 *
 * 为什么抽出来：文本、图片、音频三种消息的差异只在 payload 字段，
 * 而"本地乐观回显 → 三态 → 3s 超时重发 → HTTP 兜底"这套可靠性机制完全相同。
 * 抽成一个方法，避免三处重复实现导致行为不一致（尤其是三态与重发）。
 *
 * @param {Object} opts { msgType, content, mediaUrl, mediaSize, mediaDuration, mediaName }
 */
function dispatchMessage({ msgType, content = null, mediaUrl = null,
                           mediaSize = null, mediaDuration = null, mediaName = null }) {
  if (!active.value) return

  // clientMsgId 在发送前生成，重发时复用同一个 —— 这是幂等去重的锚点（PPT P10）
  const clientMsgId = genId()
  const payload = {
    to: Number(active.value.peerId),
    content,
    msg_type: msgType,
    client_msg_id: clientMsgId
  }
  // 媒体字段仅在媒体消息时携带，避免文本消息里塞 null 字段
  if (mediaUrl) {
    payload.media_url = mediaUrl
    if (mediaSize != null) payload.media_size = mediaSize
    if (mediaDuration != null) payload.media_duration = mediaDuration
    if (mediaName) payload.media_name = mediaName
  }

  // 先本地回显（乐观更新）：PPT P10「发送前就生成 clientId 并入列展示」
  // 媒体消息也立即展示（图片直接显示本地 URL），用户无需等待上传往返
  const local = {
    messageId: 'tmp-' + clientMsgId,
    clientMsgId,
    conversationId: String(active.value.conversationId),
    from: 'me',
    from_name: myName.value,
    content,
    msgType,
    mediaUrl,
    mediaSize,
    mediaDuration,
    mediaName,
    seq: null,
    state: 'sending',
    delivered: false,
    createTime: new Date().toISOString(),
    readAt: null
  }
  messages.value.push(local)
  scrollToBottom()

  // 优先走 WebSocket（含 3s 超时重发）；不可用时降级 HTTP
  const sent = sendWithRetry(payload)
  if (!sent) {
    sendMessage(payload)
      .then((res) => {
        if (res.code === 200 && res.data) {
          local.messageId = res.data.messageId
          local.seq = Number(res.data.seq)
          local.createTime = res.data.createTime
          local.state = 'sent'
          messages.value = messages.value.slice()
            .sort((a, b) => (Number(a.seq) || 0) - (Number(b.seq) || 0))
        }
      })
      .catch(() => {
        local.state = 'failed'
        ElMessage.error('消息发送失败')
      })
  }
}

// ==================== 媒体上传与发送（v1.9 新增） ====================

/** 上传中标志：防止重复点击导致并发上传多个文件 */
const uploading = ref(false)
const imageInput = ref(null)
const audioInput = ref(null)

/** 图片大小上限，与后端 MAX_IMAGE_SIZE 保持一致 */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
/** 音频大小上限，与后端 MAX_AUDIO_SIZE 保持一致 */
const MAX_AUDIO_SIZE = 20 * 1024 * 1024

function pickImage() {
  imageInput.value?.click()
}

function pickAudio() {
  audioInput.value?.click()
}

function onImageSelected(event) {
  const file = event.target.files?.[0]
  // 清空 input，保证选同一个文件也能再次触发 change
  event.target.value = ''
  if (file) uploadAndSend(file, 2)
}

function onAudioSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) uploadAndSend(file, 4)
}

/**
 * 上传媒体并发送消息。
 *
 * 三步（对应后端 PrivateUploadController 的注释）：
 *   ① 向后端申请预签名上传地址（后端只签名，不接触文件流）
 *   ② 浏览器直接 PUT 到 MinIO（文件流不经过后端，省带宽）
 *   ③ 拿到 accessUrl 后，作为 media_url 发消息
 *
 * @param {File} file 用户选择的文件
 * @param {number} msgType 2=图片，4=音频
 */
async function uploadAndSend(file, msgType) {
  const isImage = msgType === 2
  // 前端先做一次大小校验，避免浪费一次上传请求；后端仍会再校验一次
  const limit = isImage ? MAX_IMAGE_SIZE : MAX_AUDIO_SIZE
  if (file.size > limit) {
    ElMessage.error(`${isImage ? '图片' : '音频'}不能超过 ${limit / 1024 / 1024}MB`)
    return
  }

  uploading.value = true
  const loadingTip = ElMessage({ message: isImage ? '正在上传图片…' : '正在上传音频…', duration: 0 })
  try {
    // ① 申请上传地址
    const resp = await getUploadUrl({
      fileName: file.name,
      contentType: file.type,
      size: file.size
    })
    if (resp.code !== 200 || !resp.data) {
      throw new Error(resp.message || '获取上传地址失败')
    }
    const { uploadUrl, accessUrl, contentType } = resp.data

    // ② 直传 MinIO
    // 注意：Content-Type 必须与申请时一致，否则 MinIO 验签失败
    await putFileToMinio(uploadUrl, file, contentType || file.type)

    // ③ 读取音频时长（可选），用于会话摘要显示 [语音 12″]
    let duration = null
    if (!isImage) {
      duration = await readAudioDuration(file)
    }

    // ④ 发送消息 —— 复用通用的三态与重发机制
    dispatchMessage({
      msgType,
      content: null,
      mediaUrl: accessUrl,
      mediaSize: file.size,
      mediaDuration: duration,
      mediaName: file.name
    })
    loadingTip.close()
    ElMessage.success(isImage ? '图片已发送' : '语音已发送')
  } catch (e) {
    loadingTip.close()
    ElMessage.error(e?.message || '上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

/**
 * 读取音频时长（秒）。
 *
 * 为什么需要：服务端不知道音频多长，但会话摘要要显示 [语音 12″]，
 * 且前端播放器也需要预估宽度。用临时 audio 元素读 metadata 即可，
 * 不加载整个文件（preload=metadata）。
 *
 * 失败时返回 null：部分格式（如某些 webm）浏览器读不出时长，
 * 不应因此阻断消息发送。
 */
function readAudioDuration(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const audio = new Audio()
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => {
      const d = Number.isFinite(audio.duration) ? Math.round(audio.duration) : null
      URL.revokeObjectURL(url)
      resolve(d)
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    audio.src = url
  })
}

// ==================== 展示工具 ====================

/**
 * 判断消息是否由自己发出。
 * 依据三条任一成立：本地标记为 'me'、发送者ID等于当前登录用户ID、发送者姓名相同。
 * 本地乐观回显的消息没有服务端 from，靠 clientMsgId 前缀兜底。
 */
const isMine = (m) => {
  if (!m) return false
  if (m.from === 'me') return true
  if (String(m.messageId || '').startsWith('tmp-')) return true
  if (myId.value && String(m.from) === String(myId.value)) return true
  return false
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

function onScroll() {
  if (scrollRef.value && scrollRef.value.scrollTop === 0 && hasMore.value) {
    loadEarlier()
  }
}

function initial(name) {
  return (name || '?').slice(0, 1)
}

function shortTime(value) {
  if (!value) return ''
  const d = new Date(value)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay
    ? d.toTimeString().slice(0, 5)
    : `${d.getMonth() + 1}/${d.getDate()}`
}

function fullTime(value) {
  if (!value) return ''
  const d = new Date(value)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/**
 * 判断是否为"纯媒体"消息（图片/音频/文件）。
 *
 * 用途：这类消息的气泡不加白底和内边距，
 * 否则图片会被框住、音频控件两侧出现多余留白（v1.9 新增）。
 */
function isVisualMedia(m) {
  return m && (m.msgType === 2 || m.msgType === 3 || m.msgType === 4) && m.mediaUrl
}

/**
 * 媒体类型中文名，用于占位文案与错误提示。
 * v1.9：新增音频（type=4）。
 */
function mediaLabel(type) {
  return { 2: '图片', 3: '文件', 4: '语音' }[type] || '消息'
}

function genId() {
  return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}

function goBack() {
  router.back()
}

onMounted(loadSessions)
</script>

<style scoped>
.chat-page { display: flex; height: 100vh; background: var(--bg, #f4f7fc); color: var(--text, #1b2540); font-family: var(--font-body, system-ui, sans-serif); }

/* ---------- 左栏 ---------- */
.side { width: 320px; flex: 0 0 320px; display: flex; flex-direction: column; background: var(--surface, #fff); border-right: 1px solid var(--border, #e3e9f4); }
.side-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 16px 12px; }
.brand { display: flex; align-items: center; gap: 10px; }
.logo { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; font-weight: 700; color: #fff; background: linear-gradient(135deg, #8bb8ff, #5f8bff); }
.brand-txt { display: flex; flex-direction: column; line-height: 1.3; }
.brand-txt strong { font-size: 15px; }
.brand-txt .conn { font-size: 11.5px; color: var(--muted, #6c7893); display: flex; align-items: center; gap: 4px; }
.brand-txt .conn::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: #bbb; }
.brand-txt .conn.live::before { background: #34c759; }
.brand-txt .conn.connecting::before { background: #f5b955; }
.brand-txt .conn.fallback::before { background: #ff8f6b; }
.back { width: 30px; height: 30px; border: 1px solid var(--border, #e3e9f4); border-radius: 8px; background: transparent; cursor: pointer; color: var(--muted, #6c7893); display: grid; place-items: center; }
.back:hover { background: #f0f4fb; }

.tabs { display: flex; gap: 6px; padding: 0 16px 10px; }
.tabs button { flex: 1; padding: 7px 0; border: none; border-radius: 8px; background: transparent; font-size: 13px; color: var(--muted, #6c7893); cursor: pointer; position: relative; }
.tabs button.on { background: #eaf1ff; color: #2f6fed; font-weight: 600; }
.tabs button em { position: absolute; top: 2px; right: 8px; font-size: 10px; font-style: normal; background: #ff5c5c; color: #fff; border-radius: 8px; padding: 0 5px; }

.list { flex: 1; overflow-y: auto; padding: 0 10px 16px; }
.group-title { margin: 12px 6px 6px; font-size: 11.5px; color: var(--muted, #6c7893); letter-spacing: .04em; }
.hint { padding: 24px 12px; font-size: 13px; color: var(--muted, #6c7893); text-align: center; }
.hint.center { padding: 40px 0; }

.item { display: flex; gap: 10px; padding: 10px; border-radius: 10px; cursor: pointer; }
.item:hover { background: #f5f8ff; }
.item.on { background: #eaf1ff; }
.ava { width: 38px; height: 38px; flex: 0 0 38px; border-radius: 50%; display: grid; place-items: center; font-size: 14px; font-weight: 600; color: #fff; background: linear-gradient(135deg, #9dffd9, #48d9a6); }
.ava.sm { width: 30px; height: 30px; flex: 0 0 30px; font-size: 12px; }
.meta { flex: 1; min-width: 0; }
.row1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.row1 strong { font-size: 13.5px; font-weight: 600; }
.row1 time { font-size: 11px; color: var(--muted, #6c7893); flex: 0 0 auto; }
.row2 { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 3px; }
.preview { font-size: 12.5px; color: var(--muted, #6c7893); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge { font-style: normal; font-size: 10.5px; background: #ff5c5c; color: #fff; border-radius: 9px; padding: 1px 6px; }
.role-tag { font-size: 10.5px; padding: 1px 6px; border-radius: 6px; }
.role-tag.teacher { background: #fff1e3; color: #c47a1c; }
.role-tag.student { background: #e8f6ee; color: #1f8a52; }

/* ---------- 右栏 ---------- */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--muted, #6c7893); }
.empty-ico { font-size: 44px; }
.empty h2 { margin: 6px 0 0; font-size: 17px; color: var(--text, #1b2540); }
.empty p { margin: 0; font-size: 13px; }
.primary { margin-top: 8px; padding: 8px 18px; border: none; border-radius: 8px; background: #2f6fed; color: #fff; font-size: 13px; cursor: pointer; }

.chat-head { display: flex; align-items: center; gap: 10px; padding: 12px 18px; background: var(--surface, #fff); border-bottom: 1px solid var(--border, #e3e9f4); }
.who { display: flex; flex-direction: column; line-height: 1.35; }
.who strong { font-size: 14.5px; }
.who span { font-size: 11.5px; color: var(--muted, #6c7893); display: flex; align-items: center; gap: 5px; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #bbb; display: inline-block; }
.dot.live { background: #34c759; }
.dot.connecting { background: #f5b955; }
.dot.fallback { background: #ff8f6b; }
.more { margin-left: auto; width: 30px; height: 30px; border: 1px solid var(--border, #e3e9f4); border-radius: 8px; background: transparent; cursor: pointer; color: var(--muted, #6c7893); display: grid; place-items: center; }

.messages { flex: 1; overflow-y: auto; padding: 16px 18px; display: flex; flex-direction: column; gap: 12px; }
.load-more { align-self: center; margin: 0; font-size: 12px; color: #2f6fed; cursor: pointer; }
.load-more.done { color: var(--muted, #6c7893); cursor: default; }

.msg { display: flex; gap: 8px; max-width: 70%; }
.msg.mine { flex-direction: row-reverse; align-self: flex-end; }
.bubble-wrap { display: flex; flex-direction: column; gap: 3px; }
.msg.mine .bubble-wrap { align-items: flex-end; }
.bubble { padding: 9px 12px; border-radius: 12px; background: var(--surface, #fff); border: 1px solid var(--border, #e3e9f4); font-size: 13.5px; line-height: 1.55; word-break: break-word; white-space: pre-wrap; }
.msg.mine .bubble { background: #2f6fed; border-color: #2f6fed; color: #fff; }
.bubble .media { color: var(--muted, #6c7893); }

/* ===== v1.9 媒体消息样式 ===== */
/* 媒体气泡去掉白底与内边距，让图片/播放器贴合气泡边缘 */
.bubble.media { padding: 0; background: transparent; border: none; overflow: hidden; }
.msg.mine .bubble.media { background: transparent; }

/* 图片：限制最大宽高，保持比例，圆角裁切 */
.msg-image { display: block; max-width: 240px; max-height: 240px; border-radius: 12px; cursor: zoom-in; }
.img-fallback {
  display: flex; align-items: center; justify-content: center;
  width: 160px; height: 110px; border-radius: 12px;
  background: #f2f5fa; color: var(--muted, #6c7893); font-size: 12px;
}

/* 音频：播放器 + 时长角标 */
.msg-audio { display: flex; align-items: center; gap: 8px; }
.msg-audio audio { height: 38px; max-width: 240px; border-radius: 19px; }
.msg-audio .dur { font-size: 11.5px; color: var(--muted, #6c7893); white-space: nowrap; }

/* 文件：图标 + 文件名，超出省略 */
.msg-file {
  display: flex; align-items: center; gap: 8px; max-width: 240px;
  padding: 10px 12px; border-radius: 12px; text-decoration: none;
  background: var(--surface, #fff); border: 1px solid var(--border, #e3e9f4);
  color: inherit;
}
.msg.mine .msg-file { background: rgba(255, 255, 255, .16); border-color: rgba(255, 255, 255, .3); }
.msg-file .file-icon { font-size: 17px; line-height: 1; }
.msg-file .file-name { font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 输入区工具栏：图片/音频按钮 */
.composer .tools { display: flex; gap: 6px; align-items: center; }
.tool-btn {
  width: 34px; height: 34px; flex: none;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border, #e3e9f4); border-radius: 9px;
  background: var(--surface, #fff); font-size: 16px; cursor: pointer;
  transition: background .15s, border-color .15s;
}
.tool-btn:hover:not(:disabled) { background: #f2f5fa; border-color: #2f6fed; }
.tool-btn:disabled { opacity: .5; cursor: not-allowed; }
.tool-btn .spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.stamp { display: flex; gap: 6px; align-items: center; font-size: 10.5px; color: var(--muted, #6c7893); padding: 0 4px; }
.stamp .state { font-style: normal; }
.stamp .state.read { color: #34c759; }
.stamp .state.delivered { color: #2f6fed; }
.stamp .state.sending { color: #b0b8c8; }
.stamp .state.failed { color: #ff5c5c; font-weight: 600; }
/* 发送失败的气泡：淡红底 + 红边，与正常气泡区分 */
.bubble.failed { background: #fff2f2 !important; border-color: #ffd0d0 !important; color: #d64545 !important; }

.composer { display: flex; gap: 10px; align-items: flex-end; padding: 12px 18px 16px; background: var(--surface, #fff); border-top: 1px solid var(--border, #e3e9f4); }
.composer textarea { flex: 1; resize: none; min-height: 40px; max-height: 120px; padding: 10px 12px; border: 1px solid var(--border, #e3e9f4); border-radius: 10px; font-size: 13.5px; font-family: inherit; outline: none; }
.composer textarea:focus { border-color: #2f6fed; }
.send { padding: 10px 20px; border: none; border-radius: 10px; background: #2f6fed; color: #fff; font-size: 13.5px; cursor: pointer; }
.send:disabled { background: #c5d4f0; cursor: not-allowed; }

@media (max-width: 760px) {
  .chat-page { flex-direction: column; }
  .side { width: 100%; flex: 0 0 45vh; border-right: none; border-bottom: 1px solid var(--border, #e3e9f4); }
  .msg { max-width: 86%; }
}
</style>