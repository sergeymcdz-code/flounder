import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import type { CompetitionUnitDetailsResponse, CompetitionUnitResponse } from '../src/api/results.api'
import type { Live, Results } from '../src/features/public/public.types'
import { api, apiUrl, login, request, token, submitDialog } from './qa07-support'

test('M10: judge publishes two heats, operator corrects, finish is idempotent and advances live', async ({ page, browser }, info) => {
  const seed = JSON.parse(readFileSync('work/qa07/seed.json', 'utf8')) as { eventId: string; slug: string }
  await login(page, 'org@flounder.demo', `/organizer/events/${seed.eventId}/heats`)
  await page.getByText('Сформировать заплывы ▾', { exact: true }).click()
  await page.getByRole('button', { name: 'Всё соревнование', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Выполняем действие' })).toHaveCount(0)
  const disciplines = await api<{ id: string; code: string }[]>(page, `/events/${seed.eventId}/disciplines`)
  const discipline = disciplines.find(item => item.code === 'M-50-FREE')!
  const registrations = await api<{ id: string; participantSnapshot: { lastName: string; firstName: string } }[]>(page, `/registrations?eventId=${seed.eventId}`)
  const units = (await api<CompetitionUnitResponse[]>(page, `/event-disciplines/${discipline.id}/competition-units`))
    .sort((a, b) => a.sequenceNumber! - b.sequenceNumber!)
  // The full M seed produces 32 heats across four disciplines; this one must
  // contain at least three so finishing the second still has a live successor.
  expect(units.length).toBeGreaterThan(2)
  await page.getByRole('button', { name: 'Опубликовать протокол', exact: true }).click()
  await submitDialog(page, 'Опубликовать')
  await expect(page.getByRole('status').filter({ hasText: 'Выполняем действие' })).toHaveCount(0)
  await api(page, `/events/${seed.eventId}/live`, 'PUT', { currentUnitId: units[0].id })

  const contexts = await Promise.all([browser.newContext(info.project.use), browser.newContext(info.project.use)])
  const [operator, spectator] = await Promise.all(contexts.map(context => context.newPage()))
  const timings: { heat: number; publicationMs: number; correctionMs: number }[] = []
  try {
    await login(page, 'judge@flounder.demo', `/operator/competition-units/${units[0].id}?eventId=${seed.eventId}`)
    await login(operator, 'sec@flounder.demo', `/operator/competition-units/${units[0].id}?eventId=${seed.eventId}`)
    // Prime the anonymous results cache before publication to test invalidation.
    await spectator.goto(`/e/${seed.slug}/results`)
    expect((await publicResults()).disciplines.flatMap(item => item.overall)).toHaveLength(0)
    const expectedPublicEntries: string[] = []
    for (const [index, unit] of units.slice(0, 2).entries()) {
      await test.step(`Heat ${index + 1}: start, save, publish, deny judge correction, operator correction, finish twice`, async () => {
        await page.goto(`/operator/competition-units/${unit.id}?eventId=${seed.eventId}`)
        await page.getByRole('button', { name: 'Старт', exact: true }).click()
        await expect(page.getByRole('status').filter({ hasText: 'Заплыв начат' })).toBeVisible()
        if (index === 1) {
          const retry = await api<{ currentUnitId: string }>(page, `/competition-units/${units[0].id}/finish`, 'POST')
          expect(retry.currentUnitId).toBe(unit.id)
        }
        const detail = await api<CompetitionUnitDetailsResponse>(page, `/competition-units/${unit.id}`)
        const sorted = [...detail.entries].sort((a, b) => a.laneOrPosition! - b.laneOrPosition!)
        const athlete = registrations.find(item => item.id === sorted[0].registrationId)!.participantSnapshot
        const publicName = `${athlete.lastName} ${athlete.firstName}`
        const rows = page.locator('tbody tr')
        for (let i = 0; i < sorted.length; i++) {
          if (i === 3) await rows.nth(i).getByRole('button', { name: 'DNS', exact: true }).click()
          else await rows.nth(i).getByRole('textbox').fill(i < 2 ? '00:46.20' : `00:${48 + i}.00`)
        }
        await page.getByRole('button', { name: 'Сохранить и пересчитать', exact: true }).click()
        await expect(page.getByRole('status').filter({ hasText: /Сохранено/ })).toBeVisible()
        const started = Date.now()
        await page.getByRole('button', { name: 'Опубликовать результаты', exact: true }).click()
        await submitDialog(page, 'Опубликовать')
        await expect(page.getByRole('status').filter({ hasText: 'Результаты опубликованы' })).toBeVisible()
        await expect(page.getByRole('alert')).toHaveCount(0)
        const published = await api<CompetitionUnitDetailsResponse>(page, `/competition-units/${unit.id}`)
        expect(published.status).toBe('FINISHED')
        expectedPublicEntries.push(...sorted.map(entry => entry.participantName))
        await expect.poll(async () => (await publicResults()).disciplines.find(item => item.id === discipline.id)?.overall.length,
          { timeout: 15_000, intervals: [100, 250, 500] }).toBe(expectedPublicEntries.length)
        // Also require the spectator DOM to update without a reload.
        await expect(spectator.getByRole('row').filter({ hasText: publicName }).first()).toContainText('00:46.20', { timeout: Math.max(1, 15_000 - (Date.now() - started)) })
        const publicationMs = Date.now() - started
        expect(publicationMs).toBeLessThanOrEqual(15_000)
        await expect(page.getByRole('button', { name: 'Сохранить исправление', exact: true })).toBeDisabled()
        const denied = await request(page.request, `/competition-units/${unit.id}/results`, 'POST',
          [{ competitionUnitEntryId: sorted[0].entryId, status: 'VALID', time: '00:46.30' }], await token(page))
        expect(denied.status()).toBe(403)
        expect((await api<CompetitionUnitDetailsResponse>(page, `/competition-units/${unit.id}`)).entries
          .find(entry => entry.entryId === sorted[0].entryId)?.rawValue).toBe(published.entries.find(entry => entry.entryId === sorted[0].entryId)?.rawValue)

        await operator.goto(`/operator/competition-units/${unit.id}?eventId=${seed.eventId}`)
        await operator.getByRole('textbox', { name: `Время, дорожка ${sorted[0].laneOrPosition}`, exact: true }).fill('00:46.30')
        const correctionStart = Date.now()
        await operator.getByRole('button', { name: 'Сохранить исправление', exact: true }).click()
        await expect(operator.locator('tbody tr').first()).toContainText('исправлено')
        await expect.poll(async () => (await publicResults()).disciplines.find(item => item.id === discipline.id)?.overall
          .find(entry => `${entry.lastName} ${entry.firstName}` === publicName)?.timeMs,
          { timeout: 15_000, intervals: [100, 250, 500] }).toBe(46300)
        await expect(spectator.getByRole('row').filter({ hasText: publicName }).first()).toContainText('00:46.30', { timeout: Math.max(1, 15_000 - (Date.now() - correctionStart)) })
        const correctionMs = Date.now() - correctionStart
        expect(correctionMs).toBeLessThanOrEqual(15_000)
        let firstLiveId: string | undefined
        for (let repeat = 0; repeat < 2; repeat++) {
          const finish = page.waitForResponse(response => response.url().endsWith(`/${unit.id}/finish`) && response.request().method() === 'POST')
          await page.getByRole('button', { name: 'Финиш', exact: true }).click()
          const response = await finish
          expect(response.status()).toBe(200)
          const state = await response.json() as { currentUnitId: string }
          expect(state.currentUnitId).toBe(units[index + 1].id)
          await expect(page.getByRole('status').filter({ hasText: 'Заплыв завершён' })).toBeVisible()
          if (firstLiveId) expect(state.currentUnitId).toBe(firstLiveId)
          firstLiveId = state.currentUnitId
          await expect.poll(async () => {
            const publicLive = await spectator.request.get(`${apiUrl}/api/v1/public/events/${seed.slug}/live`)
            expect(publicLive.status()).toBe(200)
            return ((await publicLive.json()) as Live).currentUnit?.id
          }, { timeout: 15_000 }).toBe(units[index + 1].id)
        }
        timings.push({ heat: index + 1, publicationMs, correctionMs })
      })
      if (index === 0) {
        await page.getByRole('button', { name: 'Следующий заплыв', exact: true }).click()
        await expect(page).toHaveURL(new RegExp(`/operator/competition-units/${units[1].id}`))
      }
    }
  } finally {
    await info.attach('Publication and correction latency', { body: JSON.stringify(timings, null, 2), contentType: 'application/json' })
    await Promise.all(contexts.map(context => context.close()))
  }

  async function publicResults(): Promise<Results> {
    const response = await spectator.request.get(`${apiUrl}/api/v1/public/events/${seed.slug}/results`)
    expect(response.status()).toBe(200)
    return response.json()
  }
})
