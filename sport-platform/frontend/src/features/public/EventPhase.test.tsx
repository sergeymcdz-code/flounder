import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { demoEvent, demoResults } from '../../mocks/demo-data'
import { server } from '../../test/setup'
import { CalendarCards } from './CalendarCards'
import { PublicEventPage } from './EventPage'
import { endedByDate, isEventOver, publicEventStatus, setEventPhaseClock } from './event-phase'
import { LivePage } from './LivePage'
import { ResultsPage } from './ResultsPage'
import type { CalendarCard } from './calendar.types'
import type { PublicEvent } from './public.types'
import { LIVE_POLL_MS, RESULTS_POLL_MS, SCHEDULE_POLL_MS } from './usePublicData'

// 25.10.2026: the demo event (15.10) is over by date, but its status still says REGISTRATION_CLOSED.
const today = Date.parse('2026-10-25T12:00:00+03:00')
const pastEvent: PublicEvent = { ...demoEvent, status: 'REGISTRATION_CLOSED', registrationOpen: false }

beforeEach(() => setEventPhaseClock(() => today))
afterEach(() => vi.useRealTimers())

function renderPage(page: React.ReactNode, event = pastEvent) {
  function Context() { return <Outlet context={{ event }} /> }
  return render(<MemoryRouter><Routes><Route element={<Context />}><Route index element={page} /></Route></Routes></MemoryRouter>)
}

describe('event phase guard', () => {
  it.each([
    ['REGISTRATION_CLOSED', '2026-10-24T18:00:00+03:00', true],
    ['LIVE', '2026-10-24T18:00:00+03:00', false],
    ['REGISTRATION_OPEN', '2026-10-24', true],
    ['REGISTRATION_OPEN', '2026-10-25', false],
    ['REGISTRATION_CLOSED', '2026-10-26T09:00:00+03:00', false],
    ['COMPLETED', '2026-10-24T18:00:00+03:00', false],
    ['ARCHIVED', '2026-10-24T18:00:00+03:00', false],
    ['CANCELLED', '2026-10-24T18:00:00+03:00', false],
    ['PUBLISHED', null, false],
    ['PUBLISHED', 'not-a-date', false],
  ] as const)('%s ending %s is ended by date: %s', (status, endAt, expected) => {
    expect(endedByDate(status, endAt)).toBe(expected)
  })

  it('maps statuses for public badges and polling', () => {
    expect(publicEventStatus('REGISTRATION_CLOSED', '2026-10-24T18:00:00+03:00')).toBe('COMPLETED')
    expect(publicEventStatus('FINISHED', '2026-12-01T18:00:00+03:00')).toBe('COMPLETED')
    expect(publicEventStatus('CANCELLED', '2026-10-24T18:00:00+03:00')).toBe('CANCELLED')
    expect(publicEventStatus('LIVE', '2026-10-25T18:00:00+03:00')).toBe('LIVE')
    expect(publicEventStatus('LIVE', pastEvent.eventEndAt)).toBe('LIVE')
    expect(isEventOver('LIVE', pastEvent.eventEndAt)).toBe(false)
    expect(isEventOver('ARCHIVED', '2026-12-01T18:00:00+03:00')).toBe(true)
    expect(isEventOver('LIVE', '2026-10-25T18:00:00+03:00')).toBe(false)
  })
})

it('shows «Завершено» and the results action on the event card instead of «Регистрация закрыта»', () => {
  renderPage(<PublicEventPage />)
  expect(screen.getByText('Завершено')).toBeInTheDocument()
  expect(screen.queryByText(/^Регистрация закрыта/)).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Результаты' })).toHaveAttribute('href', '/e/demo/results')
  expect(screen.queryByText(/Сейчас в бассейне/)).not.toBeInTheDocument()
})

