import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import type { EventGenerationResult, Heat, ImportReport, OrganizerEventDetails, OrganizerRegistration, Session, Violation } from '../src/api/organizer.types'
import type { CompetitionUnitDetailsResponse } from '../src/api/results.api'
import type { Live, Results, StartList } from '../src/features/public/public.types'
import { api, evidence, login, pdf, screenshots } from './qa05-support'

test('QA05: seven-step demo with a real backend and demo seed', async ({ browser }, info) => {
  const seed = JSON.parse(readFileSync('work/qa05/seed.json', 'utf8')) as { eventId: string; slug: string; suffix: string }
  const base = `/organizer/events/${seed.eventId}`
  const publicBase = `/e/${seed.slug}`
  const contexts = await Promise.all(Array.from({ length: 4 }, () => browser.newContext(info.project.use)))
  for (const context of contexts) {
    context.setDefaultTimeout(10_000)
    context.setDefaultNavigationTimeout(20_000)
  }
  const [org, parent, sec, spectator] = await Promise.all(contexts.map(context => context.newPage()))
  const audit: { kind: string; message: string }[] = []
  const steps: { name: string; durationMs: number; errors: number; blocked?: string }[] = []
  const timings: Record<string, number> = {}
  let failedPrerequisite: number | undefined
  for (const context of contexts) context.on('page', monitor)
  for (const page of [org, parent, sec, spectator]) monitor(page)
  function monitor(page: Page) {
    page.on('console', message => { if (message.type() === 'error') audit.push({ kind: 'console', message: message.text() }) })
    page.on('pageerror', error => audit.push({ kind: 'pageerror', message: error.message }))
    page.on('response', response => { if (response.status() >= 400) audit.push({ kind: 'http', message: `${response.status()} ${response.request().method()} ${response.url()}` }) })
    page.on('requestfailed', request => {
      if (!request.failure()?.errorText.includes('ERR_ABORTED')) audit.push({ kind: 'network', message: `${request.url()}: ${request.failure()?.errorText}` })
    })
  }
  async function step(name: string, action: () => Promise<void>) {
    if (failedPrerequisite !== undefined) {
      steps.push({ name, durationMs: 0, errors: 0, blocked: `skipped: step ${failedPrerequisite} failed` })
      return
    }
    const number = Number(name.split('.')[0])
    const started = Date.now()
    const errors = info.errors.length
    let blocked: string | undefined
    await test.step(name, async () => {
      try { await action() } catch (error) {
        blocked = error instanceof Error ? error.message : String(error)
        expect.soft(blocked, `${name}: failed or blocked`).toBeUndefined()
      }
    })
    steps.push({ name, durationMs: Date.now() - started, errors: info.errors.length - errors, blocked })
    if (blocked && (number === 2 || number === 4)) failedPrerequisite = number
  }
  let event!: OrganizerEventDetails
  let heats: Heat[] = []
  async function loadEvent() {
    event = await api<OrganizerEventDetails>(org, `/events/${seed.eventId}`)
    event.disciplines = await api<OrganizerEventDetails['disciplines']>(org, `/events/${seed.eventId}/disciplines`)
  }
  async function loadHeats() {
    const summaries = (await Promise.all(event.disciplines.map(d => api<Heat[]>(org, `/event-disciplines/${d.id}/competition-units`)))).flat()
    heats = await Promise.all(summaries.map(async summary => ({ ...summary, ...await api<Heat>(org, `/competition-units/${summary.id}`) })))
    return heats
  }
  async function registrations() {
    return api<OrganizerRegistration[]>(org, `/registrations?eventId=${seed.eventId}`)
  }
  async function settle(page: Page) {
    await expect(page.getByRole('status').filter({ hasText: 'Выполняем действие' })).toHaveCount(0)
  }

  try {
    await step('1. Anonymous overview, program, organizer QR and settings', async () => {
      await spectator.goto(publicBase)
      await expect(spectator.getByRole('heading', { name: `Открытый кубок Самары по плаванию${seed.suffix}`, exact: true })).toBeVisible()
      await expect.soft(spectator.getByText('8 дорожек · бассейн 25 м', { exact: true })).toBeVisible()
      const program = spectator.locator('section').filter({ has: spectator.getByRole('heading', { name: 'Программа' }) })
      await expect.soft(program.getByRole('heading', { level: 3 })).toHaveCount(2)
      await expect.soft(program.locator('ol > li')).toHaveCount(2)
      await expect.soft(program.locator('ul > li')).toHaveCount(4)
      await screenshots(spectator, '01-public-overview')
      await login(org, 'org@flounder.demo', base)
      await loadEvent()
      await expect.soft(org.locator('svg').filter({ has: org.locator('title', { hasText: 'QR-код публичной страницы' }) })).toHaveCount(1)
      await expect.soft(org.getByRole('link', { name: 'Публичная страница ↗', exact: true })).toHaveAttribute('href', publicBase)
      await screenshots(org, '01-organizer-overview')
      await org.getByRole('link', { name: 'Программа', exact: true }).click()
      await expect(org.getByRole('heading', { name: 'Сессии соревнования' })).toBeVisible()
      await screenshots(org, '01-program')
      await org.getByRole('row').filter({ has: org.getByText('M-50-FREE', { exact: true }) }).getByRole('button', { name: 'Изменить', exact: true }).click()
      const dialog = org.getByRole('dialog')
      await expect(dialog.getByRole('button', { name: 'Применить', exact: true })).toBeVisible()
      await expect.soft(dialog.getByRole('combobox', { name: 'Пресет' })).toContainText(/Детские.*2 года/i)
      await screenshots(org, '01-discipline-settings')
      await dialog.getByRole('button', { name: /Закрыть|Отмена/ }).first().click()
    })
    // Recover only read-only metadata if an overview assertion blocked its load.
    if (!event) await loadEvent()

    await step('2. Excel download, 203/200/3 report and duplicate-free reimport', async () => {
      await org.getByRole('link', { name: 'Заявки', exact: true }).last().click()
      await org.getByRole('button', { name: 'Импорт из Excel', exact: true }).first().click()
      const dialog = org.getByRole('dialog')
      const downloadPromise = org.waitForEvent('download')
      await dialog.getByRole('button', { name: 'Скачать шаблон', exact: true }).click()
      const download = await downloadPromise
      expect.soft(download.suggestedFilename()).toMatch(/\.xlsx$/)
      expect.soft(await download.failure()).toBeNull()
      async function upload(file: string) {
        await dialog.getByLabel('Файл .xlsx').setInputFiles(resolve(`../../docs/scripts/demo/out/${file}`))
        const responsePromise = org.waitForResponse(r => r.url().endsWith('/registrations/import') && r.request().method() === 'POST')
        const start = Date.now()
        await dialog.getByRole('button', { name: 'Импортировать', exact: true }).click()
        const response = await responsePromise
        timings[file] = Date.now() - start
        expect.soft(response.status()).toBe(200)
        const report = await response.json() as ImportReport
        await expect(dialog.getByRole('heading', { name: 'Отчёт импорта' })).toBeVisible()
        return report
      }
      const bad = await upload('set-M-with-errors.xlsx')
      expect.soft(bad.total).toBe(203)
      expect.soft(bad.created + bad.updated + bad.skipped).toBe(200)
      expect.soft(bad.errors.map(e => e.row)).toEqual([202, 203, 204])
      for (const [row, message] of [[202, /ДД.ММ.ГГГГ|дата/i], [203, /неизвестный код/i], [204, /фамилия не заполнена/i]] as const) {
        expect.soft(bad.errors.find(e => e.row === row)?.message).toMatch(message)
        await expect.soft(dialog.getByRole('row').filter({ has: org.getByRole('cell', { name: String(row), exact: true }) })).toContainText(message)
      }
      await screenshots(org, '02-import-errors')
      const good = await upload('set-M.xlsx')
      expect.soft(good.total).toBe(200)
      expect.soft(good.created).toBe(0)
      expect.soft(good.errors).toEqual([])
      expect.soft(timings['set-M.xlsx']).toBeLessThanOrEqual(10_000)
      await screenshots(org, '02-import-corrected')
      await dialog.getByRole('button', { name: 'Закрыть', exact: true }).last().click()
      await expect.soft(org.getByRole('heading', { name: 'Заявки · 200', exact: true })).toBeVisible()
      expect.soft(await registrations()).toHaveLength(200)
      await screenshots(org, '02-entries')
    })

    await step('3. Parent mobile submission, organizer confirmation and secretary payment', async () => {
      await parent.setViewportSize({ width: 375, height: 812 })
      await login(parent, 'parent@flounder.demo', `${publicBase}/register`)
      await parent.getByLabel('Фамилия', { exact: true }).fill('Демов')
      await parent.getByLabel('Имя', { exact: true }).fill('Тимофей')
      await parent.getByLabel('Дата рождения').fill('2014-04-18')
      await parent.getByRole('combobox', { name: /^Пол/ }).selectOption('M')
      await parent.getByLabel('Клуб / город').fill('Дельфин')
      await parent.getByRole('button', { name: 'Далее: Виды и время', exact: true }).click()
      for (const [distance, time] of [[50, '00:47.20'], [100, '01:49.50']] as const) {
        const choice = parent.getByRole('button', { name: new RegExp(`^${distance} м.*юноши`) })
        await choice.click()
        await parent.getByLabel(new RegExp(`^Заявочное время · ${distance} м`)).fill(time)
      }
      await parent.getByRole('button', { name: 'Далее: Проверка', exact: true }).click()
      await parent.getByRole('checkbox', { name: /Я даю согласие/ }).check()
      await screenshots(parent, '03-parent-form')
      const responsePromise = parent.waitForResponse(r => r.url().endsWith('/registrations') && r.request().method() === 'POST')
      await parent.getByRole('button', { name: 'Отправить заявку' }).click()
      const response = await responsePromise
      expect.soft(response.status()).toBe(200)
      const submitted = response.request().postDataJSON()
      expect.soft(submitted.profile).toMatchObject({ lastName: 'Демов', firstName: 'Тимофей', birthDate: '2014-04-18', gender: 'M' })
      expect.soft(submitted.items).toHaveLength(2)
      expect.soft(submitted.items).toEqual(expect.arrayContaining([
        { eventDisciplineId: event.disciplines.find(d => d.code === 'M-50-FREE')!.id, seedTimeMs: 47200 },
        { eventDisciplineId: event.disciplines.find(d => d.code === 'M-100-FREE')!.id, seedTimeMs: 109500 },
      ]))
      await expect(parent.getByRole('heading', { name: 'Заявка отправлена' })).toBeVisible()
      await screenshots(parent, '03-parent-submitted')
      await org.reload()
      await expect.soft(org.getByRole('heading', { name: 'Заявки · 202' })).toBeVisible()
      const rows = (await registrations()).filter(r => r.participantSnapshot.lastName === 'Демов')
      expect.soft(rows).toHaveLength(2)
      expect.soft(rows.map(r => r.status)).toEqual(['SUBMITTED', 'SUBMITTED'])
      await org.getByLabel('Поиск', { exact: true }).fill('Демов')
      for (const registration of rows) {
        const name = event.disciplines.find(d => d.id === registration.eventDisciplineId)!.name
        const row = org.locator('tbody tr').filter({ hasText: name })
        await row.getByRole('button', { name: 'Подтвердить', exact: true }).click()
        await org.getByRole('dialog').getByRole('button', { name: 'Подтвердить' }).click()
        await expect(org.getByRole('dialog')).toHaveCount(0)
      }
      await screenshots(org, '03-parent-confirmed')
      await expect.soft(org.getByRole('button', { name: 'Отметить оплату…', exact: true }), 'QA05: zero-fee mark-paid action').toHaveCount(2)
      await login(sec, 'sec@flounder.demo', `${base}/entries`)
      await expect(sec.getByRole('heading', { name: 'Заявки · 202' }), 'QA05-02: secretary cabinet is accessible').toBeVisible()
      await sec.getByLabel('Поиск', { exact: true }).fill('Демов')
      await expect(sec.getByRole('button', { name: 'Отметить оплату…', exact: true })).toHaveCount(2)
      await screenshots(sec, '03-secretary-cabinet')
      for (const registration of rows) {
        if (await sec.getByLabel('Поиск', { exact: true }).count()) {
          await sec.getByLabel('Поиск', { exact: true }).fill('Демов')
          const row = sec.locator('tbody tr').filter({ hasText: event.disciplines.find(d => d.id === registration.eventDisciplineId)!.name })
          if (await row.getByRole('button', { name: 'Отметить оплату…', exact: true }).count()) {
            await row.getByRole('button', { name: 'Отметить оплату…', exact: true }).click()
            await sec.getByRole('dialog').getByRole('button', { name: 'Подтвердить', exact: true }).click()
            await expect(sec.getByRole('dialog')).toHaveCount(0)
            await expect(row.getByTitle('Статус оплаты')).toHaveText('Оплачено')
            await settle(sec)
            continue
          }
        }
        // Explicit diagnostic continuation; the failed UI assertions remain red.
        await test.step('Continue payment via real secretary API after blocked UI', () => api(sec, `/registrations/${registration.id}/mark-paid`, 'POST'))
      }
      expect((await registrations()).filter(r => r.participantSnapshot.lastName === 'Демов').every(r => r.paymentStatus === 'PAID')).toBe(true)
      await org.reload()
      await org.getByLabel('Поиск', { exact: true }).fill('Демов')
      await screenshots(org, '03-paid-through-secretary-ui')
    })

    await step('4. Seeding, validator, schedule, draft PDF and anonymous publication', async () => {
      await org.getByRole('link', { name: 'Заплывы', exact: true }).click()
      // circleSeedTopHeats is a generation option, not a discipline form field.
      const circle = org.getByRole('checkbox', { name: /circleSeedTopHeats|круговой посев/i })
      await expect.soft(circle, 'D03: the future circle seeding option belongs in the program editor').toHaveCount(0)
      if (await circle.count()) await expect.soft(circle).not.toBeChecked()
      const responsePromise = org.waitForResponse(r => r.url().endsWith('/generate-competition-units') && r.request().method() === 'POST')
      const start = Date.now()
      await org.getByRole('button', { name: 'Сформировать заплывы', exact: true }).click()
      const response = await responsePromise
      const generation = await response.json() as EventGenerationResult
      const results = generation.disciplines.map(d => 'result' in d ? d.result : d)
      timings.seeding = Date.now() - start
      expect.soft(response.request().postDataJSON().circleSeedTopHeats).toBe(false)
      expect.soft(timings.seeding).toBeLessThanOrEqual(5_000)
      expect.soft(results.flatMap(d => d.violations).filter(v => v.severity === 'ERROR')).toEqual([])
      expect.soft(results.flatMap(d => d.heats).flatMap(h => h.lanes)).toHaveLength(202)
      for (const result of results) for (const heat of result.heats) {
        const sorted = [...heat.lanes].sort((a, b) => (a.seedTimeMs ?? Infinity) - (b.seedTimeMs ?? Infinity) || a.athlete.lastName.localeCompare(b.athlete.lastName, 'ru') || a.athlete.firstName.localeCompare(b.athlete.firstName, 'ru') || a.registrationId.localeCompare(b.registrationId))
        expect.soft(sorted.map(lane => lane.lane), `lane order heat ${heat.id}`).toEqual([4, 5, 3, 6, 2, 7, 1, 8].slice(0, sorted.length))
        expect.soft(heat.scheduledAt).toBeTruthy()
      }
      await settle(org)
      await org.getByRole('button', { name: 'Проверить заплывы', exact: true }).click()
      await settle(org)
      for (const d of event.disciplines) {
        const validation = await api<{ violations: Violation[] }>(org, `/event-disciplines/${d.id}/competition-units/validate`)
        expect.soft(validation.violations.filter(v => v.severity === 'ERROR')).toEqual([])
      }
      await loadHeats()
      expect.soft(heats.some(h => h.entries.length === 8)).toBe(true)
      expect.soft(heats.some(h => h.entries.length < 8)).toBe(true)
      const sessions = await api<Session[]>(org, `/events/${seed.eventId}/sessions`)
      for (const [index, session] of [...sessions].sort((a, b) => a.orderNo - b.orderNo).entries()) {
        expect.soft(session.estimatedEndsAt).toBeTruthy()
        const first = heats.filter(h => event.disciplines.find(d => d.id === h.eventDisciplineId)?.sessionId === session.id).sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))[0]
        const clock = new Intl.DateTimeFormat('ru-RU', { timeZone: 'Europe/Samara', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(first.scheduledAt!))
        expect.soft(clock).toBe(index === 0 ? '10:00' : '15:00')
      }
      await screenshots(org, '04-heats-validator')
      // Draft protocols use the authenticated download; public HTML excludes unpublished heats.
      await org.getByText('Печать ▾', { exact: true }).click()
      await expect(org.getByText('Публичная печатная форма появится после публикации. Черновик доступен в PDF.')).toBeVisible()
      const [draftDownload] = await Promise.all([org.waitForEvent('download'), org.getByRole('button', { name: 'Скачать PDF', exact: true }).click()])
      const draftPath = resolve(evidence, 'QA05-draft-start-list.pdf')
      await draftDownload.saveAs(draftPath)
      const draftBytes = readFileSync(draftPath)
      expect(draftBytes.subarray(0, 5).toString()).toBe('%PDF-')
      expect(draftBytes.length).toBeGreaterThan(1_000)
      const printPage = await contexts[0].newPage()
      await printPage.goto(`${publicBase}/start-list?print=1`)
      await expect(printPage.getByRole('heading', { name: 'Стартовый протокол', exact: true })).toBeVisible()
      await expect(printPage.locator('article').filter({ has: printPage.locator('table') }), 'Unpublished heats are not exposed publicly').toHaveCount(0)
      await screenshots(printPage, '04-unpublished-public-print')
      await org.getByRole('button', { name: 'Опубликовать протокол', exact: true }).click()
      await org.getByRole('dialog').getByRole('button', { name: 'Опубликовать', exact: true }).click()
      await settle(org)
      // T06 intentionally caches reads for 10 seconds; wait for the new snapshot.
      await expect.poll(async () => {
        const list = await api<StartList>(spectator, `/public/events/${seed.slug}/start-list`)
        return list.sessions.flatMap(s => s.disciplines).flatMap(d => d.heats).length
      }, { timeout: 15_000 }).toBe(heats.length)
      await spectator.goto(`${publicBase}/start-list`)
      await expect(spectator.locator('article').filter({ has: spectator.locator('table') }).first()).toBeVisible()
      await expect.soft(spectator.locator('article').filter({ has: spectator.locator('table') })).toHaveCount(heats.length)
      await screenshots(spectator, '04-public-start-list')
      await pdf(spectator, 'start-list')
      const printHref = await org.getByRole('link', { name: 'Печатная форма' }).getAttribute('href')
      await printPage.goto(printHref!)
      await expect(printPage.getByRole('heading', { name: 'Стартовый протокол', exact: true })).toBeVisible()
      await expect(printPage.locator('article').filter({ has: printPage.locator('table') })).toHaveCount(heats.length)
      await printPage.close()
    })

    await step('5. Secretary live, three next heats, Demov search, polling and PWA', async () => {
      await loadHeats()
      const ordered = [...heats].sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))
      await api(sec, `/events/${seed.eventId}/live`, 'PUT', { currentUnitId: ordered[0].id })
      await spectator.goto(`${publicBase}/live`)
      await expect(spectator.locator('html')).toHaveAttribute('data-theme', 'dark')
      const current = spectator.locator('section').filter({ has: spectator.getByRole('heading', { name: /^Сейчас заплыв/ }) })
      // 15s polling plus the existing 10s server cache TTL and response time.
      await expect(current).toBeVisible({ timeout: 26000 })
      await expect.soft(current).toContainText(ordered[0].label)
      const next = spectator.locator('section').filter({ has: spectator.getByRole('heading', { name: 'Далее', exact: true }) })
      await expect.soft(next.getByRole('listitem')).toHaveCount(3)
      await spectator.getByLabel('Фамилия', { exact: true }).fill('Демов')
      const athlete = spectator.locator('article').filter({ has: spectator.getByRole('heading', { name: 'Демов Тимофей' }) })
      await expect(athlete).toBeVisible()
      await expect.soft(athlete.getByRole('listitem')).toHaveCount(2)
      await screenshots(spectator, '05-live-search')
      const start = Date.now()
      await api(sec, `/events/${seed.eventId}/live`, 'PUT', { currentUnitId: ordered[1].id })
      await expect.configure({ soft: true }).poll(async () => {
        const link = await current.getByRole('link').getAttribute('href')
        return link?.endsWith(`/heats/${ordered[1].id}`)
      }, { timeout: 26_000, intervals: [250, 500, 1000] }).toBe(true)
      timings.liveRefresh = Date.now() - start
      expect.soft(timings.liveRefresh).toBeLessThanOrEqual(26_000)
      const live = await api<Live>(spectator, `/public/events/${seed.slug}/live`)
      expect.soft(live.currentUnit?.id).toBe(ordered[1].id)
      const manifestHref = await spectator.locator('link[rel="manifest"]').getAttribute('href')
      expect.soft(manifestHref).toBeTruthy()
      const manifest = await spectator.request.get(manifestHref!)
      expect.soft(manifest.ok()).toBe(true)
      expect.soft((await manifest.json()).name).toBe('SportGate')
      const worker = await spectator.request.get('/sw.js')
      expect.soft(worker.ok()).toBe(true)
      expect.soft(worker.headers()['content-type']).toMatch(/javascript/)
      expect.soft(await spectator.evaluate(async () => !!(await navigator.serviceWorker.getRegistration())?.active)).toBe(true)
    })

    await step('6. Published withdrawal preserves neighbors, schedule and validator WARNING', async () => {
      await loadHeats()
      const discipline = event.disciplines.find(d => d.code === 'F-100-FREE')!
      const heat = heats.filter(h => h.eventDisciplineId === discipline.id).sort((a, b) => (b.sequenceNumber ?? 0) - (a.sequenceNumber ?? 0))[0]
      const target = heat.entries[0]
      const before = await api<StartList>(spectator, `/public/events/${seed.slug}/start-list`)
      const beforeHeat = before.sessions.flatMap(s => s.disciplines).flatMap(d => d.heats).find(h => h.id === heat.id)!
      const neighbors = beforeHeat.lanes.filter(l => !('registrationId' in l) || l.registrationId !== target.registrationId)
      await org.getByRole('link', { name: 'Заявки', exact: true }).last().click()
      const registration = (await registrations()).find(r => r.id === target.registrationId)!
      await org.getByLabel('Поиск', { exact: true }).fill(registration.participantSnapshot.lastName)
      await org.getByRole('combobox', { name: /^Вид программы/ }).selectOption(discipline.id)
      const row = org.getByRole('row').filter({ hasText: registration.participantSnapshot.firstName })
      await row.getByRole('button', { name: 'Действия', exact: true }).click()
      const withdraw = org.getByRole('menu', { name: 'Действия', exact: true }).getByRole('menuitem', { name: 'Снять', exact: true })
      await withdraw.click()
      await org.getByRole('dialog').getByRole('button', { name: 'Снять', exact: true }).click()
      await expect(org.getByRole('dialog')).toHaveCount(0)
      expect.soft((await registrations()).find(r => r.id === target.registrationId)?.status).toBe('WITHDRAWN')
      await screenshots(org, '06-withdrawn-entry')
      await org.getByRole('navigation', { name: 'Разделы соревнования' }).getByRole('link', { name: 'Заплывы', exact: true }).click()
      await org.getByText('Расписание ▾', { exact: true }).click()
      await org.getByRole('menuitem', { name: 'Пересчитать время стартов', exact: true }).click()
      await settle(org)
      await expect.poll(async () => {
        const list = await api<StartList>(spectator, `/public/events/${seed.slug}/start-list`)
        const unit = list.sessions.flatMap(s => s.disciplines).flatMap(d => d.heats).find(h => h.id === heat.id)
        const lane = unit?.lanes.find(l => l.lane === target.laneOrPosition)
        return Boolean(lane?.empty || lane?.withdrawn)
      }, { timeout: 15_000 }).toBe(true)
      const after = await api<StartList>(spectator, `/public/events/${seed.slug}/start-list`)
      const afterHeat = after.sessions.flatMap(s => s.disciplines).flatMap(d => d.heats).find(h => h.id === heat.id)!
      const lane = afterHeat.lanes.find(l => l.lane === target.laneOrPosition)
      expect.soft(lane?.empty || lane?.withdrawn, 'withdrawn lane is vacant or marked').toBe(true)
      expect.soft(afterHeat.lanes.filter(l => l.lane !== target.laneOrPosition)).toEqual(neighbors)
      expect.soft(afterHeat.scheduledAt).toBeTruthy()
      const validation = await api<{ violations: Violation[] }>(org, `/event-disciplines/${discipline.id}/competition-units/validate`)
      expect.soft(validation.violations.filter(v => v.severity === 'ERROR')).toEqual([])
      expect.soft(validation.violations.some(v => v.severity === 'WARNING' && /PUBLISHED|CHANGED|WITHDRAW/i.test(v.code)), 'QA05-08: published change warning').toBe(true)
      await org.getByRole('button', { name: 'Проверить заплывы', exact: true }).click()
      await settle(org)
      await screenshots(org, '06-validator')
      await spectator.goto(`${publicBase}/start-list`)
      await screenshots(spectator, '06-public-start-list')
    })

    await step('7. Times/DNS/DSQ, shared places 1,1,3, publication, correction and final PDF', async () => {
      await loadHeats()
      const discipline = event.disciplines.find(d => d.code === 'M-50-FREE')!
      const selected = heats.filter(h => h.eventDisciplineId === discipline.id).sort((a, b) => a.sequenceNumber! - b.sequenceNumber!).slice(0, 2)
      const heat = selected[0]
      const entries = [...heat.entries].sort((a, b) => a.laneOrPosition! - b.laneOrPosition!)
      const snapshot = (await registrations()).find(r => r.id === entries[2].registrationId)!.participantSnapshot
      const publicName = `${snapshot.lastName} ${snapshot.firstName}`
      const values = [ ['00:46.20', '00:46.20', '00:48.00', 'DNS'], ['00:49.10', '00:49.50', 'DNF', 'DSQ', '00:50.10', '00:50.40', '00:50.70', '00:51.00'] ]
      for (const [index, unit] of selected.entries()) {
        await sec.goto(`/operator/competition-units/${unit.id}?eventId=${seed.eventId}`)
        await expect(sec.getByRole('textbox', { name: /^Время, дорожка/ }).first()).toBeVisible()
        // A real started heat allows ordinary result entry after start-list publication.
        await sec.getByRole('button', { name: 'Старт', exact: true }).click()
        await expect(sec.getByRole('status').filter({ hasText: 'Заплыв начат' })).toBeVisible()
        const rows = sec.locator('tbody tr')
        for (let i = 0; i < unit.entries.length; i++) {
          const value = values[index][i] ?? `00:${(51.10 + (i - values[index].length) * 0.30).toFixed(2)}`
          if (['DNS', 'DNF', 'DSQ'].includes(value)) await rows.nth(i).getByRole('button', { name: value, exact: true }).click()
          else await rows.nth(i).getByRole('textbox').fill(value)
        }
        await screenshots(sec, `07-results-input-${index + 1}`)
        const responsePromise = sec.waitForResponse(r => r.url().endsWith(`/${unit.id}/results`) && r.request().method() === 'POST')
        await sec.getByRole('button', { name: 'Сохранить и пересчитать', exact: true }).click()
        expect.soft((await responsePromise).status()).toBe(200)
        await sec.getByRole('button', { name: 'Опубликовать результаты', exact: true }).click()
        await sec.getByRole('dialog').getByRole('button', { name: 'Опубликовать', exact: true }).click()
        await expect.soft(sec.getByRole('status').filter({ hasText: 'Результаты опубликованы' })).toBeVisible()
        await expect.soft(sec.getByRole('alert')).toHaveCount(0)
        await screenshots(sec, `07-results-published-${index + 1}`)
      }
      const saved = await api<CompetitionUnitDetailsResponse>(sec, `/competition-units/${heat.id}`)
      const places = saved.entries.filter(e => entries.slice(0, 3).some(row => row.entryId === e.entryId)).sort((a, b) => a.laneOrPosition! - b.laneOrPosition!)
      expect.soft(places.map(e => e.finalPlace)).toEqual([1, 1, 3])
      expect.soft(places.map(e => e.placeInAgeGroup)).toEqual([1, 1, 3])
      const special = (await Promise.all(selected.map(unit => api<CompetitionUnitDetailsResponse>(sec, `/competition-units/${unit.id}`)))).flatMap(unit => unit.entries).filter(e => ['DNS', 'DNF', 'DSQ', 'DID_NOT_START', 'DID_NOT_FINISH', 'DISQUALIFIED'].includes(e.resultStatus!))
      expect.soft(special).toHaveLength(3)
      expect.soft(special.every(e => e.finalPlace == null && e.placeInAgeGroup == null)).toBe(true)
      await spectator.goto(`${publicBase}/results`)
      await expect(spectator.locator('html')).toHaveAttribute('data-theme', 'dark')
      await expect(spectator.getByRole('heading', { name: 'Результаты', exact: true })).toBeVisible()
      // QA05: published results must appear before the later demo finish action.
      await expect.configure({ soft: true }).poll(async () => {
        const results = await api<Results>(spectator, `/public/events/${seed.slug}/results`)
        return results.disciplines.find(d => d.id === discipline.id)?.overall.length ?? 0
      }, { timeout: 15_000, message: 'QA05-07: published results are public' }).toBe(selected.reduce((sum, unit) => sum + unit.entries.length, 0))
      await screenshots(spectator, '07-public-results')
      await sec.goto(`/operator/competition-units/${heat.id}?eventId=${seed.eventId}`)
      await sec.getByRole('textbox', { name: `Время, дорожка ${entries[2].laneOrPosition}`, exact: true }).fill('00:48.10')
      const correctionPromise = sec.waitForResponse(r => r.url().endsWith(`/${heat.id}/results`) && r.request().method() === 'POST')
      await sec.getByRole('button', { name: 'Сохранить исправление', exact: true }).click()
      const corrected = await (await correctionPromise).json() as CompetitionUnitDetailsResponse
      expect.soft(corrected.entries.find(e => e.entryId === entries[2].entryId)?.resultStatus).toBe('CORRECTED')
      expect.soft(corrected.entries.filter(e => entries.slice(0, 3).some(row => row.entryId === e.entryId)).sort((a, b) => a.laneOrPosition! - b.laneOrPosition!).map(e => e.finalPlace)).toEqual([1, 1, 3])
      await expect.soft(sec.locator('tbody tr').nth(2)).toContainText('исправлено')
      await expect.configure({ soft: true }).poll(async () => {
        const row = spectator.getByRole('row').filter({ hasText: publicName }).first()
        return await row.count() ? row.innerText() : ''
      }, { timeout: 25_000, message: 'QA05-07: public corrected result is updated' }).toContain('00:48.10')
      await screenshots(sec, '07-secretary-correction')
      await screenshots(spectator, '07-public-corrected')
      // Finish after publication closes the heat and advances live.
      for (const unit of selected) {
        await sec.goto(`/operator/competition-units/${unit.id}?eventId=${seed.eventId}`)
        const finishPromise = sec.waitForResponse(r => r.url().endsWith(`/${unit.id}/finish`) && r.request().method() === 'POST')
        await sec.getByRole('button', { name: 'Финиш', exact: true }).click()
        const response = await finishPromise
        expect.soft(response.status(), 'QA05-09: finish after publication').toBe(200)
        await expect(sec.getByRole('status').filter({ hasText: 'Заплыв завершён' })).toBeVisible()
      }
      await expect.poll(async () => {
        const results = await api<Results>(spectator, `/public/events/${seed.slug}/results`)
        return results.disciplines.find(d => d.id === discipline.id)?.overall.find(r => r.timeMs === 48100)?.place
      }, { timeout: 15_000 }).toBe(3)
      await spectator.reload()
      await expect(spectator.getByRole('row').filter({ hasText: publicName }).first()).toContainText('00:48.10')
      await screenshots(spectator, '07-public-finished-continuation')
      await pdf(spectator, 'results')
    })
  } finally {
    await writeFile('work/qa05/audit.json', JSON.stringify({ seed, steps, timings, audit }, null, 2))
    await info.attach('QA05 audit', { path: 'work/qa05/audit.json', contentType: 'application/json' })
    for (const entry of audit) expect.soft(entry, 'browser console and HTTP audit').toBeUndefined()
    await Promise.all(contexts.map(context => context.close()))
  }
})
