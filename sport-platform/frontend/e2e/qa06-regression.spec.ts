import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { eventId, unitId, openAuditPage, navigate, scenario } from './qa06-helpers'

declare global {
  interface Window { __qa06Scroll: unknown[]; __qa06LiveCalls: number }
}

const base = `/organizer/events/${eventId}`

for (const path of ['/organizer/events?all=1', '/operator/payments', '/events/demo/disciplines/M-50-FREE/results']) {
  test(`QA06-01 table scroll stays inside its container: ${path}`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, path, path.includes('organizer') ? 'organizer' : path.includes('operator') ? 'operator' : '')
    await expect(page.locator('table tbody tr').first()).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
    const wrapper = page.locator('[class*=tableWrap]')
    await wrapper.evaluate(node => { node.scrollLeft = node.scrollWidth })
    if (path === '/operator/payments') {
      // UX-22: mobile rows become cards with actions inside the viewport.
      expect(await wrapper.evaluate(node => node.scrollWidth)).toBeLessThanOrEqual(375)
      await expect(page.getByRole('link', { name: /Открыть заявку:/ }).first()).toBeInViewport()
      return
    }
    expect(await wrapper.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
    if (path.startsWith('/events/')) await expect(page.getByRole('columnheader', { name: 'Статус', exact: true })).toBeInViewport()
    else await expect(page.getByRole('link', { name: path.includes('organizer') ? 'Открыть' : 'Открыть', exact: true }).first()).toBeInViewport()
  })
}

test('QA06-02 QR keeps its square geometry at 375px', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, base, 'organizer')
  const box = await page.locator('[class*=publicCard] svg').boundingBox()
  expect(box?.width).toBe(140)
  expect(box?.height).toBe(140)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test('QA06-03 admin form uses separated, full-width labelled controls', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, '/admin/registrations/review', 'platform_admin')
  await page.getByRole('button', { name: /Выбрать заявку:/ }).first().click()
  const comment = page.getByLabel('Комментарий для участника')
  const box = await comment.boundingBox()
  expect(box?.width).toBeGreaterThan(300)
  expect(await page.locator('[class*=decisions]').evaluate(node => getComputedStyle(node).display)).toBe('grid')
  await expect(page.locator('body')).not.toContainText('payment flow')
  await expect(page.locator('body')).not.toContainText('production')
})

test('QA06-04 table headings use the readable muted token', async ({ page }) => {
  await openAuditPage(page, '/operator/payments', 'operator')
  await expect(page.locator('tbody tr').first()).toBeVisible()
  const color = await page.locator('th').first().evaluate(node => getComputedStyle(node).color)
  expect(color).toBe('rgb(82, 97, 106)')
})

test('QA06-06 dates use DD.MM.YYYY', async ({ page }) => {
  await openAuditPage(page, '/e/demo')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Осенний кубок')
  await expect(page.getByText('15.10.2026 · Самара', { exact: true })).toBeVisible()
})

for (const [name, path, button] of [
  ['event', base, 'Опубликовать'],
  ['start list', `${base}/heats`, 'Опубликовать протокол'],
  ['results', `${base}/results`, 'Опубликовать результаты вида'],
]) {
  test(`QA06-07 publication asks for confirmation: ${name}`, async ({ page }) => {
    await openAuditPage(page, path, 'organizer')
    if (name === 'results') {
      const fixture = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures() })()')
      for (const discipline of fixture.event.disciplines) await scenario(page, `event-disciplines/${discipline.id}/competition-units`, fixture.heats.filter((h: { eventDisciplineId: string }) => h.eventDisciplineId === discipline.id).map((h: { entries: object[] }) => ({ ...h, status: 'PUBLISHED', entries: h.entries.map(e => ({ ...e, rawValue: '00:32.15', resultStatus: 'VALID' })) })))
      for (const heat of fixture.heats) await scenario(page, `competition-units/${heat.id}`, { ...heat, status: 'PUBLISHED', entries: heat.entries.map((entry: object) => ({ ...entry, rawValue: '00:32.15', resultStatus: 'VALID' })) })
      await navigate(page, '/'); await navigate(page, path)
    }
    const target = page.getByRole('button', { name: button, exact: true }).filter({ visible: true })
    const index = 0
    await expect(target.nth(index)).toBeEnabled()
    await target.nth(index).click()
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 1000 })
  })
}

