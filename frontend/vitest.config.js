import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 前端组件/路由测试配置：覆盖门户首页布局、五岗位入口、公共模块左侧岗位导航等验收路径。
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.js']
  }
})
