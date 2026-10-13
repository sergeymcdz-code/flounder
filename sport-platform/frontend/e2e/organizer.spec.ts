import { test, expect, type Page } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const base = '/organizer/events/11111111-1111-4111-8111-111111111111'
async function login(page: Page) {
  await page.goto(base)
  await page.getByLabel('Email').fill('organizer@example.test')
  await page.getByLabel('Пароль', { exact: true }).fill('demo-password')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Осенний кубок Flounder', exact: true }),
  ).toBeVisible()
}

test('organizer acceptance flow and conflict recovery', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await login(page)
  await page.getByRole('link', { name: 'Сессии и виды', exact: true }).click()
  await page
    .getByRole('button', { name: 'Добавить сессию', exact: true })
    .click()
  let dialog = page.getByRole('dialog')
  await dialog.getByLabel('Название', { exact: true }).fill('Тестовая сессия')
  await dialog
    .getByLabel('Дата и время старта (местное время)')
    .fill('2026-10-14T16:00')
  await dialog.getByRole('button', { name: 'Сохранить сессию' }).click()
  await dialog.waitFor({ state: 'hidden' })
  await page
    .getByRole('cell', { name: 'Тестовая сессия', exact: true })
    .waitFor()
  await page.getByRole('button', { name: 'Добавить вид', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: /^Код вида/ }).fill('F-200-FREE')
  await dialog.getByRole('combobox', { name: /^Дистанция/ }).selectOption('200')
  await dialog
    .getByRole('combobox', { name: /^Сессия/ })
    .selectOption({ label: 'Тестовая сессия' })
  await dialog
    .getByRole('button', { name: 'Добавить пресет', exact: true })
    .click()
  await dialog
    .getByRole('button', { name: 'Применить пресет', exact: true })
    .click()
  expect(await dialog.getByLabel('От года', { exact: true }).count()).toBe(2)
  await dialog
    .getByRole('button', { name: 'Сохранить вид', exact: true })
    .click()
  await dialog.waitFor({ state: 'hidden' })
  await page.getByRole('link', { name: 'Заявки', exact: true }).last().click()
  await page
    .getByRole('button', { name: 'Импорт из Excel', exact: true })
    .click()
  dialog = page.getByRole('dialog')
  const downloadPromise = page.waitForEvent('download')
  await dialog
    .getByRole('button', { name: 'Скачать шаблон', exact: true })
    .click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('flounder-registrations.xlsx')
  await dialog
    .getByLabel('Файл .xlsx')
    .setInputFiles('src/mocks/import-template.xlsx')
  await dialog
    .getByRole('button', { name: 'Импортировать', exact: true })
    .click()
  await dialog.getByRole('heading', { name: 'Отчёт импорта' }).waitFor()
  expect(await dialog.innerText()).toMatch(/Создано: 3/)
  await dialog.getByRole('button', { name: 'Закрыть и обновить' }).click()
  await dialog.waitFor({ state: 'hidden' })
  await page.getByRole('link', { name: 'Заплывы', exact: true }).click()
  await page
    .getByRole('button', {
      name: 'Сформировать заплывы (всё событие)',
      exact: true,
    })
    .click()
  await page.getByRole('heading', { name: 'Последнее формирование' }).waitFor()
  await page
    .getByRole('button', { name: 'Пересчитать расписание', exact: true })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'Выполняем действие' })
    .waitFor({ state: 'hidden' })
  const lane = page.getByRole('combobox').first()
  const old = await lane.inputValue()
  const occupied = await page.getByRole('combobox').nth(1).inputValue()
  await lane.selectOption(occupied)
  await page
    .getByRole('alert')
    .filter({ hasText: 'Дорожка уже занята' })
    .waitFor()
  expect(await lane.inputValue()).toBe(old)
  const used = await page
    .locator('article')
    .filter({ has: page.getByRole('combobox') })
    .first()
    .getByRole('combobox')
    .evaluateAll((nodes) => nodes.map((n) => n.value))
  const free = Array.from({ length: 8 }, (_, i) => String(i + 1)).find(
    (n) => !used.includes(n),
  )
  expect(free).toBeDefined()
  await lane.selectOption(free!)
  await page
    .getByRole('status')
    .filter({ hasText: 'Выполняем действие' })
    .waitFor({ state: 'hidden' })
  expect(await lane.inputValue()).toBe(free)
  await page
    .getByRole('button', { name: 'Проверить заплывы', exact: true })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'Выполняем действие' })
    .waitFor({ state: 'hidden' })
  await page
    .getByRole('button', {
      name: 'Опубликовать стартовый протокол',
      exact: true,
    })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'Выполняем действие' })
    .waitFor({ state: 'hidden' })
  await page
    .getByRole('button', {
      name: 'Сформировать заплывы (всё событие)',
      exact: true,
    })
    .click()
  dialog = page.getByRole('dialog')
  await dialog
    .getByRole('heading', { name: 'Переформировать опубликованные заплывы?' })
    .waitFor()
  await dialog.getByRole('button', { name: 'Подтвердить', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await page
    .getByText(
      'Опубликованные заплывы заменены. Требуется повторная публикация.',
      { exact: false },
    )
    .first()
    .waitFor()
  await page
    .getByRole('link', { name: 'Результаты', exact: true })
    .last()
    .click()
  expect(
    await page
      .getByRole('link', { name: 'Ввести', exact: true })
      .first()
      .getAttribute('href'),
  ).toMatch(/^\/operator\/competition-units\//)
  await page.goto(base + '/registrations')
  await page.waitForURL(base + '/entries')
  await page
    .getByRole('heading', { name: 'Заявки участников · 30', exact: true })
    .waitFor()

  expect(errors).toEqual([])
})

test('backend field errors stay beside inputs; event and result publication refresh', async ({
  page,
}) => {
  await login(page)
  for (const action of [
    'Опубликовать',
    'Открыть регистрацию',
    'Закрыть регистрацию',
    'Завершить соревнование',
  ]) {
    await page.getByRole('button', { name: action, exact: true }).click()
    await page
      .getByRole('status')
      .filter({ hasText: 'Выполняем действие' })
      .waitFor({ state: 'hidden' })
  }
  await expect(page.getByText('Завершено', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Сессии и виды', exact: true }).click()
  await page.getByRole('button', { name: 'Добавить вид', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: /^Код вида/ }).fill('F-50-FREE')
  await dialog
    .getByRole('button', { name: 'Сохранить вид', exact: true })
    .click()
  await expect(
    dialog.getByText('Код вида уже используется.', { exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(1)
  await expect(dialog.getByRole('alert')).toBeVisible()
  await expect(dialog.getByRole('textbox', { name: /^Код вида/ })).toHaveValue(
    'F-50-FREE',
  )
  await dialog.getByRole('textbox', { name: /^Код вида/ }).fill('F-200-FREE')
  await dialog
    .getByRole('button', { name: 'Сохранить вид', exact: true })
    .click()
  await expect(dialog).not.toBeVisible()
  await page
    .getByRole('link', { name: 'Результаты', exact: true })
    .last()
    .click()
  const publish = page
    .getByRole('button', { name: 'Опубликовать результаты вида', exact: true })
    .nth(2)
  await expect(publish).toBeEnabled()
  await publish.click()
  await expect(
    page.getByRole('cell', { name: 'Опубликованы', exact: true }),
  ).toBeVisible()
})

test('unauthorized requests redirect and forbidden roles see access denial', async ({
  page,
}) => {
  await login(page)
  const statuses = await page.evaluate(async () => {
    const missing = await fetch(
      '/api/v1/events/11111111-1111-4111-8111-111111111111',
    )
    const token = `demo.${btoa(JSON.stringify({ roles: ['participant'] }))}.demo`
    const forbidden = await fetch(
      '/api/v1/events/11111111-1111-4111-8111-111111111111',
      { headers: { Authorization: `Bearer ${token}` } },
    )
    return [missing.status, forbidden.status]
  })
  expect(statuses).toEqual([401, 403])
  await page.evaluate(() => {
    const token = `demo.${btoa(JSON.stringify({ roles: ['organizer'], exp: 1 }))}.demo`
    sessionStorage.setItem(
      'flounder.auth-session',
      JSON.stringify({
        accessToken: token,
        tokenType: 'Bearer',
        expiresAt: Date.now() + 3600000,
      }),
    )
  })
  await page.reload()
  await expect(page).toHaveURL(/\/login$/)
  await expect(
    page.getByRole('button', { name: 'Войти', exact: true }),
  ).toBeVisible()
  await page.evaluate(() => {
    const token = `demo.${btoa(JSON.stringify({ roles: ['participant'] }))}.demo`
    sessionStorage.setItem(
      'flounder.auth-session',
      JSON.stringify({
        accessToken: token,
        tokenType: 'Bearer',
        expiresAt: Date.now() + 3600000,
      }),
    )
  })
  await page.goto(base)
  await expect(page.getByRole('alert')).toContainText(
    'Нет доступа к кабинету организатора.',
  )
})

for (const width of [1280, 1024]) {
  test(`five tabs fit the viewport at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await login(page)
    const tabs = [
      ['Обзор', 'overview'],
      ['Сессии и виды', 'program'],
      ['Заявки', 'entries'],
      ['Заплывы', 'heats'],
      ['Результаты', 'results'],
    ] as const
    for (const [label, name] of tabs) {
      await page
        .getByRole('navigation', { name: 'Разделы события' })
        .getByRole('link', { name: label, exact: true })
        .click()
      await expect(
        page
          .getByRole('navigation', { name: 'Разделы события' })
          .getByRole('link', { name: label, exact: true }),
      ).toHaveAttribute('aria-current', 'page')
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBeLessThanOrEqual(width)
      if (
        process.env.ORGANIZER_SCREENSHOT_DIR &&
        (!process.env.ORGANIZER_SCREENSHOT_TABS ||
          process.env.ORGANIZER_SCREENSHOT_TABS.split(',').includes(name))
      ) {
        const directory = resolve(process.env.ORGANIZER_SCREENSHOT_DIR)
        await mkdir(directory, { recursive: true })
        await page.screenshot({
          path: resolve(directory, `${name}-${width}.png`),
          fullPage: true,
        })
      }
    }
  })
}

test('session CRUD, review decisions, payment, seed time and validator focus', async ({
  page,
}) => {
  await login(page)
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Сессии и виды', exact: true })
    .click()
  await page
    .getByRole('button', { name: 'Добавить сессию', exact: true })
    .click()
  let dialog = page.getByRole('dialog')
  await dialog
    .getByLabel('Название', { exact: true })
    .fill('Дополнительная сессия')
  await dialog.getByRole('button', { name: 'Сохранить сессию' }).click()
  await expect(dialog).not.toBeVisible()
  let row = page.getByRole('row').filter({ hasText: 'Дополнительная сессия' })
  await row.getByRole('button', { name: 'Изменить', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog
    .getByLabel('Название', { exact: true })
    .fill('Дополнительная сессия 2')
  await dialog.getByRole('button', { name: 'Сохранить сессию' }).click()
  await expect(dialog).not.toBeVisible()
  row = page.getByRole('row').filter({ hasText: 'Дополнительная сессия 2' })
  await row.getByRole('button', { name: 'Удалить', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Подтвердить', exact: true })
    .click()
  await expect(row).toHaveCount(0)
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Заявки', exact: true })
    .click()
  row = page.getByRole('row').nth(1)
  await row.getByRole('button', { name: 'Вернуть', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByLabel('Комментарий')
    .fill('Уточните время')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Сохранить решение' })
    .click()
  await expect(row).toContainText('На исправлении')
  await row.getByRole('button', { name: 'Подтвердить', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Сохранить решение' })
    .click()
  await expect(row).toContainText('Подтверждена')
  const timeButton = row.getByRole('button', { name: '00:32.15', exact: true })
  await timeButton.click()
  await row.getByRole('textbox').fill('00:31.25')
  await row.getByRole('button', { name: 'Сохранить', exact: true }).click()
  await expect(
    row.getByRole('button', { name: '00:31.25', exact: true }),
  ).toBeVisible()
  const paymentRow = page.getByRole('row').nth(2)
  await paymentRow
    .getByRole('button', { name: 'Оплачено', exact: true })
    .click()
  await expect(
    paymentRow.getByRole('cell', { name: 'Оплачено', exact: true }),
  ).toHaveCount(2)
  const rejectedRow = page.getByRole('row').nth(3)
  await rejectedRow
    .getByRole('button', { name: 'Отклонить', exact: true })
    .click()
  await page
    .getByRole('dialog')
    .getByLabel('Комментарий')
    .fill('Ошибочная заявка')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Сохранить решение' })
    .click()
  await expect(rejectedRow).toContainText('Отклонена')
  await row.getByRole('button', { name: 'Снять', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Подтвердить', exact: true })
    .click()
  await expect(row).toContainText('Снята')
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Заплывы', exact: true })
    .click()
  await page
    .getByRole('button', { name: 'Проверить заплывы', exact: true })
    .click()
  const violation = page.getByRole('button', {
    name: /^Ошибка: В заплыве 1 есть снятая заявка/,
  })
  await expect(violation).toBeVisible()
  await violation.click()
  await expect(page.locator('article[id^="heat-"]').first()).toHaveClass(
    /highlight/,
  )
})

test('template selection, optional code, explicit payloads and registration refresh scope', async ({
  page,
}) => {
  await login(page)
  const bodies: Record<string, unknown>[] = []
  const gets: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'GET' && request.url().includes('/api/v1/'))
      gets.push(new URL(request.url()).pathname)
    if (
      ['POST', 'PATCH'].includes(request.method()) &&
      /\/disciplines$|\/event-disciplines\//.test(request.url())
    )
      bodies.push(request.postDataJSON() as Record<string, unknown>)
  })
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Сессии и виды', exact: true })
    .click()
  await page.getByRole('button', { name: 'Добавить вид', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('Шаблон', { exact: true })).toHaveValue(
    '00000012-1111-4111-8111-000000000001',
  )
  await dialog.getByRole('textbox', { name: /^Название/ }).fill('Без кода')
  await dialog.getByRole('button', { name: 'Сохранить вид' }).click()
  await expect(dialog).not.toBeVisible()
  expect(bodies[0]).toHaveProperty(
    'disciplineTemplateId',
    '00000012-1111-4111-8111-000000000001',
  )
  expect(bodies[0]).not.toHaveProperty('code')
  const row = page.getByRole('row').filter({ hasText: 'Без кода' })
  await row.getByRole('button', { name: 'Изменить', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog
    .getByLabel('Шаблон', { exact: true })
    .selectOption('00000012-1111-4111-8111-000000000002')
  await dialog.getByRole('button', { name: 'Сохранить вид' }).click()
  await expect(dialog).not.toBeVisible()
  expect(bodies[1]).toHaveProperty(
    'disciplineTemplateId',
    '00000012-1111-4111-8111-000000000002',
  )
  for (const body of bodies) {
    for (const key of [
      'id',
      'eventId',
      'status',
      'settingsJson',
      'competitionFormat',
      'unitType',
      'resultType',
      'rankingStrategy',
    ])
      expect(body).not.toHaveProperty(key)
  }
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Заявки', exact: true })
    .click()
  const entry = page.getByRole('row').nth(1)
  for (const [input, display] of [
    ['31.25', '00:31.25'],
    ['1:03.45', '01:03.45'],
  ]) {
    await entry.getByRole('button', { name: /^(00:32.15|00:31.25)$/ }).click()
    await entry.getByRole('textbox').fill(input)
    gets.length = 0
    await entry.getByRole('button', { name: 'Сохранить', exact: true }).click()
    await expect(
      entry.getByRole('button', { name: display, exact: true }),
    ).toBeVisible()
    expect(gets).toEqual(['/api/v1/registrations'])
  }
  await entry.getByRole('button', { name: 'Снять', exact: true }).click()
  gets.length = 0
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Подтвердить', exact: true })
    .click()
  await expect(
    page.getByText('Пересчитайте заплывы', { exact: false }),
  ).toBeVisible()
  await expect(
    page.getByRole('link', { name: 'Заплывы', exact: true }).last(),
  ).toHaveAttribute('href', `${base}/heats`)
  expect(gets).toEqual(['/api/v1/registrations'])
})

test('generation admission persists across tabs with nested and flat summaries', async ({
  page,
}) => {
  await login(page)
  await page.evaluate(async () => {
    // Use the same MSW worker as the app to emulate both supported server contracts.
    const resources = performance
      .getEntriesByType('resource')
      .map((entry) => entry.name)
    const modules = [
      resources.find(
        (url) => new URL(url).pathname === '/src/mocks/browser.ts',
      )!,
      resources.find((url) => new URL(url).pathname.endsWith('/msw.js'))!,
    ]
    if (modules.some((url) => !url))
      throw new Error('MSW modules were not loaded')
    const { worker } = await import(modules[0])
    const { http, HttpResponse } = await import(modules[1])
    const { accessToken } = JSON.parse(
      sessionStorage.getItem('flounder.auth-session')!,
    ) as { accessToken: string }
    const headers = { Authorization: `Bearer ${accessToken}` }
    const registrations = (await (
      await fetch('/api/v1/registrations', { headers })
    ).json()) as { id: string; eventDisciplineId: string }[]
    let calls = 0
    worker.use(
      http.post('*/api/v1/events/:id/generate-competition-units', () => {
        calls++
        const result = {
          runId: 'review-run',
          heats: [],
          durationMs: 19,
          excludedRegistrationIds: registrations.slice(0, 3).map((r) => r.id),
          violations: [
            'GENDER_MISMATCH',
            'AGE_GROUP_MISMATCH',
            'DUPLICATE_ENTRY',
          ].map((code, i) => ({
            code,
            message: 'Не допущена',
            severity: 'ERROR',
            registrationId: registrations[i].id,
            eventDisciplineId: registrations[i].eventDisciplineId,
          })),
        }
        const ids = [...new Set(registrations.map((r) => r.eventDisciplineId))]
        return HttpResponse.json({
          durationMs: 19,
          disciplines: ids.map((eventDisciplineId) =>
            calls === 1
              ? { eventDisciplineId, result }
              : { eventDisciplineId, ...result },
          ),
        })
      }),
    )
  })
  for (let call = 0; call < 2; call++) {
    await page
      .getByRole('navigation', { name: 'Разделы события' })
      .getByRole('link', { name: 'Заплывы', exact: true })
      .click()
    await page
      .getByRole('button', { name: 'Сформировать заплывы (всё событие)' })
      .click()
    await expect(
      page.getByRole('heading', { name: 'Последнее формирование' }),
    ).toBeVisible()
    await page
      .getByRole('status')
      .filter({ hasText: 'Выполняем действие' })
      .waitFor({ state: 'hidden' })
    await page
      .getByRole('navigation', { name: 'Разделы события' })
      .getByRole('link', { name: 'Заявки', exact: true })
      .click()
    for (const code of [
      'GENDER_MISMATCH',
      'AGE_GROUP_MISMATCH',
      'DUPLICATE_ENTRY',
    ])
      await expect(page.getByText(code, { exact: true })).toHaveAttribute(
        'title',
        /[А-Яа-я]/,
      )
    await page
      .getByRole('combobox', { name: /^Статус/ })
      .selectOption('EXCLUDED')
    await expect(page.getByRole('row')).toHaveCount(4)
    await page.getByRole('combobox', { name: /^Статус/ }).selectOption('')
    await expect(page.getByText('✓', { exact: true }).first()).toBeVisible()
  }
})

test('missing pool dimensions retain lane editing and refresh only the changed heat', async ({
  page,
}) => {
  await login(page)
  await page.evaluate(async () => {
    const resources = performance
      .getEntriesByType('resource')
      .map((entry) => entry.name)
    const modules = [
      resources.find(
        (url) => new URL(url).pathname === '/src/mocks/browser.ts',
      )!,
      resources.find((url) => new URL(url).pathname.endsWith('/msw.js'))!,
    ]
    if (modules.some((url) => !url))
      throw new Error('MSW modules were not loaded')
    const { worker } = await import(modules[0])
    const { http, HttpResponse } = await import(modules[1])
    const { accessToken } = JSON.parse(
      sessionStorage.getItem('flounder.auth-session')!,
    ) as { accessToken: string }
    const headers = { Authorization: `Bearer ${accessToken}` }
    const event = (await (
      await fetch('/api/v1/events/11111111-1111-4111-8111-111111111111', {
        headers,
      })
    ).json()) as { venue: { lanes: number | null } }
    event.venue.lanes = null
    worker.use(http.get('*/api/v1/events/:id', () => HttpResponse.json(event)))
  })
  await page.getByRole('link', { name: '← Мои события', exact: true }).click()
  await page
    .getByRole('link', { name: 'Управлять событием', exact: true })
    .click()
  await page
    .getByRole('navigation', { name: 'Разделы события' })
    .getByRole('link', { name: 'Заплывы', exact: true })
    .click()
  const lane = page.getByRole('combobox').first()
  await expect(lane).toBeEnabled()
  await expect(lane).toHaveAttribute(
    'title',
    'Число дорожек площадки не указано',
  )
  expect(await lane.getByRole('option').count()).toBe(8) // Empty option plus inferred lanes 1..7.
  const gets: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'GET' && request.url().includes('/api/v1/'))
      gets.push(new URL(request.url()).pathname)
  })
  const laneName = await lane.getAttribute('aria-label')
  await lane.selectOption('7')
  await page
    .getByRole('status')
    .filter({ hasText: 'Выполняем действие' })
    .waitFor({ state: 'hidden' })
  expect(gets).toEqual([
    '/api/v1/competition-units/00000010-1111-4111-8111-000000000001',
  ])
  await expect(
    page.getByRole('combobox', { name: laneName!, exact: true }),
  ).toHaveValue('7')
})

test('session estimates honor server values and browser timezone; violations target the numbered heat', async ({
  browser,
}) => {
  const context = await browser.newContext({
    baseURL: 'http://127.0.0.1:5173',
    timezoneId: 'America/New_York',
  })
  const page = await context.newPage()
  try {
    await login(page)
    await page
      .getByRole('navigation', { name: 'Разделы события' })
      .getByRole('link', { name: 'Сессии и виды', exact: true })
      .click()
    await expect(
      page.getByRole('row').filter({ hasText: 'Утренняя сессия' }).first(),
    ).toContainText(/03:00.*03:16.*4 заплывов/)
    await page.evaluate(async () => {
      const resources = performance
        .getEntriesByType('resource')
        .map((entry) => entry.name)
      const { worker } = await import(
        resources.find(
          (url) => new URL(url).pathname === '/src/mocks/browser.ts',
        )!
      )
      const { http, HttpResponse } = await import(
        resources.find((url) => new URL(url).pathname.endsWith('/msw.js'))!
      )
      const { accessToken } = JSON.parse(
        sessionStorage.getItem('flounder.auth-session')!,
      ) as { accessToken: string }
      const sessions = (await (
        await fetch(
          '/api/v1/events/11111111-1111-4111-8111-111111111111/sessions',
          { headers: { Authorization: `Bearer ${accessToken}` } },
        )
      ).json()) as { heatsCount?: number; estimatedEndsAt?: string }[]
      sessions[0].heatsCount = 99
      sessions[0].estimatedEndsAt = '2026-10-14T15:26:00Z'
      worker.use(
        http.get('*/api/v1/events/:id/sessions', () =>
          HttpResponse.json(sessions),
        ),
        http.get(
          '*/api/v1/event-disciplines/00000003-1111-4111-8111-000000000001/competition-units/validate',
          () =>
            HttpResponse.json({
              violations: [
                {
                  code: 'REVIEW_CHECK',
                  severity: 'WARNING',
                  message: 'Проверить второй заплыв',
                  eventDisciplineId: '00000003-1111-4111-8111-000000000001',
                  heatNumber: 2,
                  unitId: '00000010-1111-4111-8111-000000000001',
                },
              ],
            }),
        ),
      )
    })
    await page.getByRole('link', { name: '← Мои события', exact: true }).click()
    await page
      .getByRole('link', { name: 'Управлять событием', exact: true })
      .click()
    await page
      .getByRole('navigation', { name: 'Разделы события' })
      .getByRole('link', { name: 'Сессии и виды', exact: true })
      .click()
    await expect(
      page.getByRole('row').filter({ hasText: 'Утренняя сессия' }).first(),
    ).toContainText(/03:00.*11:26.*99 заплывов/)
    await page
      .getByRole('navigation', { name: 'Разделы события' })
      .getByRole('link', { name: 'Заплывы', exact: true })
      .click()
    await page
      .getByRole('button', { name: 'Проверить заплывы', exact: true })
      .click()
    await page
      .getByRole('button', {
        name: 'Предупреждение: Проверить второй заплыв',
        exact: true,
      })
      .click()
    await expect(
      page.locator('#heat-00000010-1111-4111-8111-000000000002'),
    ).toHaveClass(/highlight/)
    await expect(
      page.locator('#heat-00000010-1111-4111-8111-000000000001'),
    ).not.toHaveClass(/highlight/)
  } finally {
    await context.close()
  }
})
