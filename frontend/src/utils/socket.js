import { useUserStore } from '@/stores/user'

/**
 * 私信 WebSocket 连接工具（低层封装）。
 *
 * 注意：页面内推荐直接使用 composables/usePrivateSocket.js，
 * 它已经封装了心跳、重连退避、断线补拉与按 type 分发。
 * 本文件保留给需要自行管理连接生命周期的场景（如全局通知栏）。
 *
 * 修复说明：
 *   1) 原实现从 localStorage 读取 token，但本系统的 access token 只存 Pinia 内存、
 *      不落 localStorage，那样永远取不到 token，连接必然失败——改为读 store；
 *   2) 原实现写死 ws://localhost:8080，与 Vite 代理目标（/ws 指向后端）硬编码分离，
 *      部署到其他域名会失效——改为按 location 动态拼接。
 */
export function connectPrivateWs(token) {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const userStore = useUserStore()
  const accessToken = token || userStore.token || ''
  if (!accessToken) {
    throw new Error('未登录，无法建立私信连接')
  }

  const ws = new WebSocket(
    `${protocol}://${location.host}/ws/private?token=${encodeURIComponent(accessToken)}`
  )

  ws.onopen = () => {
    // 连接建立后服务端会主动下发 connected 报文，无需额外握手
  }
  ws.onmessage = (event) => {
    // 具体报文处理交给调用方，避免这里与 composable 逻辑重复
    return event.data
  }
  ws.onerror = () => {
    // 交由 onclose 统一处理重连
  }
  ws.onclose = () => {
    // 重连策略由调用方决定
  }
  return ws
}

/**
 * 向后兼容旧调用：原 socket.js 导出的是 connectWs。
 * @deprecated 请改用 composables/usePrivateSocket.js
 */
export function connectWs(token) {
  return connectPrivateWs(token)
}