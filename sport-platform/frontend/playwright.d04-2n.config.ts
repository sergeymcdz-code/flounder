import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', testMatch: 'd04-2n.spec.ts', workers: 1, timeout: 90_000,
  outputDir: 'work/d04-2n-results', reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:5176', locale: 'ru-RU', timezoneId: 'Europe/Moscow', launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }, trace: 'retain-on-failure' },
  webServer: { command: 'VITE_API_MOCK=true npm run dev -- --host 127.0.0.1 --port 5176', url: 'http://127.0.0.1:5176', reuseExistingServer: !process.env.CI },
})
