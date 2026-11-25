import request from '@/utils/request'

/**
 * 私信 HTTP 兜底接口，对齐接口文档 18.5（v1.8 起游标改为 seq 语义）。
 *
 * 私信主通道是 WebSocket /ws/private（见 composables/usePrivateSocket.js），
 * 本文件用于：首屏加载会话列表与历史、WebSocket 不可用时降级收发。
 */

/** 18.5.1 会话列表 */
export function listConversations(params = {}) {
  return request.get('/api/v1/private/conversations', { params })
}

/** 18.5.2 创建或复用会话 */
export function createConversation(peerId) {
  return request.post('/api/v1/private/conversations', { peerId })
}

/**
 * 18.5.3 消息历史（seq 游标分页）
 * @param {Object} params { before_seq?, limit? }
 */
export function listMessages(conversationId, params = {}) {
  return request.get(`/api/v1/private/conversations/${conversationId}/messages`, { params })
}

/**
 * 18.5.7 按 seq 区间补洞
 * @param {Object} params { after_seq, up_to_seq?, limit? }
 */
export function listMessageRange(conversationId, params) {
  return request.get(`/api/v1/private/conversations/${conversationId}/messages/range`, { params })
}

/** 18.5.4 发送消息（HTTP 兜底） */
export function sendMessage(data) {
  return request.post('/api/v1/private/messages', data)
}

/** 18.5.5 标记已读（seq 游标） */
export function markRead(conversationId, lastReadSeq) {
  return request.post(`/api/v1/private/conversations/${conversationId}/read`, null, {
    params: lastReadSeq != null ? { last_read_seq: lastReadSeq } : {}
  })
}

/** 18.5.6 可私信联系人 */
export function listContacts() {
  return request.get('/api/v1/private/contacts')
}

/**
 * 19.1 申请媒体上传地址（v1.9 新增，支持图片与音频）
 *
 * 流程（预签名直传，文件流不经过后端）：
 *   ① 调本接口拿到 uploadUrl / accessUrl
 *   ② 用 fetch/axios PUT 把文件二进制传到 uploadUrl
 *      ★ 必须带与签发时一致的 Content-Type，否则 MinIO 验签失败
 *   ③ 发送消息时把 accessUrl 作为 media_url 传给 private_msg
 *
 * @param {Object} params { fileName, contentType, size? }
 */
export function getUploadUrl(params) {
  return request.get('/api/v1/private/upload-url', { params })
}

/**
 * 上传媒体文件到 MinIO（预签名直传）。
 *
 * 为什么不用 request 实例：项目 axios 实例带了 JWT 请求头与 baseURL，
 * 而这里要 PUT 到 MinIO 的绝对地址，不能带后端 token（MinIO 不认），
 * 因此用原生 fetch。
 *
 * @param {string} uploadUrl 后端签发的预签名 PUT 地址
 * @param {File} file 文件对象
 * @param {string} contentType MIME 类型，必须与申请时一致
 */
export async function putFileToMinio(uploadUrl, file, contentType) {
  const resp = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file
  })
  if (!resp.ok) {
    throw new Error(`文件上传失败（HTTP ${resp.status}）`)
  }
}