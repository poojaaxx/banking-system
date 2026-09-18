/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Same-origin in production (the Spring Boot jar serves the built SPA).
      // In dev, forward API/actuator calls to the backend so cookies/CSRF
      // behave exactly as they will in production.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
})