test('QA06-08 empty heat cannot be published', async ({ page }) => {
  await openAuditPage(page, '/', 'operator')
  await scenario(page, `competition-units/${unitId}`, { id: unitId, eventDisciplineId: '00000003-1111-4111-8111-000000000001', label: 'Пустой заплыв', status: 'DRAFT', entries: [] })
  await navigate(page, `/operator/competition-units/${unitId}`)
  await expect(page.getByText('В заплыве пока нет участников.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Опубликовать результаты', exact: true })).toBeDisabled()
})

test('QA06-09 network failure has Russian recovery guidance', async ({ page }) => {
  await openAuditPage(page, '/', 'organizer')
  await scenario(page, `events/${eventId}`, null, 'network')
  await navigate(page, base)
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('alert')).not.toContainText('Failed to fetch')
  await expect(page.getByRole('alert')).toContainText(/связ|подключен/i)
})

test('QA06-21 organizer exposes a single accessible main landmark', async ({ page }) => {
  await openAuditPage(page, base, 'organizer')
  await expect(page.getByRole('main')).toHaveCount(1)
})

test('QA06-11 focus ring has 3:1 contrast on white', async ({ page }) => {
  await openAuditPage(page, '/login')
  await page.keyboard.press('Tab')
  const outline = await page.evaluate(() => { const style = getComputedStyle(document.activeElement!); if (style.boxShadow === 'none') throw new Error('Focus ring is missing'); const probe = document.createElement('span'); probe.style.color = 'var(--color-focus)'; document.body.append(probe); const color = getComputedStyle(probe).color; probe.remove(); return color })
  const channels = outline.match(/[\d.]+/g)!.map(Number)
  const alpha = channels[3] ?? 1
  const luminance = channels.slice(0, 3).map(v => (v * alpha + 255 * (1 - alpha)) / 255)
    .map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    .reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0)
  expect(1.05 / (luminance + 0.05)).toBeGreaterThanOrEqual(3)
})

test('QA06-12 reduced motion avoids smooth validator scrolling', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await openAuditPage(page, `${base}/heats`, 'organizer')
  await scenario(page, 'event-disciplines/:id/competition-units/validate', { violations: [{ severity: 'WARNING', code: 'SMALL_GROUP', message: 'Проверка прокрутки', unitId }] })
  await page.evaluate(() => {
    window.__qa06Scroll = []
    Element.prototype.scrollIntoView = function (options) { window.__qa06Scroll.push(options) }
  })
  await page.getByRole('button', { name: 'Проверить заплывы', exact: true }).click()
  await page.getByRole('button', { name: 'Предупреждение: Проверка прокрутки', exact: true }).first().click()
  expect(await page.evaluate(() => window.__qa06Scroll)).not.toContainEqual({ behavior: 'smooth', block: 'center' })
})

test('QA06-13 valid WITHDRAWN registration shows its status to the participant', async ({ page }) => {
  await openAuditPage(page, '/', 'participant')
  const rows = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures().registrations.slice(0, 2) })()')
  rows[0].status = 'WITHDRAWN'
  await scenario(page, 'registrations/me', rows)
  await navigate(page, '/my/registrations')
  await expect(page.getByRole('heading', { name: 'Мои заявки', exact: true })).toBeVisible()
  await expect(page.locator('[class*=card]').first()).toBeVisible()
  await expect(page.locator('body')).toContainText('Снята')
})

