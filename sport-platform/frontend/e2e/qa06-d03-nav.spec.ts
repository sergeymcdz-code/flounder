import { test, expect } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { eventId, unitId, openAuditPage, scenario } from './qa06-helpers'

const screens = [
  ['home', `/organizer/events/${eventId}`, 'organizer'],
  ['entries', `/organizer/events/${eventId}/entries`, 'organizer'],
  ['heats', `/organizer/events/${eventId}/heats`, 'organizer'],
  ['judging', '/operator/results', 'judge'],
  ['judge', `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge'],
  ['registrations', '/my/registrations', 'participant'],
  ['live', '/e/demo/live', ''],
  ['results', '/e/demo/results', ''],
] as const
for (const width of [1366, 375]) test(`D03 navigation matrix ${width}`, async ({ browser }) => {
  for (const [name, path, role] of screens) {
    const context = await browser.newContext({ viewport: { width, height: width === 375 ? 812 : 768 }, locale: 'ru-RU', timezoneId: 'Europe/Moscow' })
    const page = await context.newPage(); const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await openAuditPage(page, path, role)
    await page.evaluate(() => document.fonts.ready)
    expect(errors, name).toEqual([])
    expect(await page.evaluate(() => document.documentElement.scrollWidth), name).toBeLessThanOrEqual(width)
    if (process.env.D03_CAPTURE === '1') { await mkdir('work/d03-nav', { recursive: true }); await page.screenshot({ path: `work/d03-nav/${name}-${width}.png` }) }
    await context.close()
  }
})

test('D03 judge returns to the competition results without manager permissions', async ({ page }) => {
  await openAuditPage(page, `/organizer/events/${eventId}/results`, 'judge')
  await expect(page.getByRole('heading', { name: 'Результаты соревнования' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Главная', exact: true })).toHaveCount(0)
})

for (const [role, deepPath, home] of [
  ['organizer', `/organizer/events/${eventId}/entries`, '/organizer/events'],
  ['operator', `/organizer/events/${eventId}/entries`, '/organizer/events'],
  ['platform_admin', '/admin/users', '/organizer/events'],
  ['judge', `/operator/competition-units/${unitId}?eventId=${eventId}`, '/operator/results'],
  ['participant', '/account/password', '/my/registrations'],
  ['', '/login', '/'],
  ['', '/register', '/'],
]) for (const source of ['workspace', 'public']) test(`UX-16 logo ${role || 'guest'} ${deepPath} from ${source}`, async ({ page }) => {
  await openAuditPage(page, source === 'public' ? '/e/demo/results' : deepPath, role)
  // Two events keep the role landing page visible instead of the single-event shortcut.
  const { createFixtures } = await import('../src/mocks/fixtures')
  const event = createFixtures().event
  const events = [event, { ...event, id: 'second-event', title: 'Второе соревнование' }]
  await scenario(page, 'organizer/events', events)
  await scenario(page, 'events', events)
  const logo = page.getByRole('banner').getByRole('link', { name: 'На главную', exact: true })
  await expect(logo).toHaveAttribute('href', home)
  await expect(logo).toHaveCSS('cursor', 'pointer')
  await logo.click()
  await expect(page).toHaveURL(new RegExp(`${home === '/' ? '/$' : home + '$'}`))
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
})
