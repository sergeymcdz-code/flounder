import { expect, type Page, type APIRequestContext } from '@playwright/test'

export const apiUrl = process.env.QA07_API_URL ?? 'http://localhost:8080'
export const password = process.env.QA07_PASSWORD ?? 'Demo2026!'

export async function login(page: Page, email: string, next: string, secret = password) {
  await page.goto('/')
  await page.evaluate(() => sessionStorage.removeItem('flounder.auth-session'))
  await page.goto(`/login?next=${encodeURIComponent(next)}`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль', { exact: true }).fill(secret)
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
}

export async function token(page: Page): Promise<string> {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem('flounder.auth-session') ?? '{}').accessToken)
}

export function request(client: APIRequestContext, path: string, method = 'GET', data?: unknown, accessToken?: string) {
  return client.fetch(`${apiUrl}/api/v1${path}`, {
    method, data, headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  })
}

export async function api<T>(page: Page, path: string, method = 'GET', data?: unknown): Promise<T> {
  const response = await request(page.request, path, method, data, await token(page))
  expect(response.status(), `${method} ${path}`).toBeLessThan(300)
  return response.status() === 204 ? undefined as T : response.json()
}

export async function submitDialog(page: Page, name = 'Сохранить') {
  await page.getByRole('dialog').getByRole('button', { name, exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
}
