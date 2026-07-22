import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/node_modules/three/')) return 'vendor-three'
          if (id.includes('/node_modules/force-graph/') || id.includes('/node_modules/three-forcegraph/')) return 'vendor-force-graph'
          if (id.includes('/node_modules/d3-') || id.includes('/node_modules/internmap/')) return 'vendor-d3'
          if (id.includes('/node_modules/pdfjs-dist/') || id.includes('/node_modules/vue-pdf-embed/')) return 'vendor-pdf'
          if (id.includes('/node_modules/docx-preview/') || id.includes('/node_modules/jszip/')) return 'vendor-documents'
          if (id.includes('/node_modules/markdown-it/')) return 'vendor-markdown'
          if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/')) return 'vendor-vue'
          return undefined
        }
      }
    }
  },
  server: {
    port: 5173,
    allowedHosts: ['33kp156616.51vip.biz'],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
