import { test, expect } from '@playwright/test'
import { eventId, unitId, openAuditPage, navigate, scenario } from './qa06-helpers'

declare global {
  interface Window { __qa06Scroll: unknown[]; __qa06LiveCalls: number }
}

// Expected failures keep the audit runnable. QA06_EXPECT_FIX=1 exposes the red tests.
function knownDefect() { test.fail(process.env.QA06_EXPECT_FIX !== '1') }
const base = `/organizer/events/${eventId}`

for (const path of ['/organizer/events', '/operator/payments', '/events/demo/disciplines/M-50-FREE/results']) {
  test(`QA06-01 table scroll stays inside its container: ${path}`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, path, path.includes('organizer') ? 'organizer' : path.includes('operator') ? 'operator' : '')
    await expect(page.locator('table tbody tr').first()).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
    const wrapper = page.locator('[class*=tableWrap]')
    await wrapper.evaluate(node => { node.scrollLeft = node.scrollWidth })
    expect(await wrapper.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
    if (path.startsWith('/events/')) await expect(page.getByRole('columnheader', { name: 'Статус', exact: true })).toBeInViewport()
    else await expect(page.getByRole('link', { name: path.includes('organizer') ? 'Управлять событием' : 'Открыть', exact: true }).first()).toBeInViewport()
  })
}

test('QA06-02 QR keeps its square geometry at 375px', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, base, 'organizer')
  const box = await page.locator('svg').boundingBox()
  expect(box?.width).toBe(140)
  expect(box?.height).toBe(140)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test('QA06-03 admin form uses separated, full-width labelled controls', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, '/admin/registrations/review', 'platform_admin')
  const comment = page.getByLabel('Комментарий для участника')
  const box = await comment.boundingBox()
  expect(box?.width).toBeGreaterThan(300)
  expect(await page.locator('.review-decisions').evaluate(node => getComputedStyle(node).display)).toBe('grid')
  await expect(page.locator('body')).not.toContainText('payment flow')
  await expect(page.locator('body')).not.toContainText('production')
})

test('QA06-04 table headings use the readable muted token', async ({ page }) => {
  await openAuditPage(page, '/operator/payments', 'operator')
  await expect(page.locator('tbody tr').first()).toBeVisible()
  const color = await page.locator('th').first().evaluate(node => getComputedStyle(node).color)
  expect(color).toBe('rgb(92, 105, 128)')
})

test('QA06-06 dates use DD.MM.YYYY', async ({ page }) => {
  await openAuditPage(page, '/e/demo')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Осенний кубок')
  knownDefect()
  await expect(page.locator('header')).toContainText('15.10.2026')
})

for (const [name, path, button] of [
  ['event', base, 'Опубликовать'],
  ['start list', `${base}/heats`, 'Опубликовать стартовый протокол'],
  ['results', `${base}/results`, 'Опубликовать результаты вида'],
]) {
  test(`QA06-07 publication asks for confirmation: ${name}`, async ({ page }) => {
    await openAuditPage(page, path, 'organizer')
    const target = page.getByRole('button', { name: button, exact: true }).filter({ visible: true })
    const index = name === 'results' ? 2 : 0
    await expect(target.nth(index)).toBeEnabled()
    await target.nth(index).click()
    knownDefect()
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 1000 })
  })
}

test('QA06-08 empty heat cannot be published', async ({ page }) => {
  await openAuditPage(page, '/', 'operator')
  await scenario(page, `competition-units/${unitId}`, { id: unitId, eventDisciplineId: '00000003-1111-4111-8111-000000000001', label: 'Пустой заплыв', status: 'DRAFT', entries: [] })
  await navigate(page, `/operator/competition-units/${unitId}`)
  await expect(page.getByText('В заплыве пока нет участников.')).toBeVisible()
  knownDefect()
  await expect(page.getByRole('button', { name: 'Опубликовать', exact: true })).toBeDisabled()
})

test('QA06-09 network failure has Russian recovery guidance', async ({ page }) => {
  await openAuditPage(page, '/', 'organizer')
  await scenario(page, `events/${eventId}`, null, 'network')
  await navigate(page, base)
  await expect(page.getByRole('alert')).toBeVisible()
  knownDefect()
  await expect(page.getByRole('alert')).not.toContainText('Failed to fetch')
  await expect(page.getByRole('alert')).toContainText(/связ|подключен/i)
})

test('QA06-21 organizer exposes a single accessible main landmark', async ({ page }) => {
  await openAuditPage(page, base, 'organizer')
  knownDefect()
  await expect(page.getByRole('main')).toHaveCount(1)
})

test('QA06-11 focus ring has 3:1 contrast on white', async ({ page }) => {
  await openAuditPage(page, '/login')
  await page.keyboard.press('Tab')
  const outline = await page.evaluate(() => getComputedStyle(document.activeElement!).outlineColor)
  const channels = outline.match(/[\d.]+/g)!.map(Number)
  const alpha = channels[3] ?? 1
  const luminance = channels.slice(0, 3).map(v => (v * alpha + 255 * (1 - alpha)) / 255)
    .map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    .reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0)
  knownDefect()
  expect(1.05 / (luminance + 0.05)).toBeGreaterThanOrEqual(3)
})

