import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'qa07-*.spec.ts',
  globalSetup: './e2e/qa07-setup.ts',
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 7_000 },
  projects: [
    { name: 'core', testMatch: ['qa07-admin.spec.ts', 'qa07-judge.spec.ts'] },
    { name: 'auth', testMatch: 'qa07-auth.spec.ts', grepInvert: /@rate-limit/, dependencies: ['core'] },
    { name: 'rate-limit-last', testMatch: 'qa07-auth.spec.ts', grep: /@rate-limit/, dependencies: ['auth'] },
  ],
  outputDir: 'work/qa07/results',
  reporter: [['list'], ['json', { outputFile: 'work/qa07/results.json' }]],
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
    env: { VITE_API_BASE_URL: process.env.QA07_API_URL ?? 'http://localhost:8080', VITE_API_MOCK: 'false' },
  },
})
