import AxeBuilder from '@axe-core/playwright'
import { test, expect } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

for (const width of [1366, 375]) for (const theme of ['light', 'dark']) {
  test(`D04 showcase ${width}px ${theme}: geometry, tokens and evidence`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    await page.addInitScript(theme => localStorage.setItem('flounder.theme', theme), theme)
    await page.goto('/dev/ui')
    await expect(page.getByRole('heading', { name: 'Чёткий ритм. Точный результат.' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
    await page.evaluate(() => document.fonts.ready)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
    const region = page.getByRole('region', { name: 'Женщины · 50 м вольный стиль · Финал' })
    if (width === 375) {
      const undersized = await page.locator('main button, main a').evaluateAll(nodes => nodes.filter(node => {
        const { width, height } = node.getBoundingClientRect()
        return width < 44 || height < 44
      }).map(node => node.textContent))
      expect(undersized).toEqual([])
      await region.focus()
      await page.keyboard.press('ArrowRight')
      await expect.poll(() => region.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
    }
    await region.evaluate(node => { node.scrollTop = 100; node.scrollLeft = 0 })
    expect(await region.evaluate(node => node.scrollTop)).toBeGreaterThan(0)
    const header = await region.getByRole('columnheader').first().boundingBox()
    const box = await region.boundingBox()
    expect(header!.y).toBeGreaterThanOrEqual(box!.y)
    expect(header!.y - box!.y).toBeLessThanOrEqual(2)
    const time = region.getByRole('cell', { name: '00:27.84' })
    expect(await time.evaluate(node => getComputedStyle(node).textAlign)).toBe('right')
    expect(await time.evaluate(node => getComputedStyle(node).fontVariantNumeric)).toContain('tabular-nums')
    await region.evaluate(node => { node.scrollTop = 0 })
    const accessibility = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(accessibility.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
    await page.evaluate(() => { (document.activeElement as HTMLElement)?.blur(); scrollTo(0, 0) })
    const output = resolve(process.env.D04_SCREENSHOTS ?? '../../docs/design/screenshots')
    await mkdir(output, { recursive: true })
    await page.screenshot({ path: `${output}/D04-ui-${width}-${theme}.png`, fullPage: true, animations: 'disabled' })
    await page.getByRole('button', { name: theme === 'dark' ? 'Светлая тема' : 'Тёмная тема' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme === 'dark' ? 'light' : 'dark')
  })
}

for (const theme of ['light', 'dark']) test(`D04 showcase ${theme}: toast, confirmation and keyboard focus`, async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.addInitScript(theme => localStorage.setItem('flounder.theme', theme), theme)
  await page.goto('/dev/ui')
  await page.getByRole('button', { name: 'Toast: успех' }).click()
  await expect(page.getByRole('status')).toContainText('Изменения сохранены')
  const output = resolve(process.env.D04_SCREENSHOTS ?? '../../docs/design/screenshots')
  await mkdir(output, { recursive: true })
  await page.locator('.fl-toast').screenshot({ path: `${output}/D04-toast-375-${theme}.png` })
  await page.getByRole('button', { name: 'Диалог с причиной' }).click()
  const dialog = page.getByRole('dialog', { name: 'Снять заявку?' })
  await expect(dialog.getByRole('button', { name: 'Отмена' })).toBeFocused()
  await expect(dialog.getByRole('button', { name: 'Снять заявку' })).toBeDisabled()
  await dialog.screenshot({ path: `${output}/D04-dialog-375-${theme}.png` })
  await dialog.getByRole('textbox', { name: 'Причина' }).fill('По просьбе участника')
  await dialog.getByRole('button', { name: 'Снять заявку' }).click()
  await expect(page.getByRole('status')).toContainText('Действие подтверждено')
  await page.getByRole('button', { name: 'Диалог с причиной' }).click()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Диалог с причиной' })).toBeFocused()
})