test('QA06-12 reduced motion avoids smooth validator scrolling', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await openAuditPage(page, `${base}/heats`, 'organizer')
  await scenario(page, 'event-disciplines/:id/competition-units/validate', { violations: [{ severity: 'WARNING', code: 'SMALL_GROUP', message: 'Проверка прокрутки', unitId }] })
  await page.evaluate(() => {
    window.__qa06Scroll = []
    Element.prototype.scrollIntoView = function (options) { window.__qa06Scroll.push(options) }
  })
  await page.getByRole('button', { name: 'Проверить заплывы', exact: true }).click()
  await page.getByRole('button', { name: 'Предупреждение: Проверка прокрутки', exact: true }).first().click()
  knownDefect()
  expect(await page.evaluate(() => window.__qa06Scroll)).not.toContainEqual({ behavior: 'smooth', block: 'center' })
})

test('QA06-13 valid WITHDRAWN registration shows its status to the participant', async ({ page }) => {
  await openAuditPage(page, '/', 'participant')
  const rows = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures().registrations.slice(0, 2) })()')
  rows[0].status = 'WITHDRAWN'
  await scenario(page, 'registrations/me', rows)
  await navigate(page, '/my/registrations')
  await expect(page.getByRole('heading', { name: 'Мои заявки', exact: true })).toBeVisible()
  await expect(page.locator('[class*=card]').first()).toBeVisible()
  knownDefect()
  await expect(page.locator('body')).toContainText('Снята')
})

test('QA06-10 withdraw confirmation returns keyboard focus to its trigger', async ({ page }) => {
  await openAuditPage(page, `${base}/entries`, 'organizer')
  const trigger = page.getByRole('button', { name: 'Снять', exact: true }).first()
  await trigger.click()
  await expect(page.getByRole('dialog', { name: 'Снять заявку?' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  knownDefect()
  await expect(trigger).toBeFocused()
})

test('QA06-14 live waits 15 seconds before its next poll', async ({ page }) => {
  await openAuditPage(page, '/')
  await page.evaluate(() => {
    const original = window.fetch
    window.__qa06LiveCalls = 0
    window.fetch = (...args) => {
      if (String(args[0]).endsWith('/api/v1/public/events/demo/live')) window.__qa06LiveCalls++
      return original(...args)
    }
  })
  await navigate(page, '/e/demo/live')
  await expect(page.getByRole('table').first()).toBeVisible()
  const initialCalls = await page.evaluate(() => window.__qa06LiveCalls)
  expect(initialCalls).toBeGreaterThan(0)
  await page.waitForTimeout(5500)
  knownDefect()
  expect(await page.evaluate(() => window.__qa06LiveCalls)).toBe(initialCalls)
})

test('QA06-15 print removes the page background', async ({ page }) => {
  await openAuditPage(page, '/e/demo/start-list')
  await expect(page.getByRole('table').first()).toBeVisible()
  await page.emulateMedia({ media: 'print' })
  expect(await page.locator('body').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(255, 255, 255)')
  await expect(page.getByRole('navigation')).toBeHidden()
})

for (const target of ['organizer', 'participant']) {
  test(`QA06-16 long names do not widen the page: ${target}`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, '/', target)
    const fixture = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures() })()')
    if (target === 'organizer') {
      fixture.event.title = 'Соревнования ' + 'ОченьДлинноеНазвание'.repeat(8)
      await scenario(page, `events/${eventId}`, fixture.event)
      await navigate(page, base)
      await expect(page.getByRole('heading', { level: 1 })).toContainText('ОченьДлинноеНазвание')
    } else {
      fixture.registrations[0].participantSnapshot.lastName = 'РимскийКорсаков'.repeat(5)
      fixture.registrations[0].participantSnapshot.clubName = 'МуниципальнаяСпортивнаяШкола'.repeat(6)
      await scenario(page, 'registrations/me', fixture.registrations.slice(0, 2))
      await navigate(page, '/my/registrations')
      await expect(page.locator('body')).toContainText('МуниципальнаяСпортивнаяШкола')
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  })
}

test('QA06-18 payment loading is announced as a status', async ({ page }) => {
  await openAuditPage(page, '/', 'participant')
  await scenario(page, 'registrations/me', [], 'loading')
  await page.evaluate(() => {
    history.pushState(null, '', '/my/registrations/00000004-1111-4111-8111-000000000001/payment')
    dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByRole('heading', { name: 'Загружаем оплату', exact: true })).toBeVisible()
  knownDefect()
  await expect(page.getByRole('status')).toContainText('Загружаем оплату')
})

test('QA06-20 Samara starts keep venue time in a Moscow browser', async ({ page }) => {
  await openAuditPage(page, '/')
  await scenario(page, 'public/events/demo/schedule', [{
    sessionName: 'Утро в Самаре', disciplineName: '50 м вольный стиль', heatNumber: 1,
    sequenceInSession: 1, scheduledAt: '2026-10-17T10:00:00+04:00', status: 'PUBLISHED',
  }])
  await navigate(page, '/e/demo/schedule')
  await expect(page.locator('li')).toContainText('Заплыв 1')
  knownDefect()
  await expect(page.locator('li')).toContainText('10:00')
})
