// Run from sport-platform/frontend, with an isolated local backend on port 8080.
import { defineConfig } from '../../sport-platform/frontend/node_modules/@playwright/test/index.mjs'
import { fileURLToPath } from 'node:url'
import base from '../../sport-platform/frontend/playwright.qa05.config.ts'

export default defineConfig({
  ...base,
  testDir: fileURLToPath(new URL('../../sport-platform/frontend/e2e', import.meta.url)),
  outputDir: fileURLToPath(new URL('../../sport-platform/frontend/work/qa05/results', import.meta.url)),
  reporter: [['list'], ['json', { outputFile: fileURLToPath(new URL('../../sport-platform/frontend/work/qa05/results.json', import.meta.url)) }]],
  globalSetup: fileURLToPath(new URL('../../sport-platform/frontend/e2e/qa05-setup.ts', import.meta.url)),
  webServer: [
    {
      cwd: fileURLToPath(new URL('../../sport-platform/frontend', import.meta.url)),
      command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5174 --strictPort',
      url: 'http://127.0.0.1:5174',
      reuseExistingServer: false,
      timeout: 120_000,
      env: { VITE_API_BASE_URL: 'http://localhost:5173', VITE_API_MOCK: 'false' },
    },
    {
      cwd: fileURLToPath(new URL('../../sport-platform/frontend', import.meta.url)),
      command: 'bash ../../infra/tests/qa05-caddy.sh',
      gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
})
