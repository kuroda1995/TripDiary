import { defineConfig, devices } from '@playwright/test'

/**
 * E2E テスト（docs/qa_test_plan.md）。
 *
 * 前提: バックエンド（:8080）と DB が起動していること。
 * フロントエンドは本番ビルドを vite preview（:4173）で配信し、/api・/actuator をバックエンドへプロキシする。
 *
 * E2E_BASE_URL を指定すると、ローカルのサーバーを起動せず、その URL（AWS のデモ環境など）に対して実行する
 * （docs/deploy.md）。テスト用のユーザー・投稿がその環境に作られる点に注意。
 */
const isCI = !!process.env.CI
const remoteBaseUrl = process.env.E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  reporter: isCI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: remoteBaseUrl ?? 'http://localhost:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: remoteBaseUrl
    ? undefined
    : {
        command: 'npm run build && npm run preview -- --port 4173 --strictPort',
        url: 'http://localhost:4173',
        reuseExistingServer: !isCI,
        timeout: 120_000,
      },
})
