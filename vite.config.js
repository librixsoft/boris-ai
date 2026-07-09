import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src/main/resources/frontend'),
      'vue': 'vue/dist/vue.esm-bundler.js'
    },
  },
  build: {
    outDir: 'src/main/resources/static/dist',
    emptyOutDir: true,
    rollupOptions: {
      input: 'src/main/resources/frontend/main.js',
      output: {
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`
      }
    }
  },
  server: {
    proxy: {
      '/api': 'http://localhost:9999',
      '/boris': 'http://localhost:9999'
    }
  }
})
