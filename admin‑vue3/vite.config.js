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
  // 顺便加上代理，开发环境不用写死baseURL，推荐
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
