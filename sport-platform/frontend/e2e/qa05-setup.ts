import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

export function validateApiUrl(apiUrl: string, allowRemote = process.env.QA05_ALLOW_REMOTE) {
  if (!['localhost', '127.0.0.1'].includes(new URL(apiUrl).hostname) && allowRemote !== '1') {
    throw new Error('QA05 запускается только против локального backend; для удалённого стенда задайте QA05_ALLOW_REMOTE=1')
  }
  return apiUrl
}

export default function setup() {
  const apiUrl = validateApiUrl(process.env.QA05_API_URL ?? 'http://localhost:8080')
  // A new isolated event per run avoids deleting or changing a previous demo.
  const directory = resolve('../../scripts/demo')
  const suffix = ` · QA05 ${new Date().toISOString()}`
  const output = execFileSync(process.execPath, [
    resolve(directory, 'node_modules/tsx/dist/cli.mjs'),
    'seed.ts', '--skip-import',
  ], {
    cwd: directory,
    timeout: 120_000,
    encoding: 'utf8',
    env: {
      ...process.env,
      API_URL: apiUrl,
      ADMIN_EMAIL: 'admin@flounder.demo',
      ADMIN_PASSWORD: process.env.QA05_PASSWORD ?? 'Demo2026!',
      DEMO_PASSWORD: process.env.QA05_PASSWORD ?? 'Demo2026!',
      DEMO_SEED_ENABLED: 'true',
      REQUIRE_VENUE_META: 'true',
      EVENT_TITLE_SUFFIX: suffix,
    },
  })
  const match = /eventId=([^,]+), дата=([^,]+), публичная страница \/e\/([^ .]+)\./.exec(output)
  if (!match) throw new Error('Demo seed did not return eventId/publicSlug')
  mkdirSync('work/qa05', { recursive: true })
  writeFileSync('work/qa05/seed.json', JSON.stringify({ eventId: match[1], date: match[2], slug: match[3], suffix }))
  writeFileSync('work/qa05/seed.log', output)
}
