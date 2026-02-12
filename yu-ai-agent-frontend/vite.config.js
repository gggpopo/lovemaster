import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    // 3000 默认常被 Grafana 占用，避免本地开发端口冲突
    port: 1218,
    strictPort: true,
    cors: true
  }
})
