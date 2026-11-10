/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// API はバックエンド（Spring Boot, :8080）へプロキシする。
// 本番は CloudFront / ALB で同一オリジンにまとめる想定（docs/architecture.md）。
const apiProxy = {
  '/api': process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
  '/actuator': process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: { proxy: apiProxy },
  preview: { proxy: apiProxy },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/features/**', 'src/lib/**'],
      exclude: ['src/**/*.test.{ts,tsx}'],
      reporter: ['text', 'html', 'lcov'],
      // docs/qa_test_plan.md 3章
      thresholds: { lines: 70 },
    },
  },
})