test('QA06-10 withdraw confirmation returns keyboard focus to its trigger', async ({ page }) => {
  await openAuditPage(page, `${base}/entries`, 'organizer')
  const trigger = page.getByRole('button', { name: 'Ещё ▾', exact: true }).first()
  // Finish scrolling before opening a menu that closes on scroll events.
  await trigger.scrollIntoViewIfNeeded()
  await trigger.click()
  await page.getByRole('region', { name: 'Действия заявки' }).getByRole('button', { name: 'Снять', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'Снять заявку?' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(trigger).toBeFocused()
})

test('QA06-14 live waits 15 seconds before its next poll', async ({ page }) => {
  await openAuditPage(page, '/')
  await page.evaluate(() => {
    const original = window.fetch
    window.__qa06LiveCalls = 0
    window.fetch = (...args) => {
      if (String(args[0]).endsWith('/api/v1/public/events/demo/live')) window.__qa06LiveCalls++
      return original(...args)
    }
  })
  await navigate(page, '/e/demo/live')
  await expect(page.getByRole('table').first()).toBeVisible()
  const initialCalls = await page.evaluate(() => window.__qa06LiveCalls)
  expect(initialCalls).toBeGreaterThan(0)
  await page.waitForTimeout(5500)
  expect(await page.evaluate(() => window.__qa06LiveCalls)).toBe(initialCalls)
})

test('QA06-15 print removes the page background', async ({ page }) => {
  await openAuditPage(page, '/e/demo/start-list')
  await expect(page.getByRole('table').first()).toBeVisible()
  await page.evaluate(() => { document.documentElement.dataset.theme = 'dark' })
  await page.emulateMedia({ media: 'print' })
  expect(await page.locator('body').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(255, 255, 255)')
  for (const nav of await page.getByRole('navigation').all()) await expect(nav).toBeHidden()
  expect(await page.locator('html').evaluate(node => getComputedStyle(node).colorScheme)).toBe('light')
  expect(await page.locator('article').first().evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(255, 255, 255)')
  const printTokens = await page.locator('html').evaluate(node => {
    const css = getComputedStyle(node)
    const statuses = ['draft', 'published', 'live', 'finished', 'withdrawn', 'dns', 'dnf', 'dsq']
    return {
      soft: ['primary', 'danger', 'success', 'warning', 'surface-muted'].map(name => css.getPropertyValue(name === 'surface-muted' ? '--color-surface-muted' : `--color-${name}-soft`).trim()),
      ink: statuses.map(name => css.getPropertyValue(`--status-${name}`).trim()),
      statusSoft: statuses.map(name => css.getPropertyValue(`--status-${name}-soft`).trim()),
    }
  })
  expect(printTokens.soft.every(color => color === '#ffffff')).toBe(true)
  expect(printTokens.statusSoft.every(color => color === '#ffffff')).toBe(true)
  expect(printTokens.ink.every(color => color === '#000000')).toBe(true)
})

for (const target of ['organizer', 'participant']) {
  test(`QA06-16 long names do not widen the page: ${target}`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, '/', target)
    const fixture = await page.evaluate('(async () => { const { createFixtures } = await import("/src/mocks/fixtures.ts"); return createFixtures() })()')
    if (target === 'organizer') {
      fixture.event.title = 'Соревнования ' + 'ОченьДлинноеНазвание'.repeat(8)
      await scenario(page, `events/${eventId}`, fixture.event)
      await navigate(page, base)
      await expect(page.getByRole('heading', { level: 1 })).toContainText('ОченьДлинноеНазвание')
    } else {
      fixture.registrations[0].participantSnapshot.lastName = 'РимскийКорсаков'.repeat(5)
      fixture.registrations[0].participantSnapshot.clubName = 'МуниципальнаяСпортивнаяШкола'.repeat(6)
      await scenario(page, 'registrations/me', fixture.registrations.slice(0, 2))
      await navigate(page, '/my/registrations')
      await expect(page.locator('body')).toContainText('МуниципальнаяСпортивнаяШкола')
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
    if (target === 'organizer') {
      await navigate(page, `${base}/entries`)
      const table = page.locator('[class*=table]').filter({ has: page.locator('table') }).first()
      await expect(page.locator('table tbody tr').first()).toBeVisible()
      expect(await page.locator('table td').first().evaluate(node => getComputedStyle(node).overflowWrap)).toBe('normal')
      await table.evaluate(node => { node.scrollLeft = node.scrollWidth })
      expect(await table.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
    }
  })
}

test('QA06-18 payment loading is announced as a status', async ({ page }) => {
  await openAuditPage(page, '/', 'participant')
  await scenario(page, 'registrations/me', [], 'loading')
  await page.evaluate(() => {
    history.pushState(null, '', '/my/registrations/00000004-1111-4111-8111-000000000001/payment')
    dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByRole('heading', { name: 'Загружаем оплату', exact: true })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Загружаем оплату' })).toContainText('Загружаем оплату')
})

test('QA06-20 Samara starts keep venue time in a Moscow browser', async ({ page }) => {
  await openAuditPage(page, '/')
  const event = await page.evaluate('(async () => { const { demoEvent } = await import("/src/mocks/demo-data.ts"); return { ...demoEvent, venue: { ...demoEvent.venue, city: "Самара", timezone: "Europe/Samara" } } })()')
  await scenario(page, 'public/events/demo', event)
  await scenario(page, 'public/events/demo/schedule', [{
    sessionName: 'Утро в Самаре', disciplineName: '50 м вольный стиль', heatNumber: 1,
    sequenceInSession: 1, scheduledAt: '2026-10-17T10:00:00+04:00', status: 'PUBLISHED',
  }])
  await navigate(page, '/e/demo/schedule')
  await expect(page.locator('li').filter({ hasText: 'Заплыв 1' }).last()).toContainText('Заплыв 1')
  await expect(page.locator('li').filter({ hasText: 'Заплыв 1' }).last()).toContainText('10:00')
})

test('QA06-22 judge can discover the results panel from primary navigation', async ({ page }) => {
  await openAuditPage(page, '/', 'judge')
  await expect(page.getByRole('navigation', { name: 'Основная навигация' })
    .getByRole('link', { name: 'Судейство', exact: true })).toBeVisible()
})

for (const width of [1366, 375]) {
  test(`D01 judge time controls stay large and use tabular numerals at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 812 })
    await openAuditPage(page, `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge')
    const input = page.getByRole('textbox', { name: /^Время, дорожка \d+$/ }).first()
    const appearance = await input.evaluate(node => {
      const css = getComputedStyle(node)
      return { fontSize: css.fontSize, height: node.getBoundingClientRect().height, numbers: css.fontVariantNumeric }
    })
    expect(appearance.fontSize).toBe('24px')
    expect(appearance.height).toBeGreaterThanOrEqual(56)
    expect(appearance.numbers).toContain('tabular-nums')
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  })
}

for (const route of ['start-list', 'live']) {
  test(`D01 review 1: ${route} keeps years and times on one line at 375px`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, `/e/demo/${route}`)
    const times = page.locator('.fl-lanes tbody td:last-child')
    await expect(times.first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const lines = await page.locator('.fl-lanes tbody td:nth-child(3), .fl-lanes tbody td:last-child').evaluateAll(cells =>
      cells.map(cell => {
        const range = document.createRange()
        range.selectNodeContents(cell)
        const tops = Array.from(range.getClientRects()).filter(rect => rect.height > 0).map(rect => Math.round(rect.top))
        return { text: cell.textContent, lines: new Set(tops).size }
      }),
    )
    expect(lines.length).toBeGreaterThanOrEqual(16)
    expect(lines.filter(cell => cell.lines !== 1)).toEqual([])
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
    const table = page.locator('.fl-lanes').first()
    if (await table.evaluate(node => node.scrollWidth > node.clientWidth)) {
      await table.evaluate(node => { node.scrollLeft = node.scrollWidth })
      expect(await table.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
    }
  })
}

for (const route of ['start-list', 'live', 'results']) {
  test(`D01 review 2: ${route} never wraps within a word at 375px`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await openAuditPage(page, `/e/demo/${route}`)
    const cells = page.locator('.fl-protocol tbody td')
    await expect(cells.first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const words = await cells.evaluateAll(nodes => nodes.flatMap(cell => {
      const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT)
      const measured = []
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        for (const match of (node.textContent ?? '').matchAll(/\S+/gu)) {
          const range = document.createRange()
          range.setStart(node, match.index)
          range.setEnd(node, match.index + match[0].length)
          const tops = Array.from(range.getClientRects()).filter(rect => rect.height > 0).map(rect => Math.round(rect.top))
          measured.push({ word: match[0], lines: new Set(tops).size })
        }
      }
      return measured
    }))
    expect(words.length).toBeGreaterThan(0)
    expect(words.filter(word => word.lines !== 1)).toEqual([])
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  })
}

test('D01 review 1: all eight live lanes fit above navigation at 1366x768', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openAuditPage(page, '/e/demo/live')
  const rows = page.locator('.fl-lanes tbody tr')
  await expect(rows).toHaveCount(8)
  await page.evaluate(() => document.fonts.ready)
  const nav = await page.getByRole('navigation', { name: 'Разделы соревнования' }).boundingBox()
  for (const row of await rows.all()) {
    const box = await row.boundingBox()
    expect(box!.y).toBeGreaterThanOrEqual(0)
    expect(box!.y + box!.height).toBeLessThanOrEqual(768)
    expect(box!.y).toBeGreaterThanOrEqual(nav!.y + nav!.height)
  }
  const board = await page.locator('section').filter({ has: page.locator('.fl-lanes') }).first().boundingBox()
  expect(board!.y + board!.height).toBeLessThanOrEqual(768)
  expect(board!.y).toBeGreaterThanOrEqual(nav!.y + nav!.height)
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
})

test('UX-20: judge lane and time remain visible without horizontal scrolling at 375px', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, `/operator/competition-units/${unitId}?eventId=${eventId}`, 'judge')
  const lane = page.getByRole('article').first()
  await expect(lane).toBeVisible()
  await expect(page.getByRole('table')).toHaveCount(0)
  const input = lane.getByRole('textbox')
  await expect(input).toBeInViewport()
  const box = (await input.boundingBox())!
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(375)
})

