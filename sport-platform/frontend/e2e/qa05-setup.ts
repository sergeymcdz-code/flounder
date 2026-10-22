import { requiredCredential } from './credentials'
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
  // S02 fixtures are reused by title; the seven-step flow keeps its isolated B event.
  const multiOutput = execFileSync(process.execPath, [resolve(directory, 'node_modules/tsx/dist/cli.mjs'), 'seed.ts'], {
    cwd: directory, timeout: 600_000, encoding: 'utf8',
    env: { ...process.env, API_URL: apiUrl, ADMIN_EMAIL: requiredCredential('ADMIN_EMAIL'),
      ADMIN_PASSWORD: requiredCredential('ADMIN_PASSWORD'), DEMO_PASSWORD: requiredCredential('QA05_PASSWORD', 'DEMO_PASSWORD'),
      DEMO_SEED_ENABLED: 'true', REQUIRE_VENUE_META: 'true', DEMO_DATE: process.env.DEMO_DATE ?? '2026-10-19',
      DEMO_EVENTS: 'A,B,C', EVENT_TITLE_SUFFIX: '' },
  })
  mkdirSync('work/qa05', { recursive: true })
  const events = Object.fromEntries([...multiOutput.matchAll(/^S02 ([ABC]): (.+)$/gm)].map(match => [match[1], JSON.parse(match[2])]))
  if (Object.keys(events).length !== 3) throw new Error('S02 seed did not return all three event summaries')
  writeFileSync('work/qa05/s02-seed.json', JSON.stringify(events))
  writeFileSync('work/qa05/s02-seed.log', multiOutput)
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
      ADMIN_EMAIL: requiredCredential('ADMIN_EMAIL'),
      ADMIN_PASSWORD: requiredCredential('ADMIN_PASSWORD'),
      DEMO_PASSWORD: requiredCredential('QA05_PASSWORD', 'DEMO_PASSWORD'),
      DEMO_SEED_ENABLED: 'true',
      REQUIRE_VENUE_META: 'true',
      DEMO_DATE: process.env.DEMO_DATE ?? '2026-10-19',
      DEMO_EVENTS: 'B',
      EVENT_TITLE_SUFFIX: suffix,
    },
  })
  const match = /eventId=([^,]+), дата=([^,]+), публичная страница \/e\/([^ .]+)\./.exec(output)
  if (!match) throw new Error('Demo seed did not return eventId/publicSlug')
  mkdirSync('work/qa05', { recursive: true })
  writeFileSync('work/qa05/seed.json', JSON.stringify({ eventId: match[1], date: match[2], slug: match[3], suffix }))
  writeFileSync('work/qa05/seed.log', output)
}
