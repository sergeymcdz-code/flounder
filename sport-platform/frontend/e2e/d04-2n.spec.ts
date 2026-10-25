import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mkdirSync } from 'node:fs'
import { openAuditPage, navigate, scenario } from './qa06-helpers'
const registrationId = '00000004-1111-4111-8111-000000000001'
const screens = [
  ['my', '/my/registrations', 'participant'],
  ['review', '/admin/registrations/review', 'platform_admin'],
  ['payments', '/operator/payments', 'operator'],
  ['detail', `/operator/registrations/${registrationId}`, 'operator'],
  ['organization', '/o/volna', ''],
] as const
const shotDir = 'work/d04-2n-shots'
mkdirSync(shotDir, { recursive: true })
async function enrich(page: Page) {
  const rows = await page.evaluate('(async () => { const {createFixtures} = await import("/src/mocks/fixtures.ts"); const f = createFixtures(); return f.registrations.slice(0,2).map(r => ({...r, eventTitle: f.event.title, disciplineName: f.event.disciplines[0].name, eventStartAt: f.event.eventStartAt, venueTimezone: "Europe/Samara", feeAmount: 1500, feeCurrency: "RUB", paymentStatus: "PENDING"})); })()')
  await scenario(page, 'registrations', rows)
  await scenario(page, 'registrations/me', rows)
  await scenario(page, `registrations/${registrationId}`, rows[0])
}
async function verify(page: Page, width: number, mobileTargets = true) {
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
  for (const table of await page.locator('main table').all()) {
    await expect(table.locator('caption')).toHaveCount(1)
    expect(await table.locator('thead th:not([scope])').count()).toBe(0)
  }
  if (width === 375 && mobileTargets) {
    const targets = await page.locator('main .fl-btn, main input:not([type=checkbox]):not([type=radio]), main select, main textarea, main summary').evaluateAll(nodes => nodes.filter(node => node.getClientRects().length).map(node => ({ text: node.textContent?.slice(0, 70), w: node.getBoundingClientRect().width, h: node.getBoundingClientRect().height })))
    expect(targets.filter(box => box.w < 43.9 || box.h < 43.9)).toEqual([])
  }
  const axe = await new AxeBuilder({ page }).include('main').analyze()
  expect(axe.violations.filter(item => ['serious', 'critical'].includes(item.impact ?? ''))).toEqual([])
}
for (const width of [375, 768, 1366]) for (const theme of ['light', 'dark']) {
  test(`D04-2n data, layout and accessibility ${width} ${theme}`, async ({ browser }) => {
    const errors: string[] = []
    for (const [name, path, role] of screens) {
      const context = await browser.newContext({ viewport: { width, height: width === 768 ? 1024 : width === 1366 ? 768 : 812 }, locale: 'ru-RU' })
      const page = await context.newPage()
      page.on('pageerror', error => errors.push(error.message))
      await openAuditPage(page, '/', role)
      await enrich(page)
      await navigate(page, path)
      await page.evaluate(theme => { localStorage.setItem('flounder.theme', theme); window.dispatchEvent(new StorageEvent('storage', { key: 'flounder.theme' })); document.documentElement.dataset.theme = theme }, theme)
      if (name === 'organization') await expect(page.getByRole('heading', { name: 'Клуб Волна', exact: true })).toBeVisible()
      else if (name === 'detail') await expect(page.getByRole('heading', { name: 'Данные заявки', exact: true })).toBeVisible()
      else await expect(page.locator('tbody tr')).toHaveCount(2)
      await expect(page.locator('.fl-loading-state')).toHaveCount(0)
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
      await verify(page, width)
      await page.evaluate(() => document.fonts.ready)
      await page.screenshot({ path: `${shotDir}/${width}-${theme}-${name}.png` })
      await context.close()
    }
    expect(errors).toEqual([])
  })
}
for (const mode of ['loading', 'empty', 'error']) test(`D04-2n ${mode} states at 375`, async ({ browser }) => {
  for (const [name, path, role] of screens.filter(([name]) => name !== 'detail')) {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, locale: 'ru-RU' })
    const page = await context.newPage()
    await openAuditPage(page, '/', role)
    const resource = name === 'my' ? 'registrations/me' : name === 'organization' ? 'public/organizations/volna' : 'registrations'
    const empty = name === 'organization' ? await page.evaluate('(async () => { const {demoOrganization} = await import("/src/mocks/calendar-data.ts"); return {...demoOrganization, upcoming: [], past: [], stats: {eventsTotal: 0, eventsUpcoming: 0, participantsTotal: 0}} })()') : []
    await scenario(page, resource, mode === 'empty' ? empty : { message: 'Сервер временно недоступен' }, mode === 'error' ? '500' : mode === 'loading' ? 'loading' : 'json')
    await navigate(page, path)
    if (mode === 'loading') await expect(page.locator('.fl-loading-state')).toBeVisible()
    if (mode === 'error') await expect(page.getByRole('button', { name: 'Повторить', exact: true })).toBeVisible()
    if (mode === 'empty') await expect(page.locator('.fl-empty').first()).toBeVisible()
    await verify(page, 375, mode !== 'loading')
    await page.screenshot({ path: `${shotDir}/375-${mode}-${name}.png` })
    await context.close()
  }
})
test('D04-2n actions close with Escape, restore focus and keep bulk selection scoped', async ({ browser }) => {
  let context = await browser.newContext({ viewport: { width: 375, height: 812 } })
  let page = await context.newPage()
  await openAuditPage(page, '/', 'participant'); await enrich(page); await navigate(page, '/my/registrations')
  await page.getByText('Подробнее', { exact: true }).first().click()
  const withdraw = page.getByRole('button', { name: 'Отозвать заявку' }).first()
  await withdraw.click(); await expect(page.getByRole('dialog', { name: 'Отозвать заявку?' })).toBeVisible()
  await page.screenshot({ path: `${shotDir}/375-actions-my.png` })
  await page.keyboard.press('Escape'); await expect(withdraw).toBeFocused()
  await context.close(); context = await browser.newContext({ viewport: { width: 375, height: 812 } }); page = await context.newPage()
  await openAuditPage(page, '/', 'platform_admin'); await enrich(page); await navigate(page, '/admin/registrations/review')
  const more = page.getByRole('button', { name: /Действия заявки:/ }).first()
  await more.click(); await expect(page.getByRole('menuitem', { name: 'Запросить уточнение' })).toBeVisible()
  await page.screenshot({ path: `${shotDir}/375-actions-review.png` })
  await page.keyboard.press('Escape'); await expect(more).toBeFocused()
  await more.click(); await page.getByRole('menuitem', { name: 'Запросить уточнение' }).click()
  await expect(page.getByRole('radio', { name: /Запросить уточнение/ })).toBeChecked()
  await expect(page.getByRole('heading', { name: 'Решение по заявке' })).toBeFocused()
  await context.close(); context = await browser.newContext({ viewport: { width: 375, height: 812 } }); page = await context.newPage()
  await openAuditPage(page, '/', 'operator'); await enrich(page); await navigate(page, '/operator/payments')
  await expect(page.locator('tbody tr').first()).toBeVisible()
  await page.getByRole('combobox', { name: 'Соревнование', exact: true }).selectOption({ index: 1 })
  await page.getByRole('checkbox').first().check()
  const bulk = page.getByRole('button', { name: 'Отметить оплаченными' })
  await bulk.click(); await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape'); await expect(bulk).toBeFocused()
  await page.screenshot({ path: `${shotDir}/375-actions-payments.png` })
  await page.getByRole('searchbox').fill('несуществующий участник')
  await expect(bulk).toBeDisabled()
  await context.close()
})
