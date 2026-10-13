import { expect, type Page } from '@playwright/test'
import { mkdir, stat } from 'node:fs/promises'
import { resolve } from 'node:path'

export const evidence = resolve('../../docs/qa/screenshots')
export async function screenshots(page: Page, name: string) {
  await mkdir(evidence, { recursive: true })
  const viewport = page.viewportSize()!
  for (const width of [1366, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    await page.screenshot({ path: resolve(evidence, `QA05-${name}-${width}.png`), fullPage: await page.getByRole('dialog').count() === 0 })
  }
  await page.setViewportSize(viewport)
}

export async function login(page: Page, email: string, destination: string) {
  await page.goto(`/login?next=${encodeURIComponent(destination)}`)
  await page.getByLabel('Email').fill(email)
  await screenshots(page, `login-${email.split('@')[0]}`)
  await page.getByLabel('Пароль', { exact: true }).fill(process.env.QA05_PASSWORD ?? 'Demo2026!')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login')
}

// Requests originate in the logged-in browser; credentials never enter evidence.
export async function api<T>(page: Page, path: string, method = 'GET', body?: unknown): Promise<T> {
  return page.evaluate(async ({ path, method, body, base }) => {
    const session = JSON.parse(sessionStorage.getItem('flounder.auth-session') ?? '{}')
    const response = await fetch(`${base}/api/v1${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(session.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`)
    return response.status === 204 ? undefined : response.json()
  }, { path, method, body, base: process.env.QA05_API_URL ?? 'http://localhost:8080' })
}

export async function pdf(page: Page, name: string) {
  await page.emulateMedia({ media: 'print' })
  const blocks = name === 'results' ? page.locator('#public-content table tbody tr') : page.locator('article').filter({ has: page.locator('table') })
  expect.soft(await blocks.count(), `${name}: protocol has heats`).toBeGreaterThan(0)
  for (const block of await blocks.all()) {
    expect.soft(await block.evaluate(node => getComputedStyle(node).breakInside), `${name}: no heat split`).toBe('avoid')
  }
  await mkdir(resolve('../../docs/qa'), { recursive: true })
  const path = resolve(`../../docs/qa/QA05-${name}.pdf`)
  await page.pdf({ path, format: 'A4', printBackground: true, margin: { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' } })
  expect.soft((await stat(path)).size).toBeGreaterThan(1_000)
  await page.emulateMedia({ media: 'screen' })
}
