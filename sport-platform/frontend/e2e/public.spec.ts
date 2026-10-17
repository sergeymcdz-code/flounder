import { test, expect } from '@playwright/test'
import { mkdir, readdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { demoStartList } from '../src/mocks/demo-data'

const artifacts = resolve(process.env.T07_ARTIFACT_DIR ?? 'work/verification')
const participantSession = {
  accessToken:
    'demo.' +
    Buffer.from(JSON.stringify({ roles: ['participant'] })).toString(
      'base64url',
    ) +
    '.demo',
  tokenType: 'Bearer',
  expiresAt: Date.now() + 3_600_000,
}

test('all public pages fit 375px, with screenshots and printable protocols', async ({
  page,
}) => {
  await mkdir(artifacts, { recursive: true })
  await page.addInitScript(
    (session) =>
      sessionStorage.setItem('flounder.auth-session', JSON.stringify(session)),
    participantSession,
  )
  for (const route of [
    '',
    '/start-list',
    '/schedule',
    '/live',
    '/results',
    '/register',
    '/heats/M-50-FREE-1',
  ]) {
    await page.goto(`/e/demo${route}`)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByText('Загружаем данные…')).toHaveCount(0)
    if (route === '/live') {
      await expect(page.getByText(/Сейчас заплыв №/)).toBeVisible()
      await expect(page.getByTitle('Дорожка', { exact: true })).toBeVisible()
      expect(
        await page
          .locator('[class*=heatNumber]')
          .evaluate((element) => getComputedStyle(element).fontSize),
      ).toBe('64px')
    }
    if (route === '/start-list')
      await expect(page.getByRole('table')).toHaveCount(6)
    if (route === '/results')
      await expect(page.getByRole('table')).toHaveCount(4)
    if (route === '/register')
      await expect(
        page.getByRole('button', { name: 'Отправить заявку' }),
      ).toBeVisible()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
    const name = route.slice(1).replaceAll('/', '-') || 'event'
    await page.screenshot({
      path: `${artifacts}/${name}-375.png`,
      fullPage: true,
    })
    await page.screenshot({ path: `${artifacts}/${name}-375x812.png` })
  }
  for (const route of ['start-list', 'results']) {
    await page.goto(`/e/demo/${route}`)
    await expect(page.getByRole('table').first()).toBeVisible()
    if (route === 'results')
      await page.getByRole('button', { name: 'По группам' }).click()
    await page.emulateMedia({ media: 'print' })
    await expect(page.getByRole('navigation')).toBeHidden()
    await expect(page.getByRole('button', { name: 'Печать' })).toBeHidden()
    await page.pdf({
      path: `${artifacts}/${route}.pdf`,
      format: 'A4',
      preferCSSPageSize: true,
      printBackground: true,
    })
    await page.emulateMedia({ media: 'screen' })
  }
})

test('surname search, auto print and legacy redirect', async ({ page }) => {
  await page.goto('/events/demo')
  await expect(page).toHaveURL(/\/e\/demo$/)
  await page.getByRole('link', { name: 'Найти себя', exact: true }).click()
  const lane = demoStartList.sessions[0].disciplines[0].heats[0].lanes[0]
  if (lane.empty) throw new Error('Expected an occupied lane')
  const athlete = lane.athlete
  await page.getByRole('searchbox').fill(athlete.lastName)
  await expect(
    page.getByRole('heading', {
      name: `${athlete.lastName} ${athlete.firstName}`,
    }),
  ).toBeVisible()
  await page.evaluate(() => {
    ;(window as unknown as { printCalls: number }).printCalls = 0
    window.print = () => {
      ;(window as unknown as { printCalls: number }).printCalls++
    }
  })
  await page.getByRole('link', { name: 'SportGate', exact: true }).click()
  await page.getByRole('link', { name: /Стартовый протокол →/ }).click()
  await page.evaluate(() => history.replaceState(null, '', '?print=1'))
  // A full navigation requires reinstalling the print spy before app bootstrap.
  await page.addInitScript(() => {
    ;(window as unknown as { printCalls: number }).printCalls = 0
    window.print = () => {
      ;(window as unknown as { printCalls: number }).printCalls++
    }
  })
  await page.goto('/e/demo/start-list?print=1')
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as { printCalls: number }).printCalls,
      ),
    )
    .toBe(1)
})

