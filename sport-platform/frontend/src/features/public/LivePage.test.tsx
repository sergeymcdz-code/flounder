import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LiveBoard, AthleteSearch } from './LivePage'
import { usePublicData, LIVE_POLL_MS } from './usePublicData'
import { publicApi } from '../../api/public.api'
import { demoEvent, demoSchedule, demoStartList } from '../../mocks/demo-data'
import type { Live } from './public.types'
import { server } from '../../test/setup'

const units = demoStartList.sessions.flatMap((session) =>
  session.disciplines.flatMap((discipline) => discipline.heats),
)
const loadLive = (signal: AbortSignal) => publicApi.live('demo', signal)
function PollingBoard() {
  const { data, error } = usePublicData(loadLive, LIVE_POLL_MS)
  return (
    <MemoryRouter>
      {error && <p role="alert">{error}</p>}
      {data && (
        <LiveBoard live={data} list={demoStartList} lanes={8} slug="demo" />
      )}
    </MemoryRouter>
  )
}
afterEach(() => {
  vi.useRealTimers()
  Object.defineProperty(document, 'hidden', {
    configurable: true,
    value: false,
  })
})

describe('Public live', () => {
  it('renders the current event, heat, lanes and the next three starts', () => {
    const live: Live = {
      currentUnit: units[0],
      next: units.slice(1, 4),
      updatedAt: '2026-10-15T10:00:00+03:00',
    }
    render(
      <MemoryRouter>
        <LiveBoard live={live} list={demoStartList} lanes={8} slug="demo" />
      </MemoryRouter>,
    )
    expect(
      screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/Вид № 1 · 50 м/)).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(9)
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  it('shows delay and estimates following starts in venue time', () => {
    render(<MemoryRouter><LiveBoard live={{
      currentUnit: units[0],
      next: [{ ...units[1], scheduledAt: '2026-10-17T06:00:00Z', estimatedStartAt: '2026-10-17T06:12:00Z' }],
      updatedAt: '2026-10-17T06:00:00Z', delaySec: 720,
    }} lanes={8} slug="demo" timeZone="Europe/Samara" /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Соревнование идёт с задержкой 12 мин')
    expect(screen.getByText(/10:12 · Заплыв/)).toBeInTheDocument()
    expect(screen.getByText('Расчётное время старта')).toBeInTheDocument()
  })

  it.each([0, undefined])('uses the schedule without an estimate label when delaySec is %s', (delaySec) => {
    render(<MemoryRouter><LiveBoard live={{
      currentUnit: units[0],
      next: [{ ...units[1], scheduledAt: '2026-10-17T06:00:00Z', estimatedStartAt: '2026-10-17T06:12:00Z' }],
      updatedAt: '2026-10-17T06:00:00Z', delaySec,
    }} lanes={8} slug="demo" timeZone="Europe/Samara" /></MemoryRouter>)
    expect(screen.getByRole('listitem')).toHaveTextContent('10:00 · Заплыв')
    expect(screen.queryByText(/10:12 · Заплыв/)).not.toBeInTheDocument()
    expect(screen.queryByText('Расчётное время старта')).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('resolves an in-progress heat omitted from the published start-list through the schedule', () => {
    render(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: units[0],
            next: [],
            updatedAt: '2026-10-15T10:00:00+03:00',
          }}
          list={{ sessions: [] }}
          schedule={demoSchedule}
          disciplines={demoEvent.disciplines}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getByText(/Вид № 1 · 50 м/)).toBeInTheDocument()
    expect(screen.getByText(/Заплыв 1 из 2/)).toBeInTheDocument()
  })

  it('handles an empty current heat without displaying made-up results', () => {
    render(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: null,
            next: [],
            updatedAt: '2026-10-15T10:00:00+03:00',
          }}
          list={demoStartList}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('Сейчас нет активного заплыва')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('polls every five seconds, pauses when hidden and refreshes on return', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/public/events/demo/live', () => {
        const live: Live = {
          currentUnit: units[calls++ % 2],
          next: [],
          updatedAt: new Date().toISOString(),
        }
        return HttpResponse.json(live)
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { unmount } = render(<PollingBoard />)
    await vi.waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
      ).toBeInTheDocument(),
    )
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000)
    })
    await vi.waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Сейчас заплыв №2' }),
      ).toBeInTheDocument(),
    )
    expect(calls).toBe(2)
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: true,
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000)
    })
    expect(calls).toBe(2)
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    })
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
    })
    await vi.waitFor(() => expect(calls).toBe(3))
    unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000)
    })
    expect(calls).toBe(3)
  })

  it('keeps the last successful heat when polling fails', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/public/events/demo/live', () =>
        ++calls === 1
          ? HttpResponse.json({
              currentUnit: units[0],
              next: [],
              updatedAt: new Date().toISOString(),
            })
          : HttpResponse.error(),
      ),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    render(<PollingBoard />)
    await vi.waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
      ).toBeInTheDocument(),
    )
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000)
    })
    await vi.waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Нет связи'),
    )
    expect(
      screen.getByRole('heading', { name: 'Сейчас заплыв №1' }),
    ).toBeInTheDocument()
  })

  it('searches through the API and shows a swimmer with places and results', async () => {
    const userEvent = (await import('@testing-library/user-event')).default
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <AthleteSearch slug="demo" />
      </MemoryRouter>,
    )
    const lane = units[0].lanes[0]
    if (lane.empty) throw new Error('Expected an occupied lane')
    const athlete = lane.athlete
    await user.type(screen.getByRole('searchbox'), athlete.lastName)
    expect(
      await screen.findByRole('heading', {
        name: `${athlete.lastName} ${athlete.firstName}`,
      }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/место в группе/).length).toBeGreaterThan(0)
    await user.clear(screen.getByRole('searchbox'))
    expect(screen.queryByText(/место в группе/)).not.toBeInTheDocument()
  })
})
