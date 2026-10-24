import { test, expect, type Locator, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mkdir } from 'node:fs/promises'
import { eventId, unitId, openAuditPage, navigate, scenario } from './qa06-helpers'

const panel = `/operator/competition-units/${unitId}?eventId=${eventId}`

async function noPageOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
}

async function hitTarget(target: Locator) {
  expect(await target.evaluate(node => {
    const box = node.getBoundingClientRect()
    const hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
    return hit === node || node.contains(hit)
  })).toBe(true)
}

async function screenshot(page: Page, name: string) {
  if (!process.env.D04_SCREENSHOTS) return
  await mkdir('../../docs/design/screenshots', { recursive: true })
  await page.screenshot({ path: `../../docs/design/screenshots/D04-2m-${name}.png` })
}

for (const width of [375, 767, 768, 1366]) {
  test(`UX-20 judge lane layout and action bounds at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 812 })
    await page.addInitScript(() => sessionStorage.setItem('flounder.auth-session', JSON.stringify({
      accessToken: `demo.${btoa(JSON.stringify({ roles: ['judge'] }))}.demo`,
      tokenType: 'Bearer', expiresAt: Date.now() + 3600000,
    })))
    await page.goto('/operator/competition-units/00000010-1111-4111-8111-000000000001?eventId=11111111-1111-4111-8111-111111111111')
    const inputs = page.getByRole('textbox')
    await expect(inputs.first()).toBeVisible()
    await noPageOverflow(page)
    const actions = page.getByRole('group', { name: 'Действия заплыва' })
    if (width <= 767) {
      await expect(page.getByRole('table')).toHaveCount(0)
      await expect(page.getByRole('article')).toHaveCount(await inputs.count())
      await actions.locator('summary').click()
      await hitTarget(actions.locator('.fl-btn-primary'))
      await noPageOverflow(page)
      for (const status of ['DNS', 'DNF', 'DSQ']) {
        const box = (await page.getByRole('button', { name: status, exact: true }).first().boundingBox())!
        expect(box.height).toBeGreaterThanOrEqual(48)
        expect(box.height).toBeLessThanOrEqual(56)
      }
      for (const button of await actions.getByRole('button').all()) {
        const box = (await button.boundingBox())!
        expect(box.x).toBeGreaterThanOrEqual(0)
        expect(box.x + box.width).toBeLessThanOrEqual(width)
        expect(box.height).toBeGreaterThanOrEqual(48)
        await hitTarget(button)
      }
      await page.keyboard.press('Escape')
      await expect(actions.locator('details')).not.toHaveAttribute('open', '')
      for (const target of [page.getByRole('link', { name: '← К соревнованию' }), page.locator('summary').filter({ hasText: 'Как вводить' })]) {
        expect((await target.boundingBox())!.height).toBeGreaterThanOrEqual(44)
      }
      await inputs.last().focus()
      const last = (await inputs.last().boundingBox())!
      expect(last.y + last.height).toBeLessThanOrEqual((await actions.boundingBox())!.y)
      await inputs.first().fill('003215')
      await inputs.first().press('ArrowDown')
      await expect(inputs.nth(1)).toBeFocused()
      await inputs.nth(1).press('ArrowUp')
      await expect(inputs.first()).toBeFocused()
      await inputs.first().press('Enter')
      await expect(inputs.nth(1)).toBeFocused()
      await page.setViewportSize({ width: 1366, height: 812 })
      await expect(page.getByRole('table')).toBeVisible()
      await expect(inputs.first()).toHaveValue('00:32.15')
    } else {
      await expect(page.getByRole('table')).toBeVisible()
      await expect(page.getByRole('article')).toHaveCount(0)
      if (width === 768) await expect(page.locator('[data-scroll-edges]')).toHaveAttribute('data-scroll-edges', /right/)
    }
  })
}

for (const width of [375, 768, 1366]) {
  test(`D04-2m judge screen, states and accessibility at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 768 ? 1024 : width === 1366 ? 768 : 812 })
    await openAuditPage(page, panel, 'judge')
    await expect(page.getByRole('textbox').first()).toBeVisible()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('ЗАПЛЫВ 1 из 2')
    await noPageOverflow(page)
    expect(await page.getByRole('heading', { level: 1 }).evaluate(node => getComputedStyle(node).fontSize)).toBe('28px')
    expect(await page.getByRole('textbox').first().evaluate(node => getComputedStyle(node).fontFamily)).toContain('Oswald')
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
    await noPageOverflow(page)
    const section = page.locator('section').filter({ has: page.getByRole('heading', { level: 1 }) }).last()
    const axe = await new AxeBuilder({ page }).include('main').analyze()
    expect(axe.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
    await expect(section.getByText('Клуб не указан')).toHaveCount(0)
    if (width === 375) {
      await expect(section.getByText(/Заявочное время|Место:|В группе:/)).toHaveCount(0)
      await screenshot(page, '375-data-dark')
    } else {
      await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'light'))
      await screenshot(page, `${width}-data`)
    }
    for (const mode of ['loading', 'empty', 'error']) {
      await navigate(page, '/operator/results')
      // Wait for the panel to unmount; consecutive history updates can otherwise be batched.
      await expect(page.getByRole('heading', { name: 'Судейство', exact: true })).toBeVisible()
      const fixture = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures() })()')
      await scenario(page, `competition-units/${unitId}`, mode === 'empty' ? { ...fixture.heats[0], eventId, entries: [] } : { message: 'Сервис временно недоступен. Повторите попытку.' }, mode === 'loading' ? 'loading' : mode === 'error' ? '500' : 'json')
      // navigate() waits for a table for this URL, so use the router directly for state checks.
      await page.evaluate(path => { history.pushState(null, '', path); dispatchEvent(new PopStateEvent('popstate')) }, panel)
      if (mode === 'loading') await expect(page.getByRole('status', { name: 'Загружаем заплыв…' })).toBeVisible()
      if (mode === 'empty') await expect(page.getByText('В заплыве пока нет участников.', { exact: true })).toBeVisible()
      if (mode === 'error') await expect(page.getByRole('alert').first()).toContainText('Сервис временно недоступен')
      await noPageOverflow(page)
      if (width === 375) await screenshot(page, `375-${mode}`)
    }
  })

  test(`D04-2m heat selection and disabled explanations at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 812 })
    await openAuditPage(page, '/operator/results', 'judge')
    const open = page.getByRole('button', { name: 'Открыть выбранный заплыв' })
    await expect(open).toBeDisabled()
    await expect(open).toHaveAttribute('title', 'Сначала выберите заплыв.')
    await noPageOverflow(page)
    await page.getByRole('combobox', { name: 'Соревнование', exact: true }).selectOption(eventId)
    await expect(page.getByRole('link', { name: 'Открыть →' }).first()).toBeVisible()
    await noPageOverflow(page)
    if (width === 375) {
      const link = page.getByRole('link', { name: 'Открыть →' }).first()
      expect((await link.boundingBox())!.height).toBeGreaterThanOrEqual(44)
      await hitTarget(link)
    }
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'))
    const axe = await new AxeBuilder({ page }).include('main').analyze()
    expect(axe.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
  })
}

declare global { interface Window { __d04StartLists: number } }
test('D04-2m fetches clubs on mobile only and caches one start list per slug across heats', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 812 })
  await openAuditPage(page, '/operator/results', 'judge')
  await page.evaluate(() => {
    window.__d04StartLists = 0
    const original = window.fetch
    window.fetch = (...args) => {
      if (String(args[0]).endsWith('/start-list')) window.__d04StartLists++
      return original(...args)
    }
  })
  const fixture = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures() })()')
  await scenario(page, `public/events/${fixture.event.publicSlug}/start-list`, { sessions: [{ disciplines: [{ heats: fixture.heats.slice(0, 2).map((heat: { id: string; entries: { registrationId: string }[] }, index: number) => ({ id: heat.id, lanes: [{ registrationId: heat.entries[0].registrationId, athlete: { club: index === 0 ? 'Клуб первой смены' : 'Клуб второй смены' } }] })) }] }] })
  await navigate(page, panel)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('ЗАПЛЫВ 1 из 2')
  expect(await page.evaluate(() => window.__d04StartLists)).toBe(0)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => page.evaluate(() => window.__d04StartLists)).toBe(1)
  await expect(page.getByText('Клуб первой смены', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 1366, height: 812 })
  await page.setViewportSize({ width: 375, height: 812 })
  const next = unitId.replace(/1$/, '2')
  await navigate(page, `/operator/competition-units/${next}?eventId=${eventId}`)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('ЗАПЛЫВ 2 из 2')
  await expect(page.getByText('Клуб второй смены', { exact: true })).toBeVisible()
  await expect(page.getByText('Клуб первой смены', { exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => window.__d04StartLists)).toBe(1)
})
