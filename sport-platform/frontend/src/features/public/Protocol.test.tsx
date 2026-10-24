import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, MemoryRouter, Outlet, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { HeatProtocol, PrintButton, ResultsTable } from './Protocol'
import type { ResultRow } from './public.types'
import { demoEvent, demoStartList } from '../../mocks/demo-data'
import { publicApi, PublicApiError } from '../../api/public.api'
import { StartListPage } from './StartListPage'
import { HeatPage } from './HeatPage'

function renderProtocol(path = '/e/demo/start-list') {
  const router = createMemoryRouter([{
    element: <Outlet context={{ event: demoEvent }} />,
    children: [
      { path: '/e/:slug/start-list', element: <StartListPage /> },
      { path: '/e/:slug/heats/:unitId', element: <HeatPage /> },
    ],
  }], { initialEntries: [path] })
  return render(<RouterProvider router={router} />)
}

describe('Public protocols', () => {
  it('renders all eight lanes, NT, a withdrawn athlete and empty lanes', () => {
    const heat = demoStartList.sessions[0].disciplines[0].heats[0]
    render(
      <MemoryRouter>
        <HeatProtocol
          heat={{
            ...heat,
            lanes: heat.lanes
              .slice(0, 7)
              .map((lane, index) =>
                index === 1 ? { ...lane, withdrawn: true } : lane,
              ),
          }}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getAllByRole('row')).toHaveLength(9)
    expect(screen.getByText('NT')).toBeInTheDocument()
    expect(screen.getByText('Снят')).toBeInTheDocument()
    const last = screen.getAllByRole('row').at(-1)!
    expect(within(last).getAllByText('—')).toHaveLength(4)
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      `/e/demo/heats/${heat.id}`,
    )
  })

  it('sorts non-finishers last, preserves shared places and uses age-group places', () => {
    const base: ResultRow = {
      lastName: 'Иванов',
      firstName: 'Иван',
      birthYear: 2012,
      club: 'Волна',
      display: '00:32.00',
      timeMs: 32000,
      status: 'VALID',
      place: 1,
    }
    const rows: ResultRow[] = [
      {
        ...base,
        lastName: 'Не стартовал',
        status: 'DNS',
        place: null,
        timeMs: null,
      },
      base,
      { ...base, lastName: 'Петров', status: 'OK' },
    ]
    const { rerender } = render(<ResultsTable rows={rows} />)
    expect(screen.getAllByRole('row').at(-1)).toHaveTextContent('DNS')
    expect(
      screen.getAllByRole('rowheader').map((row) => row.textContent),
    ).toEqual(['1', '1', '—'])
    rerender(
      <ResultsTable
        rows={rows.map((row) => ({
          ...row,
          place: row.status === 'VALID' || row.status === 'OK' ? 2 : null,
        }))}
        grouped
      />,
    )
    expect(
      screen.getAllByRole('rowheader').map((row) => row.textContent),
    ).toEqual(['2', '2', '—'])
  })

  it('prints once after data is ready when print=1', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    Object.defineProperty(document, 'fonts', {
      configurable: true,
      value: { ready: Promise.resolve() },
    })
    const { rerender } = render(
      <MemoryRouter initialEntries={['/e/demo/start-list?print=1']}>
        <PrintButton ready={false} />
      </MemoryRouter>,
    )
    expect(print).not.toHaveBeenCalled()
    rerender(
      <MemoryRouter initialEntries={['/e/demo/start-list?print=1']}>
        <PrintButton ready />
      </MemoryRouter>,
    )
    await vi.waitFor(() => expect(print).toHaveBeenCalledTimes(1))
    rerender(
      <MemoryRouter>
        <PrintButton ready />
      </MemoryRouter>,
    )
    expect(print).toHaveBeenCalledTimes(1)
  })

  it('keeps print disabled with an explanation until ready, then prints on click', async () => {
    const user = userEvent.setup()
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    const { rerender } = render(<MemoryRouter><PrintButton ready={false} /></MemoryRouter>)
    expect(screen.getByRole('button', { name: 'Печать' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Печать' })).toHaveAttribute('title', expect.stringContaining('после публикации'))
    await user.click(screen.getByRole('button', { name: 'Печать' }))
    expect(print).not.toHaveBeenCalled()
    rerender(<MemoryRouter><PrintButton ready /></MemoryRouter>)
    await user.click(screen.getByRole('button', { name: 'Печать' }))
    expect(print).toHaveBeenCalledTimes(1)
  })

  it('shows loading, disables document actions and keeps a single accessible page heading', () => {
    vi.spyOn(publicApi, 'startList').mockImplementation(() => new Promise(() => {}))
    renderProtocol()
    expect(screen.getByRole('status')).toHaveTextContent('Загружаем стартовый протокол…')
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Скачать PDF' })).toBeDisabled()
    expect(screen.getByText('Печать и PDF доступны после публикации заплывов.')).toBeInTheDocument()
  })

  it.each([
    { sessions: [] },
    { sessions: [{ ...demoStartList.sessions[0], disciplines: [{ ...demoStartList.sessions[0].disciplines[0], heats: [] }] }] },
  ])('offers the schedule when the protocol contains no published heats: %j', async list => {
    vi.spyOn(publicApi, 'startList').mockResolvedValue(list)
    renderProtocol()
    expect(await screen.findByText('Стартовый протокол ещё не опубликован')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Открыть расписание' })).toHaveAttribute('href', '/e/demo/schedule')
    expect(screen.getByRole('button', { name: 'Печать' })).toBeDisabled()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('distinguishes a server error from a network error and retries the existing API call', async () => {
    const load = vi.spyOn(publicApi, 'startList')
      .mockRejectedValueOnce(new PublicApiError(503, 'Сервис временно недоступен.'))
      .mockResolvedValueOnce(demoStartList)
    renderProtocol()
    expect(await screen.findByRole('alert')).toHaveTextContent('Сервер ответил ошибкой (503).')
    await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
    expect(await screen.findAllByRole('table')).toHaveLength(6)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(load).toHaveBeenCalledTimes(2)
    expect(load).toHaveBeenLastCalledWith('demo', expect.any(AbortSignal))
  })

  it('retains published lanes when a refresh loses the connection', async () => {
    vi.spyOn(publicApi, 'startList').mockResolvedValueOnce(demoStartList)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderProtocol()
    expect(await screen.findAllByRole('table')).toHaveLength(6)
    act(() => { window.dispatchEvent(new Event('online')) })
    expect(await screen.findByRole('alert')).toHaveTextContent('Нет связи.')
    expect(screen.getByRole('alert')).toHaveTextContent('Показаны последние данные.')
    expect(screen.getAllByRole('table')).toHaveLength(6)
    expect(screen.getByRole('button', { name: 'Печать' })).toBeEnabled()
  })

  it('scrolls and focuses the selected discipline without navigating away', async () => {
    vi.spyOn(publicApi, 'startList').mockResolvedValue(demoStartList)
    renderProtocol()
    const nav = await screen.findByRole('navigation', { name: 'Виды программы' })
    const discipline = demoStartList.sessions[1].disciplines[0]
    const heading = screen.getByRole('heading', { name: `Вид № ${discipline.number}. ${discipline.name}` })
    const scroll = vi.fn()
    heading.scrollIntoView = scroll
    await userEvent.click(within(nav).getByRole('link', { name: `Вид ${discipline.number} · ${discipline.name}` }))
    expect(scroll).toHaveBeenCalledWith({ block: 'start' })
    expect(heading).toHaveFocus()
  })

  it('shows the heat context and preserves links to the protocol, live board and results', async () => {
    vi.spyOn(publicApi, 'startList').mockResolvedValue(demoStartList)
    const heat = demoStartList.sessions[0].disciplines[0].heats[0]
    renderProtocol(`/e/demo/heats/${heat.id}`)
    expect(await screen.findByRole('table')).toHaveAccessibleName(`Дорожки заплыва №${heat.number}`)
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(`Заплыв ${heat.number}`)
    expect(screen.getByRole('link', { name: '← Стартовый протокол' })).toHaveAttribute('href', '/e/demo/start-list')
    expect(screen.getByRole('link', { name: 'Табло' })).toHaveAttribute('href', '/e/demo/live')
    expect(screen.getByRole('link', { name: 'Результаты вида' })).toHaveAttribute('href', `/events/demo/disciplines/${demoStartList.sessions[0].disciplines[0].id}/results`)
    expect(screen.getByText(/NT — нет времени/)).toBeInTheDocument()
  })

  it('offers the protocol when a heat is missing from the published list', async () => {
    vi.spyOn(publicApi, 'startList').mockResolvedValue(demoStartList)
    renderProtocol('/e/demo/heats/missing')
    expect(await screen.findByText('Заплыв не найден или ещё не опубликован')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'К протоколу' })).toHaveAttribute('href', '/e/demo/start-list')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('explains a 404 response and provides retry without claiming the browser is offline', async () => {
    vi.spyOn(publicApi, 'startList').mockRejectedValue(new PublicApiError(404, 'Протокол не найден.'))
    renderProtocol()
    expect(await screen.findByRole('alert')).toHaveTextContent('Данные не найдены.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('Нет связи')
    expect(screen.getByRole('button', { name: 'Повторить' })).toBeEnabled()
  })
})
