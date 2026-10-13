import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

export default function setup() {
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
      API_URL: process.env.QA05_API_URL ?? 'http://localhost:8080',
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
