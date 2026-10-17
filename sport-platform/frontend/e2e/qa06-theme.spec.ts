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

test('D02: explicit theme survives navigation and reload, including a light live board', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await openAuditPage(page, '/e/demo/live')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: 'Светлая тема' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(238, 240, 238)')
  await expect(page.locator('section[aria-live]').first()).toHaveCSS('background-color', 'rgb(255, 255, 255)')
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
        await page.locator('header button').click()
      await expect(page.getByRole('button', { name: 'Скачать PDF', exact: true })).toBeEnabled()
      await expect(page.getByRole('button', { name: 'Печать', exact: true })).toHaveClass(/fl-btn-ghost/)
      expect(await page.locator('html').evaluate(el => getComputedStyle(el).colorScheme)).toBe(theme)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }
  })
}

test('D02: a mobile protocol keeps table semantics and supports keyboard scrolling', async ({ page }) => {
  await openAuditPage(page, '/e/demo/live')
  const table = page.locator('.fl-lanes').first()
  await expect(table).toHaveAttribute('role', 'table')
  await table.focus()
  await expect(table).toBeFocused()
  await table.press('ArrowRight')
  await expect.poll(() => table.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
})

test('D02: 375px judge keeps lane and participant visible while scrolling and entering time', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge')
  const row = page.locator('tbody tr').first()
  const lane = row.locator('td').nth(0)
  const participant = row.locator('td').nth(1)
  const before = await lane.boundingBox()
  const nameBefore = await participant.boundingBox()
  await page.locator('table').evaluate(table => { table.parentElement!.scrollLeft = 280 })
  const after = await lane.boundingBox()
  const nameAfter = await participant.boundingBox()
  expect(Math.abs(after!.x - before!.x)).toBeLessThan(1)
  expect(Math.abs(nameAfter!.x - nameBefore!.x)).toBeLessThan(1)
  expect(nameAfter!.x).toBeGreaterThanOrEqual(after!.x + after!.width - 1)
  const input = row.getByRole('textbox')
  await expect(input).toHaveAttribute('inputmode', 'decimal')
  expect((await input.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await input.fill('003215')
  const focused = (await input.boundingBox())!
  expect(focused.x).toBeGreaterThanOrEqual(nameAfter!.x + nameAfter!.width - 1)
  expect(focused.x + focused.width).toBeLessThanOrEqual(360)
  await input.press('Tab')
  await expect(input).toHaveValue('00:32.15')
  await expect(page.locator('[aria-live="polite"]').filter({ hasText: 'Есть несохранённые изменения' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
