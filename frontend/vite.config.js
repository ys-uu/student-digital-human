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
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 扫码接口路径无 /api 前缀，单独代理
      '/qr': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
