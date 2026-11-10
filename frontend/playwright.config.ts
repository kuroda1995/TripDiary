import { defineConfig, devices } from '@playwright/test'

/**
 * E2E テスト（docs/qa_test_plan.md）。
 *
 * 前提: バックエンド（:8080）と DB が起動していること。
 * フロントエンドは本番ビルドを vite preview（:4173）で配信し、/api・/actuator をバックエンドへプロキシする。
 */
const isCI = !!process.env.CI

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  reporter: isCI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build && npm run preview -- --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !isCI,
    timeout: 120_000,
  },
})
