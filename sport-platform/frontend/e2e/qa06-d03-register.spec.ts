import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mkdir } from 'node:fs/promises'
import { openAuditPage, scenario } from './qa06-helpers'

for (const width of [375, 1366]) test(`UX-17 registration layout, errors and accessibility ${width}`, async ({ page }) => {
  await page.setViewportSize({ width, height: width === 375 ? 812 : 768 })
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await openAuditPage(page, '/e/demo/register', 'participant')
  const card = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Заявка на участие' }) })
  const form = card.locator('form')
  const disciplines = page.getByRole('group', { name: 'Виды программы', exact: true })
  await expect(disciplines).toBeVisible()
  await expect(disciplines).toHaveAttribute('disabled', '')
  await expect(disciplines.getByRole('checkbox').first()).toBeDisabled()
  expect(await disciplines.getByRole('checkbox').count()).toBeGreaterThan(1)
  await expect(page.getByText('Укажите пол и дату рождения, чтобы увидеть доступные виды', { exact: true })).toBeVisible()
  const heading = await card.getByRole('heading', { level: 1 }).boundingBox()
  const athlete = await page.getByRole('group', { name: 'Данные спортсмена', exact: true }).boundingBox()
  const box = await card.boundingBox()
  expect(box!.width).toBeLessThanOrEqual(640)
  expect(Math.abs(box!.x + box!.width / 2 - width / 2)).toBeLessThan(2)
  expect(heading!.y + heading!.height).toBeLessThan(athlete!.y)
  const fields = form.locator('input:not([type=checkbox]), select')
  for (const field of await fields.all()) {
    const bounds = await field.boundingBox()
    expect(bounds!.x).toBeGreaterThanOrEqual(box!.x + 16)
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(box!.x + box!.width - 16)
  }
  const gender = await page.getByLabel('Пол', { exact: true }).boundingBox()
  const club = await page.getByLabel('Клуб / город', { exact: true }).boundingBox()
  if (width >= 640) expect(gender!.y).toBe(club!.y)
  else expect(gender!.y + gender!.height).toBeLessThan(club!.y)
  await expect(page.getByLabel('Дата рождения', { exact: true })).toHaveAttribute('type', 'date')
  for (const theme of ['light', 'dark']) {
    await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme)
    const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(audit.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
  }
  await page.evaluate(() => document.documentElement.dataset.theme = 'light')
  if (process.env.D03_CAPTURE === '1') {
    await page.evaluate(() => document.fonts.ready)
    await mkdir('../../docs/design/screenshots', { recursive: true })
    await page.screenshot({ path: `../../docs/design/screenshots/D03-v2-register-${width}.png`, fullPage: true })
  }
  const send = page.getByRole('button', { name: 'Отправить заявку', exact: true })
  await send.click()
  await expect(page.getByLabel('Фамилия', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.locator('#registration-error-lastName')).toBeVisible()
  await page.getByLabel('Фамилия', { exact: true }).fill('Демов')
  await page.getByLabel('Имя', { exact: true }).fill('Тимофей')
  await page.getByLabel('Дата рождения', { exact: true }).fill('2014-04-18')
  await expect(disciplines).toHaveAttribute('disabled', '')
  await page.getByLabel('Пол', { exact: true }).selectOption('M')
  await expect(disciplines).not.toHaveAttribute('disabled')
  await disciplines.getByRole('checkbox').nth(1).check()
  await page.getByPlaceholder('мм:сс.сс').fill('003215')
  // Clicking the consent text toggles its associated checkbox.
  await page.getByText('Я даю согласие на обработку персональных данных', { exact: true }).click()
  await expect(page.getByRole('checkbox', { name: 'Я даю согласие на обработку персональных данных' })).toBeChecked()
  if (width === 375) expect((await send.boundingBox())!.width).toBeGreaterThan(250)
  await scenario(page, 'public/events/demo/registrations', [{ id: 'new-registration' }], 'json', 'post')
  await send.click()
  await expect(page.getByRole('heading', { name: 'Заявка отправлена' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
  expect(errors).toEqual([])
})
