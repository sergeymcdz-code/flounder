// Explicit CSP audit complements the unchanged seven-step QA05 assertions.
import { test, expect } from '../../sport-platform/frontend/node_modules/@playwright/test/index.mjs'
import { readFileSync } from 'node:fs'

const password = process.env.QA05_PASSWORD
if (!password) throw new Error('Задайте QA05_PASSWORD для браузерной проверки CSP.')

test('CSP: public and organizer QA05 pages use the enforced policy without violations', async ({ context, page }, info) => {
  const violations: unknown[] = []
  const consoleViolations: string[] = []
  const checked: string[] = []
  await context.exposeBinding('recordCspViolation', (_source, violation) => violations.push(violation))
  await context.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', event => {
      const record = (globalThis as unknown as { recordCspViolation: (value: unknown) => void }).recordCspViolation
      record({ directive: event.violatedDirective, blocked: event.blockedURI, document: event.documentURI })
    })
  })
  page.on('console', message => {
    if (/content security policy|content-security-policy|violates.*directive/i.test(message.text())) consoleViolations.push(message.text())
  })
  const events = JSON.parse(readFileSync('work/qa05/s02-seed.json', 'utf8'))
  async function visit(path: string) {
    const response = await page.goto(path)
    expect(response?.headers()['content-security-policy']).toContain("script-src 'self' 'sha256-")
    await expect(page.locator('#root > *').first()).toBeVisible()
    await page.waitForLoadState('networkidle')
    checked.push(path)
  }
  try {
    for (const key of ['A', 'B', 'C']) {
      for (const suffix of ['', '/schedule', '/start-list', '/live', '/results']) {
        await visit(`/e/${events[key].slug}${suffix}`)
      }
    }
    await visit('/login')
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    await page.getByLabel('Email').fill('org@flounder.demo')
    await page.getByLabel('Пароль', { exact: true }).fill(password)
    await page.getByRole('button', { name: 'Войти', exact: true }).click()
    await page.waitForURL(url => url.pathname !== '/login')
    for (const suffix of ['', '/program', '/entries', '/heats']) {
      await visit(`/organizer/events/${events.B.eventId}${suffix}`)
    }
    expect(violations).toEqual([])
    expect(consoleViolations).toEqual([])
  } finally {
    await info.attach('CSP audit', { body: JSON.stringify({ checked, violations, consoleViolations }, null, 2), contentType: 'application/json' })
    console.log(`CSP: проверено ${checked.length} страниц, нарушений ${violations.length}, сообщений консоли ${consoleViolations.length}`)
  }
})
