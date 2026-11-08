import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backend = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  // 构建产物直接落到 Spring Boot 的静态资源目录，mvn package 后一个 jar 就能跑
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      // 后端所有接口都在 /api 下，前端只认这一个前缀
      '/api': backend,
    },
  },
})
