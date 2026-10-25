import { test, expect } from '@playwright/test'
import { openAuditPage, unitId, eventId, navigate } from './qa06-helpers'

test.use({ viewport: { width: 375, height: 812 }, colorScheme: 'light' })

test('D02: theme is set before the application loads', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.route('**/src/main.tsx', route => route.abort())
  await page.goto('/e/demo/live')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.locator('#root')).toBeEmpty()
})

test('D04: explicit theme survives navigation and reload while the live board stays dark', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await openAuditPage(page, '/e/demo/live')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: 'Светлая тема' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(238, 240, 238)')
  const board = page.getByRole('region', { name: 'Текущий заплыв', exact: true })
  await expect(board).toHaveCSS('background-color', 'rgb(24, 33, 38)')
  await expect(board).toHaveCSS('color-scheme', 'dark')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(board).toHaveCSS('background-color', 'rgb(24, 33, 38)')
  await expect(board).toHaveCSS('color-scheme', 'dark')
  await page.getByRole('link', { name: 'Результаты', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.reload()
  await expect(page.getByRole('button', { name: 'Тёмная тема' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  expect(await page.evaluate(() => localStorage.getItem('flounder.theme'))).toBe('light')
  await page.getByRole('button', { name: 'Тёмная тема' }).click()
  await navigate(page, '/login')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await navigate(page, '/e/demo')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

test('D02: system preference, route defaults and an explicit override', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await openAuditPage(page, '/e/demo')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: 'Светлая тема' }).click()
  await page.emulateMedia({ colorScheme: 'light' })
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})

test('D02: printing a dark results page uses white surfaces and hides the toggle', async ({ page }) => {
  await openAuditPage(page, '/e/demo/results')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.emulateMedia({ media: 'print' })
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await expect(page.getByRole('button', { name: 'Светлая тема' })).toBeHidden()
  expect(await page.locator('html').evaluate(el => getComputedStyle(el).getPropertyValue('--color-warning-soft').trim())).toBe('#ffffff')
})

for (const route of ['start-list', 'results']) {
  test(`D02 over D01/M03: ${route} keeps PDF and ghost print actions in both themes at 375px`, async ({ page }) => {
    await openAuditPage(page, `/e/demo/${route}`)
    for (const theme of ['light', 'dark']) {
      if (await page.locator('html').getAttribute('data-theme') !== theme)
        await page.getByRole('banner').getByRole('button', { name: /^(Светлая|Тёмная) тема$/ }).click()
      await expect(page.getByRole('button', { name: 'Скачать PDF', exact: true })).toBeEnabled()
      await expect(page.getByRole('button', { name: 'Печать', exact: true })).toHaveClass(/fl-btn-ghost/)
      expect(await page.locator('html').evaluate(el => getComputedStyle(el).colorScheme)).toBe(theme)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }
  })
}

test('D04: live lanes keep table semantics and focus on actions without horizontal overflow', async ({ page }) => {
  await openAuditPage(page, '/e/demo/live')
  const table = page.locator('.fl-lanes').first()
  for (const width of [375, 768, 1366]) {
    await page.setViewportSize({ width, height: width === 1366 ? 768 : 1024 })
    await expect(table).toHaveAttribute('role', 'table')
    await expect(table).toHaveAccessibleName(/Дорожки заплыва/)
    await expect(table.locator('tbody tr')).toHaveCount(8)
    await expect(table.getByRole('columnheader')).toHaveCount(width < 768 ? 3 : 5)
    await expect(table).not.toHaveAttribute('tabindex')
    const openHeat = page.getByRole('link', { name: 'Открыть заплыв', exact: true })
    await openHeat.focus()
    await expect(openHeat).toBeFocused()
    for (const key of ['ArrowRight', 'ArrowLeft']) {
      await openHeat.press(key)
      await expect(openHeat).toBeFocused()
      const dimensions = await table.evaluate(el => ({
        tableWidth: el.clientWidth,
        tableScrollWidth: el.scrollWidth,
        tableScrollLeft: el.scrollLeft,
        pageWidth: document.documentElement.clientWidth,
        pageScrollWidth: document.documentElement.scrollWidth,
        pageScrollLeft: window.scrollX,
      }))
      expect(dimensions.tableScrollWidth).toBeLessThanOrEqual(dimensions.tableWidth)
      expect(dimensions.tableScrollLeft).toBe(0)
      expect(dimensions.pageScrollWidth).toBeLessThanOrEqual(dimensions.pageWidth)
      expect(dimensions.pageScrollLeft).toBe(0)
    }
    const tableBox = (await table.boundingBox())!
    const cells = await table.locator('tbody th, tbody td').evaluateAll(nodes => nodes
      .filter(node => node.getClientRects().length)
      .map(node => ({ left: node.getBoundingClientRect().left, right: node.getBoundingClientRect().right })))
    for (const cell of cells) {
      expect(cell.left).toBeGreaterThanOrEqual(tableBox.x)
      expect(cell.right).toBeLessThanOrEqual(tableBox.x + tableBox.width)
    }
  }
})

test('UX-20: 375px judge keeps lane, participant and time inside a card', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge')
  const card = page.getByRole('article').first()
  await expect(card.locator('[class*=laneCell]')).toBeVisible()
  await expect(card.locator('[class*=participantName]')).toBeVisible()
  await expect(page.getByRole('table')).toHaveCount(0)
  const input = card.getByRole('textbox')
  await expect(input).toHaveAttribute('inputmode', 'decimal')
  expect((await input.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await input.fill('003215')
  const focused = (await input.boundingBox())!
  const bounds = (await card.boundingBox())!
  expect(focused.x).toBeGreaterThanOrEqual(bounds.x)
  expect(focused.x + focused.width).toBeLessThanOrEqual(bounds.x + bounds.width)
  await input.press('Tab')
  await expect(input).toHaveValue('00:32.15')
  await expect(page.locator('[aria-live="polite"]').filter({ hasText: 'Есть несохранённые изменения' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
