import { test, expect } from '@playwright/test'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { navigate, openAuditPage, readState, routes } from './qa06-helpers'

const output = resolve(process.env.QA06_SCREENSHOTS ?? 'work/qa06-screenshots')
for (const [width, height] of [[1366, 768], [1920, 1080], [375, 812]]) {
  test(`route matrix ${width}x${height}`, async ({ browser }) => {
    test.skip(process.env.QA06_CAPTURE !== '1', 'Opt-in evidence capture; see docs/qa/QA06-ui-ux.md')
    await mkdir(output, { recursive: true })
    const rows = []
    for (const [name, path, role] of routes) {
      if (process.env.QA06_ROUTE_FILTER && !process.env.QA06_ROUTE_FILTER.split(',').includes(name)) continue
      const context = await browser.newContext({ viewport: { width, height }, locale: 'ru-RU', timezoneId: 'Europe/Moscow' })
      const page = await context.newPage()
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      await openAuditPage(page, path, role)
      await expect(page.locator('body')).not.toContainText('Загружаем оплату')
      await expect(page.locator('body')).not.toContainText('Загружаем список заявок')
      await expect(page.locator('body')).not.toContainText('Загружаем ваши заявки')
      await expect(page.locator('body')).not.toContainText('Загружаем заявку и данные платежа')
      await expect(page.getByText('Загрузка...', { exact: true })).toHaveCount(0)
      await expect(page.getByText('Загружаем данные…', { exact: true })).toHaveCount(0)
      await expect(page.getByText('Загружаем мероприятия…', { exact: true })).toHaveCount(0)
      await page.screenshot({ path: `${output}/QA06-${name}-${width}x${height}.png` })
      rows.push({ name, path, role, width, height, finalPath: new URL(page.url()).pathname,
        scrollWidth: await page.evaluate(() => document.documentElement.scrollWidth), errors })
      expect(errors, name).toEqual([])
      await context.close()
    }
    const file = `${output}/../QA06-matrix-${width}x${height}.json`
    const previous = process.env.QA06_ROUTE_FILTER ? JSON.parse(await readFile(file, 'utf8')) : []
    const merged = [...previous.filter((row: { name: string }) => !rows.some(updated => updated.name === row.name)), ...rows]
    await writeFile(file, JSON.stringify(merged, null, 2) + '\n')
  })
}

test('read states at 375px: empty, loading, HTTP 500 and network', async ({ browser }) => {
  test.skip(process.env.QA06_CAPTURE !== '1', 'Opt-in state evidence capture')
  test.setTimeout(360000)
  await mkdir(output, { recursive: true })
  const readPages = routes.filter(([name]) => !['login', 'register', 'legacy-register', 'admin-review', 'legacy-event', 'organizer-registrations'].includes(name))
  const rows = []
  for (const mode of (process.env.QA06_STATE_FILTER?.split(',') ?? ['empty', '500', 'network', 'loading'])) {
    for (const [name, path, role] of readPages) {
      const context = await browser.newContext({ viewport: { width: 375, height: 812 }, locale: 'ru-RU', timezoneId: 'Europe/Moscow' })
      const page = await context.newPage()
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      await openAuditPage(page, '/', role)
      await readState(page, mode)
      // Remount '/' so its initial data load also exercises this state.
      if (path === '/') {
        await page.evaluate(() => { history.pushState(null, '', '/login'); dispatchEvent(new PopStateEvent('popstate')) })
        await expect(page.getByRole('heading', { name: 'Войти', exact: true })).toBeVisible()
      }
      await page.evaluate(path => { history.pushState(null, '', path); dispatchEvent(new PopStateEvent('popstate')) }, path)
      if (mode === 'loading') {
        await expect(page.getByText('Загружаем страницу…', { exact: true })).toHaveCount(0)
        if (name === 'organizer-regulations-create') await expect(page.getByRole('combobox')).toBeDisabled()
        else await expect(page.locator('body')).toContainText(/Загруз|Загружа|загружа/)
        await expect(page.getByText('Загружаем страницу…', { exact: true })).toHaveCount(0)
      }
      else if (mode === '500' || mode === 'network') await expect(page.getByRole('alert').first()).toBeVisible()
      else {
        if (path.includes('/organizer/events/11111111-1111-4111-8111-111111111111')) await navigate(page, path)
        if (['my-payment', 'payment-return', 'operator-registration'].includes(name))
          await expect(page.getByRole('alert').first()).toBeVisible()
        else await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
        await expect(page.getByText('Загружаем страницу…', { exact: true })).toHaveCount(0)
        await expect(page.getByText('Загружаем событие…', { exact: true })).toHaveCount(0)
        await expect(page.locator('body')).not.toContainText('Загружаем оплату')
        await expect(page.locator('body')).not.toContainText('Загружаем ваши заявки')
        await expect(page.locator('body')).not.toContainText('Загружаем мероприятия')
        await expect(page.locator('body')).not.toContainText('Загружаем список заявок')
        await expect(page.getByText('Загрузка...', { exact: true })).toHaveCount(0)
        await expect(page.getByText('Загрузка…', { exact: true })).toHaveCount(0)
        await expect(page.locator('body')).not.toContainText(/Загружаем|Загрузка/)
      }
      await page.screenshot({ path: `${output}/QA06-${name}-${mode}-375x812.png` })
      rows.push({ name, path, mode, statusCount: await page.getByRole('status').count(), text: await page.locator('body').innerText(), errors })
      expect(errors, `${name} ${mode}`).toEqual([])
      await context.close()
    }
  }
  const file = `${output}/../QA06-states.json`
  const previous = process.env.QA06_STATE_FILTER ? JSON.parse(await readFile(file, 'utf8')) : []
  const merged = [...previous.filter((row: { name: string; mode: string }) => !rows.some(updated => updated.name === row.name && updated.mode === row.mode)), ...rows]
  await writeFile(file, JSON.stringify(merged, null, 2) + '\n')
})
