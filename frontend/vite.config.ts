import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // allow serving files from the workspace images folder (outside frontend root)
    fs: {
      // allow serving files from the frontend project root and the workspace images folder
      allow: [path.resolve(__dirname), path.resolve(__dirname, '../images')]
    },
    // proxy API calls to backend to avoid CORS / 403 issues during dev
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
