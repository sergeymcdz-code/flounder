import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

export default function setup() {
  const api = process.env.QA07_API_URL ?? 'http://localhost:8080'
  if (!['localhost', '127.0.0.1'].includes(new URL(api).hostname)) {
    throw new Error('QA07 создаёт тестовые данные только на локальном backend.')
  }
  const directory = resolve('../../scripts/demo')
  const cli = resolve(directory, 'node_modules/tsx/dist/cli.mjs')
  if (!existsSync(cli)) throw new Error('Не установлены зависимости сида: выполните cd scripts/demo && npm ci из корня репозитория.')
  const suffix = ` · QA07 ${new Date().toISOString()}`
  const output = execFileSync(process.execPath, [cli, 'seed.ts'], {
    cwd: directory, timeout: 120_000, encoding: 'utf8',
    env: {
      ...process.env, API_URL: api, ADMIN_EMAIL: 'admin@flounder.demo',
      ADMIN_PASSWORD: process.env.QA07_PASSWORD ?? 'Demo2026!',
      DEMO_PASSWORD: process.env.QA07_PASSWORD ?? 'Demo2026!',
      DEMO_SEED_ENABLED: 'true', REQUIRE_VENUE_META: 'true',
      DEMO_DATE: process.env.DEMO_DATE ?? '2026-10-24', EVENT_TITLE_SUFFIX: suffix,
    },
  })
  const match = /eventId=([^,]+), дата=([^,]+), публичная страница \/e\/([^ .]+)\./.exec(output)
  if (!match) throw new Error('Demo seed did not return eventId/publicSlug')
  mkdirSync('work/qa07', { recursive: true })
  writeFileSync('work/qa07/seed.json', JSON.stringify({ eventId: match[1], date: match[2], slug: match[3], suffix }))
  writeFileSync('work/qa07/seed.log', output)
}
