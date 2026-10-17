import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { api, login } from './qa05-support'
import type { PublicEvent, Results, StartList, RegistrationForm, Live } from '../src/features/public/public.types'
import type { CompetitionUnitDetailsResponse } from '../src/api/results.api'

test('S02: three event lifecycles, shared profiles, published medals and waitlist documents', async ({ page, request }) => {
  const events = JSON.parse(readFileSync('work/qa05/s02-seed.json', 'utf8')) as Record<string, { eventId: string; slug: string }>
  const apiBase = `${process.env.QA05_API_URL ?? 'http://localhost:8080'}/api/v1`
  const demoDate = process.env.DEMO_DATE ?? '2026-10-19'
  const date = (offset: number) => new Date(Date.parse(`${demoDate}T12:00:00Z`) + offset * 86400_000).toISOString().slice(0, 10)
  const specifications = [
    ['A', 'Кубок SportGate — осень', 'COMPLETED', -8, 160],
    ['B', 'Открытый кубок Самары по плаванию', 'REGISTRATION_CLOSED', 1, 200],
    ['C', 'Первенство области среди юношей', 'REGISTRATION_OPEN', 5, 124],
  ] as const
  const profiles: Record<string, Set<string>> = {}
  await login(page, 'org@flounder.demo', '/organizer/events')
  for (const [key, title, status, offset, count] of specifications) {
    const event = events[key]
    const response = await request.get(`${apiBase}/public/events/${event.slug}`)
    expect(response.ok()).toBe(true)
    const details = await response.json() as PublicEvent
    expect(details.title).toBe(title)
    expect(details.status).toBe(status)
    const local = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Samara', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(details.eventStartAt))
    expect(local).toBe(date(offset))
    expect(details.venue?.timezone).toBe('Europe/Samara')
    expect(new Date(details.sessions[0].startsAt).getUTCHours()).toBe(6)
    const rows = await api<{ id: string; participantProfileId: string; status: string; eventDisciplineId: string; documents: Record<string, string> }[]>(page, `/events/${event.eventId}/registrations`)
    expect(rows).toHaveLength(count)
    profiles[key] = new Set(rows.map(row => row.participantProfileId))
    await page.goto(`/e/${event.slug}`)
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
    if (key === 'C') {
      expect(details.maxParticipants).toBe(150)
      const form = await (await request.get(`${apiBase}/public/events/${event.slug}/registration-form`)).json() as RegistrationForm
      expect(form.registrationOpen).toBe(true)
      expect(form.disciplines).toHaveLength(8)
      expect(form.disciplines.every(discipline => discipline.maxParticipants === 16)).toBe(true)
      expect(rows.filter(row => row.status === 'WAITLISTED')).toHaveLength(4)
      expect(rows.filter(row => row.status === 'NEEDS_CORRECTION')).toHaveLength(6)
      expect(rows.filter(row => row.documents.MEDICAL_CERT === 'PENDING')).toHaveLength(2)
      await page.goto(`/organizer/events/${event.eventId}/entries`)
      await expect(page.getByRole('heading', { name: /Заявки участников · 124/ })).toBeVisible()
      await page.getByRole('combobox', { name: 'Статус', exact: true }).selectOption('WAITLISTED')
      await expect(page.locator('tbody tr')).toHaveCount(4)
      await expect(page.locator('tbody tr').first()).toContainText('Лист ожидания · №')
    } else {
      const start = await (await request.get(`${apiBase}/public/events/${event.slug}/start-list`)).json() as StartList
      const heats = start.sessions.flatMap(session => session.disciplines).flatMap(discipline => discipline.heats)
      expect(heats.flatMap(heat => heat.lanes).filter(lane => !lane.empty)).toHaveLength(count)
      const units = await Promise.all(heats.map(heat => api<CompetitionUnitDetailsResponse>(page, `/competition-units/${heat.id}`)))
      const finished = units.filter(unit => unit.status === 'FINISHED')
      expect(finished).toHaveLength(key === 'A' ? units.length : 2)
      if (key === 'A') {
        expect(units.flatMap(unit => unit.entries).filter(entry => entry.resultStatus === 'CORRECTED')).toHaveLength(2)
        expect(units.flatMap(unit => unit.entries).filter(entry => ['DNS', 'DNF'].includes(entry.resultStatus ?? ''))).toHaveLength(3)
        const results = await (await request.get(`${apiBase}/public/events/${event.slug}/results`)).json() as Results
        expect(results.disciplines.flatMap(discipline => discipline.overall)).toHaveLength(160)
        expect(results.disciplines.every(discipline => [1, 2, 3].every(place => discipline.overall.some(row => row.place === place)))).toBe(true)
        await page.goto(`/e/${event.slug}/results`)
        await expect(page.getByRole('heading', { name: 'Результаты', exact: true })).toBeVisible()
        await expect(page.locator('tbody tr').first()).toBeVisible()
      } else {
        const firstSession = start.sessions[0].disciplines.flatMap(discipline => discipline.heats).sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))
        expect(new Set(finished.map(unit => unit.id))).toEqual(new Set(firstSession.slice(0, 2).map(unit => unit.id)))
        expect(units.filter(unit => !finished.includes(unit)).every(unit => unit.status === 'PUBLISHED')).toBe(true)
        const live = await (await request.get(`${apiBase}/public/events/${event.slug}/live`)).json() as Live
        expect(live.currentUnit).not.toBeNull()
        expect(live.delaySec).toBeDefined()
        await page.goto(`/e/${event.slug}/live`)
        await expect(page.getByRole('heading', { name: /^Сейчас заплыв/ })).toBeVisible()
      }
    }
  }
  expect(new Set([...profiles.A, ...profiles.B, ...profiles.C]).size).toBe(300)
  expect([...profiles.B].filter(id => profiles.C.has(id))).toHaveLength(40)
})
