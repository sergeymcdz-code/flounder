import { expect, test } from '@playwright/test'
import { openAuditPage } from './qa06-helpers'

const eventPath = '/organizer/events/11111111-1111-4111-8111-111111111111'

for (const width of [1366, 375]) {
  test(`UX-19 payment context, tab scrolling and user menu at ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 768 })
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await openAuditPage(page, eventPath, 'organizer')
    expect((await page.getByRole('button', { name: 'Сохранить', exact: true }).boundingBox())!.width).toBeLessThan(200)
    const tabs = page.getByRole('navigation', { name: 'Разделы соревнования' })
    const overflow = tabs.locator('..').locator('..')
    await expect(overflow).toHaveAttribute('data-overflow-end', 'true')
    await tabs.getByRole('link', { name: 'Оплата', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Настройки оплаты' })).toBeVisible()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(errors).toEqual([])
    await page.evaluate(path => { history.pushState(null, '', `${path}/analytics`); dispatchEvent(new PopStateEvent('popstate')) }, eventPath)
    const active = tabs.getByRole('link', { name: 'Аналитика', exact: true })
    await expect(active).toHaveAttribute('aria-current', 'page')
    await expect.poll(async () => {
      const rect = await active.boundingBox(), container = await tabs.boundingBox()
      return !!rect && !!container && rect.x >= container.x - 1 && rect.x + rect.width <= container.x + container.width + 1
    }).toBe(true)
    await expect(overflow).toHaveAttribute('data-overflow-start', 'true')
    await page.locator('header summary').filter({ hasText: 'Организатор' }).click()
    const logout = page.getByRole('button', { name: 'Выйти', exact: true })
    await expect(logout).toBeVisible()
    const logoutBox = await logout.boundingBox()
    if (width === 375) expect(logoutBox!.width).toBeGreaterThan(140)
    await expect(page.getByRole('link', { name: 'Сменить пароль' })).toBeVisible()
  })

  test(`UX-19 entry actions do not resize rows and close on Escape/outside at ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 768 })
    await openAuditPage(page, `${eventPath}/entries`, 'organizer')
    const trigger = page.getByRole('button', { name: 'Ещё ▾', exact: true }).first()
    await trigger.scrollIntoViewIfNeeded()
    const row = trigger.locator('xpath=ancestor::tr')
    const before = await row.boundingBox()
    await trigger.click()
    const panel = page.getByRole('region', { name: 'Действия заявки' })
    await expect(panel).toBeVisible()
    expect((await row.boundingBox())!.height).toBe(before!.height)
    if (width === 375) {
      const rect = await panel.boundingBox()
      expect(rect!.x).toBe(0)
      expect(rect!.width).toBe(width)
      expect(rect!.y + rect!.height).toBe(812)
    }
    await page.keyboard.press('Escape')
    await expect(panel).toHaveCount(0)
    await expect(trigger).toBeFocused()
    await trigger.click()
    await page.mouse.click(width - 2, 20)
    await expect(panel).toHaveCount(0)
  })

  test(`UX-19 heat back link receives clicks at ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 768 })
    await openAuditPage(page, '/e/demo/heats/M-50-FREE-1')
    await page.getByRole('link', { name: '← Стартовый протокол', exact: true }).click()
    await expect(page).toHaveURL(/\/e\/demo\/start-list$/)
  })
}
