import { onMounted, onUnmounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 数据大屏实时通道。
 * 优先使用 WebSocket 接收服务端节拍推送，连续重连失败后回调 onFallback 交由页面降级轮询。
 *
 * 修复说明：原实现从 localStorage 读取 token，但本系统的 access token 只存 Pinia 内存、
 * 不落 localStorage（见 stores/user.js），导致 token 恒为空、握手必然被拒——改为读 store。
 */
export function useDashboardSocket({ onMessage, onFallback } = {}) {
  const status = ref('connecting') // connecting | live | fallback
  const userStore = useUserStore()

  let ws = null
  let retry = 0
  let retryTimer = null
  let stopped = false
  let fallbackNotified = false

  const buildUrl = () => {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const token = userStore.token || ''
    return `${protocol}://${location.host}/ws/dashboard?token=${encodeURIComponent(token)}`
  }

  const notifyFallback = () => {
    if (fallbackNotified) return
    fallbackNotified = true
    status.value = 'fallback'
    onFallback?.()
  }

  const connect = () => {
    if (stopped) return
    status.value = 'connecting'

    try {
      ws = new WebSocket(buildUrl())
    } catch (e) {
      notifyFallback()
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      retry = 0
      fallbackNotified = false
      status.value = 'live'
    }

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload && payload.data) {
          onMessage?.(payload.data)
        }
      } catch (e) {
        // 忽略非法报文，保持连接
      }
    }

    ws.onerror = () => {
      ws?.close()
    }

    ws.onclose = (event) => {
      ws = null
      if (stopped) return
      // 1008：服务端判定身份非教务，属于终结态，不再重连
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
    const delay = Math.min(1000 * 2 ** retry, 15000)
    retry += 1
    clearTimeout(retryTimer)
    retryTimer = setTimeout(connect, delay)
  }

  const close = () => {
    stopped = true
    clearTimeout(retryTimer)
    if (ws) {
      ws.onclose = null
      ws.close()
      ws = null
    }
  }

  onMounted(connect)
  onUnmounted(close)

  return { status, close }
}
