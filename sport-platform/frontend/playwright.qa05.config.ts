import { defineConfig } from '@playwright/test'

const api = process.env.QA05_API_URL ?? 'http://localhost:8080'
export default defineConfig({
  testDir: './e2e',
  testMatch: ['qa05-demo.spec.ts', 's02-multi-events.spec.ts'],
  globalSetup: './e2e/qa05-setup.ts',
  workers: 1,
  retries: 0,
  timeout: 240_000,
  expect: { timeout: 7_000 },
  outputDir: 'work/qa05/results',
  reporter: [['list'], ['json', { outputFile: 'work/qa05/results.json' }]],
  use: {
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
    baseURL: 'http://localhost:5173',
    viewport: { width: 1366, height: 900 },
    locale: 'ru-RU',
    timezoneId: 'Europe/Samara',
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE },
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host localhost --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: false,
    timeout: 120_000,
    env: { VITE_API_BASE_URL: api, VITE_API_MOCK: 'false' },
  },
})
