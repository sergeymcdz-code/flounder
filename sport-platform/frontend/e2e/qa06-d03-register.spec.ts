import { test, expect, type Page, type Locator } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mkdir } from 'node:fs/promises'
import { openAuditPage, scenario } from './qa06-helpers'
import type { RegistrationRequest } from '../src/features/public/public.types'

async function checkLayout(page: Page, form: Locator, width: number) {
  const fields = form.locator('input:not([type=checkbox]):visible, select:visible')
  const group = form.locator('fieldset:visible')
  const box = (await group.boundingBox())!
  for (const field of await fields.all()) {
    const bounds = (await field.boundingBox())!
    expect(bounds.x).toBeGreaterThanOrEqual(box.x + 16)
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(box.x + box.width - 16)
  }
  for (const target of await form.locator('button:visible, input:visible, select:visible').all()) {
    const bounds = (await target.boundingBox())!
    expect(bounds.width).toBeGreaterThanOrEqual(44)
    expect(bounds.height).toBeGreaterThanOrEqual(44)
    await target.scrollIntoViewIfNeeded()
    expect(await target.evaluate(element => {
      const bounds = element.getBoundingClientRect()
      const hit = document.elementFromPoint(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
      return element === hit || element.contains(hit)
    })).toBe(true)
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
  for (const theme of ['light', 'dark']) {
    await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme)
    const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(audit.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
  }
  await page.evaluate(() => document.documentElement.dataset.theme = 'light')
}

for (const width of [375, 768, 1366]) test(`UX-17 registration layout, errors and accessibility ${width}`, async ({ page }) => {
  await page.setViewportSize({ width, height: width === 375 ? 812 : width === 768 ? 1024 : 768 })
  const errors: string[] = []
  const submissions: RegistrationRequest[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/public/events/demo/registrations')
      submissions.push(request.postDataJSON() as RegistrationRequest)
  })
  await openAuditPage(page, '/e/demo/register', 'participant')
  const card = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Заявка на участие' }) })
  const form = card.locator('form')
  const athlete = page.getByRole('group', { name: 'Участник', exact: true })
  const disciplines = page.getByRole('group', { name: 'Виды (до трёх)', exact: true })
  const steps = page.getByRole('navigation', { name: 'Шаги заявки' })
  const choices = disciplines.getByRole('button', { name: /Выбрать|Выбрано/ })
  await expect(athlete).toBeVisible()
  await expect(disciplines).toBeHidden()
  await expect(steps.locator('[aria-current=step]')).toHaveText('1 Спортсмен')
  const heading = (await card.getByRole('heading', { level: 1 }).boundingBox())!
  const box = (await card.boundingBox())!
  expect(box.width).toBeLessThanOrEqual(640)
  expect(heading.y + heading.height).toBeLessThan((await athlete.boundingBox())!.y)
  if (width < 1200) expect(Math.abs(box.x + box.width / 2 - width / 2)).toBeLessThan(2)
  else {
    const layout = (await card.locator('..').boundingBox())!
    const eventCard = (await page.getByRole('complementary', { name: 'О соревновании' }).boundingBox())!
    expect(Math.abs(layout.x + layout.width / 2 - width / 2)).toBeLessThan(2)
    expect(eventCard.width).toBe(280)
    expect(eventCard.x + eventCard.width).toBeLessThan(box.x)
  }
  expect((await card.locator('h1,h2,h3,legend').allTextContents()).join(' ')).not.toMatch(/по плаванию/i)
  const birth = page.getByLabel('Дата рождения', { exact: true })
  const gender = page.getByLabel('Пол', { exact: true })
  const club = page.getByLabel(/Клуб \/ город/)
  const birthBox = (await birth.boundingBox())!
  const genderBox = (await gender.boundingBox())!
  const lastNameBox = (await page.getByLabel('Фамилия', { exact: true }).boundingBox())!
  const firstNameBox = (await page.getByLabel('Имя', { exact: true }).boundingBox())!
  if (width >= 768) {
    expect(birthBox.y).toBe(genderBox.y)
    expect(lastNameBox.y).toBe(firstNameBox.y)
  } else {
    expect(birthBox.y + birthBox.height).toBeLessThan(genderBox.y)
    expect(lastNameBox.y + lastNameBox.height).toBeLessThan(firstNameBox.y)
  }
  expect(genderBox.y + genderBox.height).toBeLessThan((await club.boundingBox())!.y)
  await expect(birth).toHaveAttribute('type', 'date')
  await checkLayout(page, form, width)
  if (process.env.D03_CAPTURE === '1') {
    await page.evaluate(() => document.fonts.ready)
    await mkdir('../../docs/design/screenshots', { recursive: true })
    await page.screenshot({ path: `../../docs/design/screenshots/D03-v2-register-${width}.png`, fullPage: true })
  }

  // The same eligibility gate applies when the second step is opened directly.
  await steps.getByRole('button', { name: '2 Виды и время', exact: true }).click()
  await expect(disciplines).toBeVisible()
  await expect(disciplines).toHaveAttribute('disabled', '')
  expect(await choices.count()).toBeGreaterThan(1)
  for (const choice of await choices.all()) {
    await expect(choice).toBeDisabled()
    expect((await choice.boundingBox())!.height).toBeGreaterThanOrEqual(56)
  }
  await expect(disciplines.getByRole('checkbox')).toHaveCount(0)
  await expect(page.getByText('Укажите пол и дату рождения, чтобы увидеть доступные виды', { exact: true })).toBeVisible()
  await steps.getByRole('button', { name: '1 Спортсмен', exact: true }).click()
  await page.getByRole('button', { name: 'Далее: Виды и время', exact: true }).click()
  await expect(page.getByLabel('Фамилия', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.locator('#registration-error-lastName')).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('Фамилия: Укажите фамилию спортсмена.')
  await expect(page.getByLabel('Фамилия', { exact: true })).toBeFocused()
  await page.getByLabel('Фамилия', { exact: true }).fill('Демов')
  await page.getByLabel('Имя', { exact: true }).fill('Тимофей')
  await birth.fill('2014-04-18')
  await steps.getByRole('button', { name: '2 Виды и время', exact: true }).click()
  await expect(disciplines).toHaveAttribute('disabled', '')
  await expect(choices.first()).toBeDisabled()
  await steps.getByRole('button', { name: '1 Спортсмен', exact: true }).click()
  await gender.selectOption('M')
  await page.getByRole('button', { name: 'Далее: Виды и время', exact: true }).click()
  await expect(disciplines).not.toHaveAttribute('disabled')
  await expect(steps.locator('[aria-current=step]')).toHaveText('2 Виды и время')
  const selectedCard = choices.filter({ hasText: '50 м вольный стиль, юноши' }).first()
  await selectedCard.click()
  await expect(selectedCard).toHaveAttribute('aria-pressed', 'true')
  const seed = page.getByPlaceholder('мм:сс.сс')
  await expect(seed).toHaveAttribute('required', '')
  const errorId = (await seed.getAttribute('aria-describedby'))!.split(' ').find(id => id.startsWith('registration-error-'))!
  const seedError = page.locator(`[id="${errorId}"]`)
  const reservedHeight = (await seedError.boundingBox())!.height
  const lineHeight = await seedError.evaluate(element => Number.parseFloat(getComputedStyle(element).lineHeight))
  expect(reservedHeight).toBeCloseTo(lineHeight, 1)
  await page.getByRole('button', { name: 'Далее: Проверка', exact: true }).click()
  await expect(seed).toHaveAttribute('aria-invalid', 'true')
  await expect(seedError).toHaveText('Укажите заявочное время в формате мм:сс.сс.')
  await expect(seed).toBeFocused()
  expect((await seedError.boundingBox())!.height).toBeGreaterThanOrEqual(reservedHeight)
  await seed.fill('006000')
  await page.getByRole('button', { name: 'Далее: Проверка', exact: true }).click()
  await expect(seedError).toHaveText('Введите ненулевое время в формате мм:сс.сс (секунды 00–59).')
  await seed.fill('003215')
  await expect(seed).toHaveValue('00:32.15')
  await checkLayout(page, form, width)
  await page.getByRole('button', { name: 'Далее: Проверка', exact: true }).click()
  await expect(steps.locator('[aria-current=step]')).toHaveText('3 Проверка')
  await page.reload()
  await expect(page.getByRole('heading', { name: '3. Проверка', exact: true })).toBeVisible({ timeout: 30000 })
  await expect(page.getByText('Демов Тимофей', { exact: true })).toBeVisible()
  await expect(page.getByText('00:32.15', { exact: true })).toBeVisible()
  const send = page.getByRole('button', { name: 'Отправить заявку', exact: true })
  await expect(send).toBeDisabled()
  await expect(page.locator('#registration-disabled-reason')).toHaveText('Необходимо согласие на обработку персональных данных.')
  // The full consent label remains a tap target.
  await page.getByText('Я даю согласие на обработку персональных данных', { exact: true }).click()
  await expect(page.getByRole('checkbox', { name: 'Я даю согласие на обработку персональных данных' })).toBeChecked()
  await expect(send).toBeEnabled()
  if (width === 375) expect((await send.boundingBox())!.width).toBeGreaterThan(250)
  await checkLayout(page, form, width)
  await scenario(page, 'public/events/demo/registrations', [{ id: 'new-registration' }], 'json', 'post')
  await send.click()
  await expect(page.getByRole('heading', { name: 'Заявка отправлена' })).toBeVisible()
  expect(submissions).toEqual([{
    profile: { lastName: 'Демов', firstName: 'Тимофей', middleName: '', birthDate: '2014-04-18', gender: 'M', club: '' },
    items: [{ eventDisciplineId: 'M-50-FREE', seedTimeMs: 32150 }],
  }])
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
  expect(errors).toEqual([])
})