test('D01 review 1: login card retains the four-pixel primary top border', async ({ page }) => {
  await openAuditPage(page, '/login')
  const card = page.locator('[class*=card]').filter({ has: page.getByRole('heading', { name: 'Войти', exact: true }) })
  expect(await card.evaluate(node => getComputedStyle(node).borderTopWidth)).toBe('4px')
})

for (const route of ['live', 'results']) {
  test(`D01/D02 owner: ${route} defaults to dark tokens and prints on white`, async ({ page }) => {
    await openAuditPage(page, `/e/demo/${route}`)
    await expect(page.locator(route === 'live' ? '.fl-lanes' : '.fl-results').first()).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    expect(await page.locator('html').evaluate(node => getComputedStyle(node).colorScheme)).toBe('dark')
    if (route === 'results') {
      await scenario(page, 'public/events/demo/results', { disciplines: [] })
      await navigate(page, '/e/demo/live')
      await navigate(page, '/e/demo/results')
      await expect(page.getByText(/Результаты ещё не опубликованы/)).toBeVisible()
      expect(await page.locator('html').evaluate(node => getComputedStyle(node).colorScheme)).toBe('dark')
    }
    await page.emulateMedia({ media: 'print' })
    expect(await page.locator('html').evaluate(node => getComputedStyle(node).colorScheme)).toBe('light')
    expect(await page.locator('body').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(255, 255, 255)')
  })
}

test('D01 owner: print is a ghost action and central lanes have no color fill', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, '/e/demo/start-list')
  await expect(page.locator('.fl-lanes').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Печать', exact: true })).toHaveClass(/fl-btn-ghost/)
  const lanes = await page.locator('.fl-lanes').first().locator('tbody th').evaluateAll(cells => cells.map(cell => {
    const css = getComputedStyle(cell)
    return { border: css.borderInlineStartColor, background: css.backgroundColor, weight: css.fontWeight }
  }))
  expect(lanes[0].border).toBe('rgba(0, 0, 0, 0)')
  for (const index of [3, 4]) {
    expect(lanes[index].border).toBe('rgb(24, 33, 38)')
    expect(lanes[index].background).toBe('rgba(0, 0, 0, 0)')
    expect(lanes[index].weight).toBe('700')
  }
})

