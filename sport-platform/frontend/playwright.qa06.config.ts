import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'qa06-*.spec.ts',
  forbidOnly: !!process.env.CI,
  timeout: 240000,
  workers: process.env.CI ? 2 : 1,
  outputDir: 'work/qa06-playwright',
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'VITE_API_MOCK=true npm run dev -- --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
  },
})
