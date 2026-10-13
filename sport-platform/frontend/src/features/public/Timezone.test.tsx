import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { demoEvent, demoStartList } from '../../mocks/demo-data'
import { server } from '../../test/setup'
import { date, localTimeLabel, time } from './format'
import { PublicLayout } from './PublicLayout'
import { PublicEventPage } from './EventPage'
import { SchedulePage } from './SchedulePage'
import { StartListPage } from './StartListPage'
import { HeatPage } from './HeatPage'
import { LivePage } from './LivePage'
import { ResultsPage } from './ResultsPage'

const startsAt = '2026-10-17T06:00:00Z'

it('formats public time and date in the venue timezone', () => {
  expect(time(startsAt, 'Europe/Samara')).toBe('10:00')
  expect(time(startsAt, 'Europe/Moscow')).toBe('09:00')
  expect(date('2026-10-16T22:00:00Z', 'Europe/Samara')).toBe('17 октября 2026 г.')
  expect(time(null, 'Europe/Samara')).toBe('Время уточняется')
})

it('falls back to the browser timezone for an empty timezone', () => {
  const browserTime = new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(startsAt))
  expect(time(startsAt, '')).toBe(browserTime)
  expect(time(startsAt, '   ')).toBe(browserTime)
  expect(date(startsAt, '')).toBe(date(startsAt))
  expect(() => localTimeLabel(startsAt, '')).not.toThrow()
})

it('computes the offset on the event date, including daylight saving and fractional offsets', () => {
  expect(localTimeLabel(startsAt, 'Europe/Samara')).toBe('время местное (UTC+04)')
  expect(localTimeLabel('2026-01-17T06:00:00Z', 'Europe/Berlin')).toBe('время местное (UTC+01)')
  expect(localTimeLabel('2026-07-17T06:00:00Z', 'Europe/Berlin')).toBe('время местное (UTC+02)')
  expect(localTimeLabel(startsAt, 'Asia/Kolkata')).toBe('время местное (UTC+05:30)')
  expect(localTimeLabel(startsAt, 'UTC')).toBe('время местное (UTC+00)')
})

const heat = demoStartList.sessions[0].disciplines[0].heats[0]
const pages = [
  ['', <PublicEventPage />],
  ['schedule', <SchedulePage />],
  ['start-list', <StartListPage />],
  [`heats/${heat.id}`, <HeatPage />],
  ['live', <LivePage />],
  ['results', <ResultsPage />],
] as const

it.each(pages)('passes the venue timezone from PublicLayout to /e/demo/%s', async (path, page) => {
  server.use(
    http.get('*/public/events/demo', () => HttpResponse.json({
      ...demoEvent,
      eventStartAt: '2026-10-16T22:00:00Z',
      venue: { ...demoEvent.venue, timezone: 'Europe/Samara' },
      sessions: demoEvent.sessions.map((session) => ({ ...session, startsAt })),
    })),
    http.get('*/public/events/demo/start-list', () => HttpResponse.json({
      sessions: demoStartList.sessions.map((session) => ({
        ...session,
        disciplines: session.disciplines.map((discipline) => ({
          ...discipline,
          heats: discipline.heats.map((item) => ({ ...item, scheduledAt: startsAt })),
        })),
      })),
    })),
    http.get('*/public/events/demo/schedule', () => HttpResponse.json([{
      sessionName: 'Утро', disciplineName: '50 м', heatNumber: 1,
      sequenceInSession: 1, scheduledAt: startsAt, status: 'PUBLISHED',
    }])),
    http.get('*/public/events/demo/live', () => HttpResponse.json({
      currentUnit: { ...heat, scheduledAt: startsAt }, next: [], updatedAt: startsAt,
    })),
  )
  render(
    <MemoryRouter initialEntries={[`/e/demo/${path}`]}>
      <Routes>
        <Route path="/e/:slug" element={<PublicLayout />}>
          <Route path={path.startsWith('heats/') ? 'heats/:unitId' : path || undefined} index={!path} element={page} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
  expect(await screen.findByText('17 октября 2026 г. · Самара')).toBeInTheDocument()
  if (path !== 'results') {
    expect((await screen.findAllByText(/10:00/)).length).toBeGreaterThan(0)
  }
  if (!path || path === 'schedule') {
    expect(screen.getAllByText(/время местное \(UTC\+04\)/)).toHaveLength(1)
  }
})

it.each(['', 'schedule'])('renders /e/demo/%s when the venue timezone is empty', async (path) => {
  server.use(
    http.get('*/public/events/demo', () => HttpResponse.json({
      ...demoEvent,
      venue: { ...demoEvent.venue, timezone: '' },
      sessions: demoEvent.sessions.map((session) => ({ ...session, startsAt })),
    })),
    http.get('*/public/events/demo/schedule', () => HttpResponse.json([{
      sessionName: 'Утро', disciplineName: '50 м', heatNumber: 1,
      sequenceInSession: 1, scheduledAt: startsAt, status: 'PUBLISHED',
    }])),
  )
  render(
    <MemoryRouter initialEntries={[`/e/demo/${path}`]}>
      <Routes>
        <Route path="/e/:slug" element={<PublicLayout />}>
          <Route path={path || undefined} index={!path}
            element={path ? <SchedulePage /> : <PublicEventPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
  const browserTime = new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(startsAt))
  expect((await screen.findAllByText(new RegExp(browserTime))).length).toBeGreaterThan(0)
  expect(screen.getAllByText(/время местное/)).toHaveLength(1)
})
