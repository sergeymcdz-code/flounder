import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, expect, it, vi } from 'vitest'
import { PublicLayout } from './PublicLayout'
import { PublicEventPage } from './EventPage'
import { LiveBoard, LivePage } from './LivePage'
import { SchedulePage } from './SchedulePage'
import { ResultsPage } from './ResultsPage'
import {
  demoEvent,
  demoSchedule,
  demoResults,
  demoStartList,
} from '../../mocks/demo-data'
import { server } from '../../test/setup'
import type { PublicEvent } from './public.types'

const unit = demoStartList.sessions[0].disciplines[0].heats[0]
function PublicContext({ event }: { event: PublicEvent }) {
  return <Outlet context={{ event }} />
}
function renderPage(page: React.ReactNode, event = demoEvent) {
  return render(
    <MemoryRouter>
      <Routes>
        <Route element={<PublicContext event={event} />}>
          <Route index element={page} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}
afterEach(() => vi.useRealTimers())

it('uses live metadata before stale start-list or schedule metadata', () => {
  render(
    <MemoryRouter>
      <LiveBoard
        live={{
          currentUnit: {
            ...unit,
            disciplineId: 'new',
            disciplineNumber: 42,
            disciplineName: 'Новый вид',
            status: 'FINISHED',
          },
          next: [],
          updatedAt: new Date().toISOString(),
        }}
        list={demoStartList}
        schedule={demoSchedule}
        disciplines={demoEvent.disciplines}
        lanes={8}
        slug="demo"
      />
    </MemoryRouter>,
  )
  expect(screen.getByText('Вид № 42 · Новый вид')).toBeInTheDocument()
  expect(screen.getByText('Заплыв завершён')).toBeInTheDocument()
})

it('shows the live board when both fallback endpoints fail', async () => {
  server.use(
    http.get('*/api/v1/public/events/demo/start-list', () =>
      HttpResponse.error(),
    ),
    http.get('*/api/v1/public/events/demo/schedule', () =>
      HttpResponse.error(),
    ),
    http.get('*/api/v1/public/events/demo/live', () =>
      HttpResponse.json({
        currentUnit: {
          ...unit,
          disciplineNumber: 42,
          disciplineName: 'Новый вид',
        },
        next: [],
        updatedAt: new Date().toISOString(),
      }),
    ),
  )
  renderPage(<LivePage />)
  expect(
    await screen.findByRole('heading', { name: 'Сейчас заплыв №1' }),
  ).toBeInTheDocument()
  expect(screen.getByText('Вид № 42 · Новый вид')).toBeInTheDocument()
  expect(screen.getAllByRole('row')).toHaveLength(9)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('keeps loaded live data with a single polling error message', async () => {
  let calls = 0
  server.use(
    http.get('*/api/v1/public/events/demo/live', () =>
      ++calls === 1
        ? HttpResponse.json({
            currentUnit: unit,
            next: [],
            updatedAt: new Date().toISOString(),
          })
        : HttpResponse.error(),
    ),
  )
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderPage(<LivePage />)
  await vi.waitFor(() =>
    expect(
      screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
    ).toBeInTheDocument(),
  )
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5000)
  })
  await vi.waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(1))
  expect(screen.getAllByText(/Показаны последние данные/)).toHaveLength(1)
  expect(
    screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
  ).toBeInTheDocument()
})

it('renders a dedicated event-not-found screen without retry or event navigation', async () => {
  render(
    <MemoryRouter initialEntries={['/e/missing']}>
      <Routes>
        <Route path="e/:slug" element={<PublicLayout />}>
          <Route index element={<PublicEventPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
  expect(
    await screen.findByRole('heading', { name: 'Соревнование не найдено' }),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: 'Повторить' }),
  ).not.toBeInTheDocument()
  expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
})

it('renders disciplines without a session and nullable program fields', () => {
  renderPage(<PublicEventPage />, {
    ...demoEvent,
    disciplines: [
      {
        ...demoEvent.disciplines[0],
        name: 'Отдельный вид',
        sessionId: null,
        distanceM: null,
        orderInSession: null,
      },
    ],
  })
  expect(
    screen.getByRole('heading', { name: 'Без сессии' }),
  ).toBeInTheDocument()
  expect(screen.getByText(/Отдельный вид/)).toBeInTheDocument()
})

it.each([
  ['schedule', 30_000, <SchedulePage key="schedule" />, demoSchedule],
  ['results', 60_000, <ResultsPage key="results" />, demoResults],
] as const)(
  'polls %s at its own interval',
  async (endpoint, interval, page, payload) => {
    let calls = 0
    server.use(
      http.get(`*/api/v1/public/events/demo/${endpoint}`, () => {
        calls++
        return HttpResponse.json(payload)
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderPage(page)
    await vi.waitFor(() => expect(calls).toBe(1))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(interval - 1000)
    })
    expect(calls).toBe(1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await vi.waitFor(() => expect(calls).toBe(2))
  },
)
