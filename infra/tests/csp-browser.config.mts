import { defineConfig } from '../../sport-platform/frontend/node_modules/@playwright/test/index.mjs'
import { fileURLToPath } from 'node:url'
import base from './qa05-csp.config.mts'

export default defineConfig({
  ...base,
  testDir: fileURLToPath(new URL('.', import.meta.url)),
  testMatch: 'csp-browser.spec.mts',
  outputDir: fileURLToPath(new URL('../../sport-platform/frontend/work/csp-browser/results', import.meta.url)),
  reporter: [['list']],
})
