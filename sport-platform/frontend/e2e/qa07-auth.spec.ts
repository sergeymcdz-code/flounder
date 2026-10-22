import { requiredCredential } from './credentials'
import { test, expect, type Page, type Route } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { request as httpRequest } from 'node:http'
import { api, apiUrl, login, password, request } from './qa07-support'

async function createUser(page: Page) {
  const email = `qa07-auth-${randomUUID()}@test.example`
  await login(page, requiredCredential('ADMIN_EMAIL'), '/admin/users')
  await api(page, '/users', 'POST', {
    email, password, firstName: 'Регрессия', lastName: 'Аутентификация',
    birthDate: '1990-01-01', roles: ['participant'],
  })
  return email
}

test('M02: user changes their own password through the password page', async ({ page }) => {
  const email = await createUser(page)
  const changed = 'Qa07SelfChanged2026!'
  await login(page, email, '/account/password')
  await page.getByLabel('Текущий пароль').fill(password)
  await page.getByLabel('Новый пароль').fill(changed)
  await page.getByLabel('Повторите пароль').fill(changed)
  const change = page.waitForResponse(response => response.url().endsWith('/auth/change-password'))
  await page.getByRole('button', { name: 'Сохранить пароль', exact: true }).click()
  expect((await change).status()).toBe(204)
  await expect(page.getByRole('status')).toContainText('Пароль изменён. Войдите заново')
  expect(await page.evaluate(() => sessionStorage.getItem('flounder.auth-session') === null)).toBe(true)
  const old = await request(page.request, '/auth/login', 'POST', { email, password })
  expect(old.status()).toBe(401)
  await login(page, email, '/my/registrations', changed)
  await expect(page).toHaveURL(/\/my\/registrations$/)
  await expect(page.getByRole('heading', { name: 'Мои заявки', exact: true })).toBeVisible()
})

test('M02: a real access 401 transparently refreshes and rotates the session', async ({ page }) => {
  const email = await createUser(page)
  await login(page, email, '/my/registrations')
  // Preserve the JWT role payload so the protected route remains accessible,
  // but invalidate the signature. The real server, not a mock, returns 401.
  await page.evaluate(() => {
    const session = JSON.parse(sessionStorage.getItem('flounder.auth-session')!)
    session.accessToken = `${session.accessToken.split('.').slice(0, 2).join('.')}.invalid-signature`
    sessionStorage.setItem('flounder.auth-session', JSON.stringify(session))
  })
  let loginRedirect = false
  page.on('framenavigated', frame => { if (frame === page.mainFrame() && new URL(frame.url()).pathname === '/login') loginRedirect = true })
  const rejected = page.waitForResponse(response => response.url().endsWith('/registrations/me') && response.status() === 401)
  const refreshed = page.waitForResponse(response => response.url().endsWith('/auth/refresh'))
  const recovered = page.waitForResponse(response => response.url().endsWith('/registrations/me') && response.status() === 200)
  await page.reload()
  expect((await rejected).status()).toBe(401)
  const response = await refreshed
  expect(response.status()).toBe(200)
  // Compare inside the test without placing credentials in an assertion failure.
  const refreshRequest = response.request().postDataJSON() as { refreshToken: string }
  const issued = await response.json() as { accessToken: string; refreshToken: string }
  expect(Boolean(issued.refreshToken) && issued.refreshToken !== refreshRequest.refreshToken).toBe(true)
  expect((await recovered).status()).toBe(200)
  const storedMatches = await page.evaluate(({ accessToken, refreshToken }) => {
    const session = JSON.parse(sessionStorage.getItem('flounder.auth-session')!)
    return session.accessToken === accessToken && session.refreshToken === refreshToken
  }, issued)
  expect(storedMatches).toBe(true)
  expect(loginRedirect).toBe(false)
  await expect(page).toHaveURL(/\/my\/registrations$/)
  await expect(page.getByRole('heading', { name: 'Мои заявки', exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('M02: login returns 429 and Retry-After after N failures @rate-limit', async ({ page }, info) => {
  const email = await createUser(page)
  const failures = Number(process.env.APP_AUTH_LOGIN_MAX_FAILURES ?? 5)
  expect(Number.isInteger(failures) && failures > 0).toBe(true)
  // A transparent real HTTP proxy uses a separate source address. Earlier old-
  // password checks must not consume this test's IP budget. No response is mocked.
  const sourceIp = process.env.QA07_RATE_LIMIT_SOURCE_IP ?? '127.0.0.2'
  expect(sourceIp).toMatch(/^127\.\d{1,3}\.\d{1,3}\.\d{1,3}$/)
  await page.route(`${apiUrl}/api/v1/auth/login`, route => forwardLogin(route, sourceIp))
  await page.evaluate(() => sessionStorage.removeItem('flounder.auth-session'))
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill('Qa07WrongPassword2026!')
  for (let attempt = 0; attempt < failures; attempt++) {
    const response = page.waitForResponse(response => response.url().endsWith('/auth/login'))
    await page.getByRole('button', { name: 'Войти', exact: true }).click()
    expect((await response).status(), `failure ${attempt + 1} of N=${failures}`).toBe(401)
    await expect(page.getByRole('alert')).toContainText('Проверьте email и пароль')
  }
  const blocked = page.waitForResponse(response => response.url().endsWith('/auth/login'))
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  const response = await blocked
  expect(response.status()).toBe(429)
  const retryHeader = response.headers()['retry-after']
  expect(retryHeader).toMatch(/^[1-9]\d*$/)
  const retryAfter = Number(retryHeader)
  expect(retryAfter).toBeGreaterThan(0)
  expect((await response.json()).code).toBe('identity.too_many_attempts')
  await expect(page.getByRole('alert')).toContainText('Слишком много попыток входа')
  await expect(page).toHaveURL(/\/login$/)
  await info.attach('Login limiter evidence', {
    body: JSON.stringify({ maxFailures: failures, rejectedPasswords: failures, status: 429, retryAfterSeconds: retryAfter, sourceIp }),
    contentType: 'application/json',
  })
})

async function forwardLogin(route: Route, sourceIp: string) {
  const destination = new URL(route.request().url())
  if (destination.hostname === 'localhost') destination.hostname = '127.0.0.1'
  const result = await new Promise<{ status: number; headers: Record<string, string>; body: Buffer }>((resolve, reject) => {
    const upstream = httpRequest(destination, {
      method: route.request().method(), headers: route.request().headers(), localAddress: sourceIp,
    }, response => {
      const chunks: Buffer[] = []
      response.on('data', chunk => chunks.push(Buffer.from(chunk)))
      response.on('error', reject)
      response.on('end', () => resolve({
        status: response.statusCode!, body: Buffer.concat(chunks),
        headers: Object.fromEntries(Object.entries(response.headers)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : value!])),
      }))
    })
    upstream.on('error', reject)
    upstream.end(route.request().postDataBuffer())
  })
  await route.fulfill(result)
}
