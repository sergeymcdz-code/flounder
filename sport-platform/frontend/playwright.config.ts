import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'public.spec.ts',
  outputDir: 'work/playwright-results',
  use: {
    baseURL: 'http://localhost:5174',
    viewport: { width: 375, height: 812 },
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
  },
  webServer: [
    {
      command: 'npm run dev:demo -- --host 127.0.0.1 --port 5174',
      url: 'http://localhost:5174',
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'node --experimental-strip-types e2e/public-server.ts',
      url: 'http://localhost:5175/e/demo',
      reuseExistingServer: !process.env.CI,
    },
  ],
})
