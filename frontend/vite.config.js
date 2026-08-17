import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8180', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8180', ws: true }
    }
  }
})
