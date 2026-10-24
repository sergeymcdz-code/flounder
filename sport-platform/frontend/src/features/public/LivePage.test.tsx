import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LiveBoard, AthleteSearch, LivePage } from './LivePage'
import { usePublicData, LIVE_POLL_MS } from './usePublicData'
import { publicApi } from '../../api/public.api'
import { demoEvent, demoSchedule, demoStartList } from '../../mocks/demo-data'
import type { Live } from './public.types'
import { server } from '../../test/setup'
import { MY_SWIMMERS_KEY } from './my-swimmers'

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
      screen.getByRole('heading', { name: 'Сейчас заплыв №1 из 2' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/Вид № 1 · 50 м/)).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(9)
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    const nt = screen.getByLabelText(
      'NT — Нет времени (результат не зафиксирован)',
    )
    expect(nt.tagName).toBe('ABBR')
    expect(nt).toHaveAttribute(
      'title',
      'Нет времени (результат не зафиксирован)',
    )
    expect(
      screen.getByLabelText('Обозначения статусов результатов'),
    ).toHaveTextContent('NT — нет времени')
  })

  it('explains a DNS result returned by athlete search', async () => {
    server.use(
      http.get('*/api/v1/public/events/demo/athletes', () =>
        HttpResponse.json([
          {
            athleteKey: 'dns',
            lastName: 'Иванов',
            firstName: 'Иван',
            birthYear: 2012,
            club: null,
            swims: [
              {
                disciplineName: '50 м',
                heatNumber: 1,
                lane: 1,
                scheduledAt: null,
                result: {
                  status: 'DNS',
                  display: null,
                  place: null,
                  placeInAgeGroup: null,
                },
              },
            ],
          },
        ]),
      ),
    )
    const user = (await import('@testing-library/user-event')).default.setup()
    render(
      <MemoryRouter>
        <AthleteSearch slug="demo" />
      </MemoryRouter>,
    )
    await user.type(screen.getByRole('searchbox'), 'Иванов')
    const code = await screen.findByLabelText('DNS — Не стартовал')
    expect(code).toHaveAttribute('title', 'Не стартовал')
    await user.click(code)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Не стартовал')
  })

  it('shows delay and estimates following starts in venue time', () => {
    render(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: units[0],
            next: [
              {
                ...units[1],
                scheduledAt: '2026-10-17T06:00:00Z',
                estimatedStartAt: '2026-10-17T06:12:00Z',
              },
            ],
            updatedAt: '2026-10-17T06:00:00Z',
            delaySec: 720,
          }}
          lanes={8}
          slug="demo"
          timeZone="Europe/Samara"
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('status')).toHaveTextContent(
      'Соревнование идёт с задержкой 12 мин',
    )
    expect(screen.getByText(/10:12 · Заплыв/)).toBeInTheDocument()
    expect(screen.getByText('Расчётное время старта')).toBeInTheDocument()
  })

  it.each([0, undefined])(
    'uses the schedule without an estimate label when delaySec is %s',
    (delaySec) => {
      render(
        <MemoryRouter>
          <LiveBoard
            live={{
              currentUnit: units[0],
              next: [
                {
                  ...units[1],
                  scheduledAt: '2026-10-17T06:00:00Z',
                  estimatedStartAt: '2026-10-17T06:12:00Z',
                },
              ],
              updatedAt: '2026-10-17T06:00:00Z',
              delaySec,
            }}
            lanes={8}
            slug="demo"
            timeZone="Europe/Samara"
          />
        </MemoryRouter>,
      )
      expect(screen.getByRole('listitem')).toHaveTextContent('10:00 · Заплыв')
      expect(screen.queryByText(/10:12 · Заплыв/)).not.toBeInTheDocument()
      expect(
        screen.queryByText('Расчётное время старта'),
      ).not.toBeInTheDocument()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    },
  )

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
    expect(
      screen.getByRole('heading', { name: 'Сейчас заплыв №1 из 2' }),
    ).toHaveTextContent('Заплыв 1 из 2')
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

  it('polls every fifteen seconds, pauses when hidden and refreshes on return', async () => {
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
        screen.getByRole('heading', { name: 'Сейчас заплыв №1 из 2' }),
      ).toBeInTheDocument(),
    )
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000)
    })
    await vi.waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Сейчас заплыв №2 из 2' }),
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
      await vi.advanceTimersByTimeAsync(15_000)
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
        screen.getByRole('heading', { name: 'Сейчас заплыв №1 из 2' }),
      ).toBeInTheDocument(),
    )
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000)
    })
    await vi.waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Нет связи'),
    )
    expect(
      screen.getByRole('heading', { name: 'Сейчас заплыв №1 из 2' }),
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

function FullLivePage() {
  return (
    <MemoryRouter initialEntries={['/live']}>
      <Routes>
        <Route element={<Outlet context={{ event: demoEvent }} />}>
          <Route path="/live" element={<LivePage />} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe('Live redesign', () => {
  it('labels a scheduled heat without announcing that it is running', () => {
    render(<MemoryRouter><LiveBoard live={{ currentUnit: { ...units[0], status: 'SCHEDULED' }, next: [], updatedAt: '' }} lanes={8} slug="demo" /></MemoryRouter>)
    expect(screen.getByText('Текущий заплыв', { selector: 'p.fl-eyebrow' })).toBeInTheDocument()
    expect(screen.queryByText('● Сейчас в бассейне')).not.toBeInTheDocument()
  })
  it('uses the reported heat status and does not infer publication from completion', () => {
    const { rerender } = render(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: { ...units[0], status: 'IN_PROGRESS' },
            next: [],
            updatedAt: '',
          }}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('Идёт заплыв')).toBeInTheDocument()
    expect(screen.getByText('● Сейчас в бассейне')).toBeInTheDocument()
    expect(screen.getByRole('table')).not.toHaveAttribute('tabindex')
    expect(
      document.querySelector('[data-status="IN_PROGRESS"]'),
    ).toBeInTheDocument()
    rerender(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: { ...units[0], status: 'FINISHED' },
            next: [],
            updatedAt: '',
          }}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.queryByText('Идёт заплыв')).not.toBeInTheDocument()
    expect(
      document.querySelector('[data-status="FINISHED"]'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Официально')).not.toBeInTheDocument()
    expect(screen.queryByText('● Сейчас в бассейне')).not.toBeInTheDocument()
    expect(
      screen.getByText(/Опубликованные результаты — в разделе/),
    ).toBeInTheDocument()
  })

  it('keeps withdrawn and empty lanes and splits hundredths without losing the accessible time', () => {
    render(
      <MemoryRouter>
        <LiveBoard
          live={{
            currentUnit: {
              ...units[0],
              lanes: [
                {
                  lane: 1,
                  registrationId: 'one',
                  athlete: {
                    lastName: 'Иванов',
                    firstName: 'Иван',
                    birthYear: 2012,
                    club: 'Волна',
                  },
                  seedTimeMs: 32650,
                },
                {
                  lane: 2,
                  registrationId: 'two',
                  athlete: {
                    lastName: 'Петров',
                    firstName: 'Пётр',
                    birthYear: 2013,
                    club: null,
                  },
                  seedTimeMs: 31000,
                  withdrawn: true,
                },
              ],
            },
            next: [],
            updatedAt: '',
          }}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getAllByRole('row')).toHaveLength(9)
    expect(screen.getByLabelText('00:32.65')).toHaveTextContent('00:32.65')
    expect(screen.getByText('.65').tagName).toBe('SMALL')
    expect(screen.queryByLabelText('00:31.00')).not.toBeInTheDocument()
    expect(screen.getByText('Снят')).toBeInTheDocument()
    expect(screen.getByText('2012')).toBeInTheDocument()
    expect(screen.getByText('Волна')).toBeInTheDocument()
  })

  it('shows eight loading placeholders, then places the update time beside the page heading', async () => {
    let complete!: (value: Response) => void
    server.use(
      http.get(
        '*/api/v1/public/events/demo/live',
        () =>
          new Promise<Response>((resolve) => {
            complete = resolve
          }),
      ),
    )
    const view = render(<FullLivePage />)
    expect(
      screen
        .getByRole('status', { name: 'Загружаем табло' })
        .querySelectorAll('[aria-hidden="true"]'),
    ).toHaveLength(8)
    await vi.waitFor(() => expect(complete).toBeTypeOf('function'))
    await act(async () =>
      complete(
        HttpResponse.json({
          currentUnit: units[0],
          next: [],
          updatedAt: '2026-10-17T06:00:00Z',
        }),
      ),
    )
    expect(
      await screen.findByText(/обновлено .* · каждые 15 с/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 1 }).parentElement,
    ).toHaveTextContent(/обновлено/)
    expect(
      screen.getByRole('heading', { level: 1 }).closest('[data-theme]'),
    ).toHaveAttribute('data-theme', 'dark')
    view.unmount()
  })

  it('offers retry on initial network failure and restores the board', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/public/events/demo/live', () =>
        ++calls === 1
          ? HttpResponse.error()
          : HttpResponse.json({
              currentUnit: units[0],
              next: [],
              updatedAt: '2026-10-17T06:00:00Z',
            }),
      ),
    )
    const user = (await import('@testing-library/user-event')).default.setup()
    render(<FullLivePage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Нет связи')
    await user.click(screen.getByRole('button', { name: 'Повторить' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps the saved-swimmer countdown in its card beside the current heat', async () => {
    const lane = units[1].lanes.find((lane) => !lane.empty)
    if (!lane || lane.empty) throw new Error('Expected swimmer')
    localStorage.setItem(
      MY_SWIMMERS_KEY,
      JSON.stringify([
        {
          name: `${lane.athlete.lastName} ${lane.athlete.firstName}`,
          club: lane.athlete.club,
          yearOfBirth: lane.athlete.birthYear,
        },
      ]),
    )
    server.use(
      http.get('*/api/v1/public/events/demo/live', () =>
        HttpResponse.json({
          currentUnit: units[0],
          next: units.slice(1, 4),
          updatedAt: '2026-10-17T06:00:00Z',
        }),
      ),
    )
    render(<FullLivePage />)
    const card = await screen.findByRole('region', { name: 'Мои пловцы' })
    expect(card).toHaveTextContent(
      `${lane.athlete.lastName} ${lane.athlete.firstName}`,
    )
    expect(card).toHaveTextContent('через 1 заплыв')
    expect(card.parentElement?.tagName).toBe('ASIDE')
    localStorage.removeItem(MY_SWIMMERS_KEY)
  })
})