it('shows «Завершено» on calendar cards of past events and hides the live board link', () => {
  const card: CalendarCard = { slug: 'cup', title: 'Кубок', startDate: '2026-10-24', endDate: '2026-10-24', city: 'Казань', venueName: 'Акватика', organization: null, status: 'REGISTRATION_CLOSED', registrationOpen: false, registrationCloseAt: null, disciplinesCount: 2, participantsCount: 3, hasResults: true }
  render(<MemoryRouter><CalendarCards items={[card, { ...card, slug: 'next', title: 'Следующий', startDate: '2026-10-31', endDate: '2026-10-31', status: 'REGISTRATION_OPEN' }]} /></MemoryRouter>)
  expect(screen.getByText('Завершено')).toBeInTheDocument()
  expect(screen.getByText('Регистрация открыта')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Табло' })).not.toBeInTheDocument()
})

it('replaces the stale live board of a finished event and stops polling', async () => {
  let calls = 0
  server.use(http.get('*/api/v1/public/events/demo/live', () => {
    calls++
    return HttpResponse.json({ updatedAt: '2026-10-15T10:03:00+03:00', delaySec: 0, next: [], currentUnit: { id: 'heat', number: 3, status: 'IN_PROGRESS', scheduledAt: '2026-10-15T10:03:00+03:00', actualStartedAt: null, lanes: [] } })
  }))
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderPage(<LivePage />)
  expect(await screen.findByRole('heading', { name: 'Соревнование завершено' })).toBeInTheDocument()
  expect(screen.getByText('Завершено')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть результаты' })).toHaveAttribute('href', '/e/demo/results')
  expect(screen.queryByText(/Текущий заплыв|Сейчас|каждые 15 с/)).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Найти себя' })).toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(LIVE_POLL_MS * 3) })
  expect(calls).toBe(1)
})

it('shows final results of a finished event without the preliminary badge and polling', async () => {
  let calls = 0
  server.use(http.get('*/api/v1/public/events/demo/results', () => { calls++; return HttpResponse.json(demoResults) }))
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderPage(<ResultsPage />)
  expect(await screen.findByText(/^Обновлено/)).toBeInTheDocument()
  expect(screen.getByText('Завершено')).toBeInTheDocument()
  expect(screen.queryByText('Предварительно')).not.toBeInTheDocument()
  expect(screen.queryByText(/каждые 5 с/)).not.toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(RESULTS_POLL_MS * 3) })
  expect(calls).toBe(1)
})

it('keeps results preliminary and polling for LIVE after its planned end', async () => {
  let calls = 0
  server.use(http.get('*/api/v1/public/events/demo/results', () => { calls++; return HttpResponse.json(demoResults) }))
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderPage(<ResultsPage />, { ...pastEvent, status: 'LIVE' })
  expect(await screen.findByText('Предварительно')).toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(RESULTS_POLL_MS) })
  await vi.waitFor(() => expect(calls).toBe(2))
})

it('keeps the live board and live/schedule polling for LIVE after its planned end', async () => {
  let liveCalls = 0
  let scheduleCalls = 0
  server.use(
    http.get('*/api/v1/public/events/demo/live', () => {
      liveCalls++
      return HttpResponse.json({ updatedAt: '2026-10-15T10:03:00+03:00', delaySec: 0, next: [], currentUnit: { id: 'heat', number: 3, status: 'IN_PROGRESS', scheduledAt: '2026-10-15T10:03:00+03:00', actualStartedAt: null, lanes: [] } })
    }),
    http.get('*/api/v1/public/events/demo/schedule', () => { scheduleCalls++; return HttpResponse.json([]) }),
  )
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderPage(<LivePage />, { ...pastEvent, status: 'LIVE' })
  expect(await screen.findByText('● Текущий заплыв')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Соревнование завершено' })).not.toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(SCHEDULE_POLL_MS) })
  await vi.waitFor(() => {
    expect(liveCalls).toBe(3)
    expect(scheduleCalls).toBe(2)
  })
})
