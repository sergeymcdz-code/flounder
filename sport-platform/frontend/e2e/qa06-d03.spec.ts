import { createFixtures } from '../src/mocks/fixtures'
import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { eventId, unitId, openAuditPage, scenario, navigate } from './qa06-helpers'
const screens = [
 ['home', `/organizer/events/${eventId}`, 'organizer'],
 ['entries', `/organizer/events/${eventId}/entries`, 'organizer'],
 ['heats', `/organizer/events/${eventId}/heats`, 'organizer'],
 ['judging', '/operator/results', 'judge'],
 ['judge', `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge'],
 ['registrations', '/my/registrations', 'participant'],
 ['create', '/organizer/events/new', 'organizer'],
 ['live', '/e/demo/live', ''],
 ['results', '/e/demo/results', ''],
] as const
for (const width of [1366, 375]) for (const [name, path, role] of screens) for (const theme of (role ? ['light'] : ['light', 'dark'])) {
 test(`D03 axe ${name} ${width} ${theme}`, async ({ page }) => {
  await page.setViewportSize({ width, height: width === 375 ? 812 : 768 })
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message))
  await openAuditPage(page, path, role)
  await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme)
  await page.evaluate(() => document.fonts.ready)
  const result = await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()
  expect(result.violations.filter(v => ['serious','critical'].includes(v.impact ?? ''))).toEqual([])
  expect(errors).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
 })
}
test('D03 mobile judge primary remains visible after lane eight focus', async ({ page }) => {
 await page.setViewportSize({ width: 375, height: 812 })
 await openAuditPage(page, `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge')
 const heat = createFixtures().heats[0]
 await scenario(page, `competition-units/${unitId}`, { ...heat, eventId, status: 'IN_PROGRESS', entries: Array.from({ length: 8 }, (_, index) => ({ ...heat.entries[index % heat.entries.length], entryId: `lane-${index + 1}`, laneOrPosition: index + 1 })) })
 await navigate(page, '/operator/results')
 await expect(page.getByRole('heading', { name: 'Судейство', exact: true })).toBeVisible()
 await navigate(page, `/operator/competition-units/${unitId}?eventId=${eventId}`)
 const inputs = page.getByRole('textbox', { name: /Время, дорожка/ })
 await expect(inputs).toHaveCount(8)
 await page.getByRole('textbox', { name: 'Время, дорожка 8', exact: true }).focus()
 const primary = page.locator('.fl-judge-actions .fl-btn-primary')
 await expect(primary).toHaveCount(1)
 await expect(primary).toBeInViewport()
 expect((await primary.boundingBox())?.height).toBeGreaterThanOrEqual(56)
})
test('D03 active mobile tab and primary focus ring', async ({ page }) => {
 await page.setViewportSize({ width: 375, height: 812 })
 await openAuditPage(page, `/organizer/events/${eventId}/results`, 'organizer')
 const tab = page.locator('.fl-tabs [aria-current="page"]')
 await expect(tab).toBeInViewport()
 await page.goto(`/organizer/events/${eventId}`)
 const primary = page.locator('.fl-checklist .fl-btn-primary')
 await primary.focus()
 await expect(primary).toBeFocused()
 expect(await primary.evaluate(el => getComputedStyle(el).boxShadow)).not.toBe('none')
 await navigate(page, '/operator/results')
 await expect(page.locator('.fl-btn:disabled').first()).toHaveCSS('opacity', '1')
})
test('D03 swimmer star persists on the live board and can be removed', async ({ page }) => {
 await openAuditPage(page, '/e/demo/live')
 await page.getByRole('searchbox').fill('Никитин')
 const star = page.getByRole('button', { name: /^Мои пловцы:/ }).first()
 await expect(star).toBeVisible()
 const name = (await star.getAttribute('aria-label'))!.replace('Мои пловцы: ', '')
 await star.click()
 await expect(star).toHaveAttribute('aria-pressed', 'true')
 const card = page.getByRole('region', { name: 'Мои пловцы', exact: true })
 await expect(card).toContainText(name)
 await expect(card).toContainText(/через \d+ заплывов|Сейчас|Предстоящих стартов/)
 expect(await page.evaluate(() => JSON.parse(localStorage.getItem('sportgate.my-swimmers')!))).toHaveLength(1)
 await navigate(page, '/e/demo/results')
 await navigate(page, '/e/demo/live')
 await expect(page.getByRole('region', { name: 'Мои пловцы', exact: true })).toContainText(name)
 await page.getByRole('searchbox').fill('Никитин')
 await page.getByRole('button', { name: `Мои пловцы: ${name}`, exact: true }).click()
 await expect(page.getByRole('region', { name: 'Мои пловцы', exact: true })).toHaveCount(0)
})
test('D03 public poster uses a named A4 page and results warning is visible in print', async ({ page }) => {
 await openAuditPage(page, '/e/demo/poster')
 await expect(page.locator('.fl-poster svg')).toBeVisible()
 await page.emulateMedia({ media: 'print' })
 await expect(page.locator('.fl-poster-controls')).toBeHidden()
 const pdf = await page.pdf({ path: 'work/d03-poster.pdf', preferCSSPageSize: true, printBackground: true })
 expect(pdf.toString('latin1').match(/\/Type\s*\/Page\b/g)).toHaveLength(1)
 expect((await page.locator('.fl-poster svg').boundingBox())!.width).toBeCloseTo(60 * 96 / 25.4, 0)
 await navigate(page, '/e/demo/results')
 await page.emulateMedia({ media: 'screen' })
 await expect(page.getByText('Предварительно', { exact: true })).toBeVisible()
 await page.emulateMedia({ media: 'print' })
 await expect(page.locator('.fl-unofficial')).toBeVisible()
 await expect(page.locator('.fl-unofficial')).toHaveText('Неофициальные результаты — публикуются сразу после заплыва и могут быть исправлены до утверждения судейской коллегией')
})

for (const theme of ['light', 'dark']) test(`D03 public header rule and current heat link stay visible in ${theme}`, async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openAuditPage(page, '/e/demo/live')
  await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme)
  const link = page.getByRole('link', { name: 'Открыть заплыв', exact: true })
  await expect(link).toBeVisible()
  const meta = page.locator('[class*="eventMeta"]')
  const header = page.getByRole('banner')
  const metaBox = (await meta.boundingBox())!
  const headerBox = (await header.boundingBox())!
  expect(metaBox.y + metaBox.height).toBeLessThanOrEqual(headerBox.y + headerBox.height - 3)
  expect(await meta.evaluate(el => getComputedStyle(el).backgroundColor)).toBe('rgba(0, 0, 0, 0)')
  expect(await link.evaluate(el => getComputedStyle(el).color)).not.toBe(await link.evaluate(el => getComputedStyle(el).backgroundColor))
  await page.setViewportSize({ width: 768, height: 900 })
  const brandBox = (await header.getByRole('link', { name: 'На главную', exact: true }).boundingBox())!
  const smallMetaBox = (await meta.boundingBox())!
  expect(smallMetaBox.x).toBeGreaterThanOrEqual(brandBox.x + brandBox.width)
})