test('anonymous registration returns after login; authenticated form validates per discipline', async ({
  page,
}) => {
  await page.goto('/e/demo/register')
  await page.getByRole('link', { name: 'Войти', exact: true }).click()
  await expect(page).toHaveURL(/next=%2Fe%2Fdemo%2Fregister/)
  await page.getByLabel('Email', { exact: true }).fill('parent@flounder.demo')
  await page.getByLabel('Пароль', { exact: true }).fill('Demo2026!')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page).toHaveURL(/\/e\/demo\/register$/)
  const roles = await page.evaluate(() => {
    const session = JSON.parse(sessionStorage.getItem('flounder.auth-session')!)
    return JSON.parse(atob(session.accessToken.split('.')[1])).roles
  })
  expect(roles).toContain('participant')
  await page.getByLabel('Фамилия', { exact: true }).fill('Ошибка')
  await page.getByLabel('Имя', { exact: true }).fill('Иван')
  await page.getByLabel('Дата рождения').fill('2012-06-15')
  await page.getByLabel('Пол', { exact: true }).selectOption('M')
  await page
    .getByRole('checkbox', { name: /50 м вольный стиль, юноши/ })
    .check()
  await page.getByLabel('Заявочное время').fill('')
  await page.getByLabel('Заявочное время').pressSequentially('32.15')
  await expect(page.getByLabel('Заявочное время')).toHaveValue('00:32.15')
  await page.getByRole('checkbox', { name: /Я даю согласие/ }).check()
  await page.getByRole('button', { name: 'Отправить заявку' }).click()
  await expect(page.getByText('Этот вид не подходит спортсмену.')).toBeVisible()
  await page.getByLabel('Фамилия', { exact: true }).fill('Иванов')
  await page.getByRole('button', { name: 'Отправить заявку' }).click()
  await expect(
    page.getByRole('heading', { name: 'Заявка отправлена' }),
  ).toBeVisible()
})

test('production PWA caches shell and live API and reloads offline', async ({
  browser,
}) => {
  expect(
    (await readdir(resolve('dist/assets'))).filter((name) =>
      /msw|demo/i.test(name),
    ),
  ).toEqual([])
  const context = await browser.newContext({
    viewport: { width: 375, height: 812 },
  })
  const page = await context.newPage()
  await page.goto('http://localhost:5175/e/demo/live')
  await expect(page.getByText('Сейчас заплыв №1')).toBeVisible()
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
  })
  await page.reload()
  await expect(page.getByText('Сейчас заплыв №1')).toBeVisible()
  await expect
    .poll(() =>
      page.evaluate(async () => {
        const cache = await caches.open('flounder-public-api-v1')
        return !!(await cache.match('/api/v1/public/events/demo/live'))
      }),
    )
    .toBe(true)
  const manifest = await (
    await page.request.get('http://localhost:5175/manifest.webmanifest')
  ).json()
  expect(await page.locator('link[rel=icon]').getAttribute('href')).toBe(
    manifest.icons[0].src,
  )
  await expect(page).toHaveTitle(/SportGate/)
  expect(manifest).toMatchObject({
    name: 'SportGate',
    short_name: 'SportGate',
    description: 'SportGate — расписание, заплывы и результаты соревнований по плаванию',
    lang: 'ru',
    display: 'standalone',
    theme_color: '#182126',
  })
  expect(
    manifest.icons.filter((icon: { sizes: string }) =>
      ['192x192', '512x512'].includes(icon.sizes),
    ),
  ).toHaveLength(3)
  for (const icon of manifest.icons) {
    expect(icon.src).toMatch(/^\/icons\/sportgate-/)
    expect((await page.request.get(`http://localhost:5175${icon.src}`)).ok()).toBe(true)
  }
  await expect(page.getByRole('contentinfo')).toHaveText('SportGate · sportgate.pro')
  await page.goto('http://localhost:5175/')
  await expect(
    page.getByRole('heading', { name: 'Осенний кубок SportGate' }),
  ).toBeVisible()
  await page.getByRole('link', { name: 'Подробнее' }).click()
  await expect(page).toHaveURL('http://localhost:5175/e/demo')
  await page.goto('http://localhost:5175/e/demo/live')
  await expect(page.getByText('Сейчас заплыв №1')).toBeVisible()
  const cdp = await context.newCDPSession(page)
  const installability = await cdp.send('Page.getInstallabilityErrors')
  expect(installability.installabilityErrors).toEqual([])
  await context.setOffline(true)
  await page.reload()
  await expect(page.getByText('Сейчас заплыв №1')).toBeVisible()
  await expect(
    page.getByText(/Нет связи, показаны последние данные/),
  ).toBeVisible()
  await context.setOffline(false)
  await expect(
    page.getByText(/Нет связи, показаны последние данные/),
  ).toHaveCount(0, { timeout: 8_000 })
  await context.close()
})
