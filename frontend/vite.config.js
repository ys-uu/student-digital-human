import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 映射到 src
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  // 开发环境走代理，不用写死 baseURL
  server: {
    // 安全响应头：CSP 先以 Report-Only 模式观察，只在控制台报告违规、不拦截业务
    headers: {
      'Content-Security-Policy-Report-Only': [
        "default-src 'self'",
        // dev 模式先放行内联与 eval，观察期确认无依赖后再收紧
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
        // Element Plus 运行时会注入内联样式，unsafe-inline 必须保留
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob:",
        "font-src 'self' data:",
        // Vite HMR 依赖 WebSocket，需要放行 ws:
        "connect-src 'self' ws:",
        "object-src 'none'",
        "base-uri 'self'"
      ].join('; '),
      // 本系统不使用摄像头、麦克风、定位，全部禁用
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/qr': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
