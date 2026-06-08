import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// When deploying to GitHub Pages the app lives at:
//   https://<username>.github.io/<repo-name>/
// VITE_BASE_PATH is injected by the CI workflow (e.g. "/tradepro/")
// For local dev it stays as "/"

export default defineConfig({
  plugins: [react()],

  // Use the env var when set (CI), otherwise "/"
  base: process.env.VITE_BASE_PATH || '/',

  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom'],
          router: ['react-router-dom'],
          redux: ['@reduxjs/toolkit', 'react-redux'],
          charts: ['recharts'],
        },
      },
    },
  },
})
