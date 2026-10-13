import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', testMatch: 'documents.spec.ts', outputDir: 'work/m04-playwright', workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:5180', viewport: { width: 375, height: 812 }, locale: 'ru-RU',
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }, trace: 'retain-on-failure',
  },
  webServer: {
    command: 'VITE_API_MOCK=false VITE_API_BASE_URL=http://127.0.0.1:5180 npm run dev -- --host 127.0.0.1 --port 5180',
    url: 'http://127.0.0.1:5180', reuseExistingServer: !process.env.CI,
  },
})