for (const width of [375, 768, 1366]) {
  test(`D04-2a shared shell, menus and tabs at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 768 ? 1024 : 812 })
    await openAuditPage(page, base, 'organizer')
    await expect(page.getByRole('banner')).toHaveCSS('height', '56px')
    if (width === 375) {
      const burger = page.getByRole('button', { name: 'Открыть меню' })
      await expect(burger).toHaveCSS('width', '44px')
      await expect(burger).toHaveCSS('height', '44px')
      await burger.click()
      const drawer = page.getByRole('dialog', { name: 'Меню кабинета' })
      await expect(drawer.getByRole('navigation', { name: 'Разделы', exact: true })).toBeVisible()
      await expect(drawer.getByRole('navigation', { name: 'Навигация кабинета' })).toBeVisible()
      await expect(drawer.getByRole('link', { name: 'Сменить пароль' })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
      const targets = await drawer.locator('a, button, summary').evaluateAll(nodes => nodes.map(node => ({ text: node.textContent, width: node.getBoundingClientRect().width, height: node.getBoundingClientRect().height })))
      expect(targets.filter(target => target.height < 44 || target.width < 44)).toEqual([])
      await page.keyboard.press('Escape')
      await expect(drawer).not.toBeVisible()
      await expect(burger).toBeFocused()
    } else {
      const profile = page.getByRole('banner').locator('summary')
      await profile.click()
      const panel = page.getByRole('banner').locator('details > div')
      expect((await panel.boundingBox())!.width).toBeGreaterThanOrEqual(240)
      await expect(panel.getByRole('button', { name: 'Выйти' })).toBeVisible()
      await expect(panel.getByRole('button', { name: 'Тёмная тема' })).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(profile).toBeFocused()
    }
    if (width < 1366) {
      const more = page.getByRole('button', { name: 'Другие разделы соревнования' })
      await more.click()
      await expect(page.getByRole('menuitem', { name: 'Аналитика' })).toBeVisible()
      if (width === 375) {
        const box = (await page.getByRole('menu', { name: 'Другие разделы соревнования' }).boundingBox())!
        expect(box.x).toBe(0)
        expect(box.width).toBe(width)
      }
      await page.keyboard.press('Escape')
      await expect(more).toBeFocused()
    } else await expect(page.getByRole('navigation', { name: 'Разделы соревнования' }).getByRole('link', { name: 'Аналитика', exact: true })).toBeAttached()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
    await navigate(page, '/e/demo/start-list')
    const nav = page.getByRole('navigation', { name: 'Разделы соревнования' })
    if (width >= 1024) {
      expect((await nav.boundingBox())!.y).toBe(56)
      await expect(nav.locator('..').locator('..')).toHaveCSS('position', 'static')
    } else expect((await nav.boundingBox())!.y).toBeGreaterThan(700)
    await navigate(page, '/e/demo/register')
    await expect(page.getByRole('navigation', { name: 'Разделы соревнования' })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  })
}

test('D04-2a row menu escapes table clipping and keeps its actions clickable', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openAuditPage(page, '/dev/ui')
  const cards = page.locator('.fl-rowcards')
  await cards.evaluate(node => node.scrollIntoView({ block: 'center' }))
  const trigger = cards.getByRole('button').last()
  await trigger.click()
  const menu = page.getByRole('menu')
  await expect(menu).toBeVisible()
  const tableBox = (await cards.boundingBox())!, menuBox = (await menu.boundingBox())!
  expect(menuBox.y + menuBox.height).toBeGreaterThan(tableBox.y + tableBox.height)
  for (const item of await menu.getByRole('menuitem').all()) {
    await expect(item).toBeInViewport()
    expect(await item.evaluate(node => { const box = node.getBoundingClientRect(); return node.contains(document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)) })).toBe(true)
  }
  await menu.getByRole('menuitem', { name: 'Открыть заявку' }).click()
  await expect(page.getByRole('status')).toContainText('Открыта заявка')
  await expect(trigger).toBeFocused()
})

test('D04-2a route loading, error and missing states retain one page heading', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await openAuditPage(page, '/')
  await scenario(page, 'public/events/demo', null, 'loading')
  await page.evaluate(() => { history.pushState(null, '', '/e/demo'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByRole('status').filter({ hasText: 'Загружаем данные' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  await expect(page.locator('.fl-skeleton > div')).toHaveCount(3)
  await navigate(page, '/')
  await scenario(page, 'public/events/demo', null, '500')
  await navigate(page, '/e/demo')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  await expect(page.getByRole('button', { name: 'Повторить', exact: true })).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Загружаем сведения')
  await navigate(page, '/e/missing')
  await expect(page.getByRole('heading', { name: 'Соревнование не найдено' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test('D04-2a cards table keeps keyboard scrolling at tablet width', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 1024 })
  await openAuditPage(page, '/dev/ui')
  const cards = page.locator('.fl-rowcards')
  await expect(cards).toHaveAttribute('tabindex', '0')
  expect(await cards.evaluate(node => node.scrollWidth > node.clientWidth)).toBe(true)
  await cards.focus()
  await expect(cards).toBeFocused()
  await page.keyboard.press('ArrowRight')
  await expect.poll(() => cards.evaluate(node => node.scrollLeft)).toBeGreaterThan(0)
  const accessibility = await new AxeBuilder({ page }).include('.fl-rowcards').analyze()
  expect(accessibility.violations.filter(item => ['serious', 'critical'].includes(item.impact ?? ''))).toEqual([])
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => cards.getAttribute('tabindex')).toBeNull()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
