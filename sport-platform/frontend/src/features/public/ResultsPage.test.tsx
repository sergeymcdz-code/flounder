import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, Outlet } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { publicApi, PublicApiError } from '../../api/public.api'
import { downloadProtocol } from '../../api/protocols.api'
import { demoEvent } from '../../mocks/demo-data'
import { ResultsPage } from './ResultsPage'
import { ResultsTable } from './ResultsTable'
import type { ResultRow, Results } from './public.types'
import { RESULTS_POLL_MS } from './usePublicData'

vi.mock('../../api/protocols.api', () => ({ downloadProtocol: vi.fn().mockResolvedValue(undefined) }))

const row: ResultRow = {
  lastName: 'Иванов', firstName: 'Иван', birthYear: 2013, club: 'Волна',
  display: '00:32.15', timeMs: 32150, status: 'VALID', place: 1,
}
const rows: ResultRow[] = [
  { ...row, lastName: 'Петров', timeMs: 33150, display: '00:33.15', place: 2 },
  row,
]
const results: Results = { disciplines: [
  { id: 'free', number: 1, name: '50 м вольный стиль', overall: rows,
    byAgeGroup: [{ code: '2013', name: '2013 год', rows }] },
  { id: 'back', number: 2, name: '50 м на спине', overall: [{ ...row, lastName: 'Сидоров' }], byAgeGroup: [] },
] }

function showPage(path = '/') {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route element={<Outlet context={{ event: demoEvent }} />}>
      <Route path="*" element={<ResultsPage />} />
    </Route>
  </Routes></MemoryRouter>)
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('shows loading placeholders, a preliminary badge and disabled action reasons', () => {
  vi.spyOn(publicApi, 'results').mockImplementation(() => new Promise(() => {}))
  showPage()
  expect(screen.getByRole('status')).toHaveTextContent('Загружаем результаты')
  expect(screen.getByText('Предварительно')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Печать' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Печать' })).toHaveAttribute('title', 'Дождитесь загрузки результатов')
  expect(screen.getByText('Дождитесь загрузки результатов')).toBeInTheDocument()
})

it('preserves grouped defaults, switches segments and filters a surname and discipline client-side', async () => {
  const load = vi.spyOn(publicApi, 'results').mockResolvedValue(results)
  const user = userEvent.setup()
  showPage()
  await screen.findByRole('table', { name: '50 м вольный стиль · 2013 год' })
  expect(screen.getByRole('button', { name: 'По группам' })).toHaveAttribute('aria-pressed', 'true')
  await user.click(screen.getByRole('button', { name: 'Абсолют' }))
  expect(screen.getByRole('button', { name: 'Абсолют' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('table', { name: '50 м вольный стиль · Абсолют' })).toBeInTheDocument()
  await user.type(screen.getByRole('searchbox', { name: 'Фамилия' }), ' ИВАН ')
  expect(screen.getAllByRole('table')).toHaveLength(1)
  expect(screen.getByText('Иван', { selector: 'mark' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '№ 2 · 50 м на спине' }))
  expect(screen.getByText('Участники не найдены')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Сбросить фильтры' }))
  expect(screen.getByRole('searchbox')).toHaveValue('')
  expect(screen.getAllByRole('table')).toHaveLength(2)
  expect(load).toHaveBeenCalledTimes(1)
  expect(load).toHaveBeenCalledWith(demoEvent.publicSlug, expect.any(AbortSignal))
})

it('does not replace a filtered age group with absolute standings', async () => {
  vi.spyOn(publicApi, 'results').mockResolvedValue({ disciplines: [{
    ...results.disciplines[0], overall: [row],
    byAgeGroup: [{ code: 'other', name: 'Другая группа', rows: [{ ...row, lastName: 'Петров' }] }],
  }] })
  const user = userEvent.setup()
  showPage()
  await screen.findByRole('table')
  await user.type(screen.getByRole('searchbox'), 'Иванов')
  expect(screen.getByText('Участники не найдены')).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Абсолют' }))
  expect(screen.getByRole('table', { name: '50 м вольный стиль · Абсолют' })).toBeInTheDocument()
})

it.each([
  { disciplines: [] },
  { disciplines: [{ ...results.disciplines[0], overall: [], byAgeGroup: [{ code: 'empty', name: 'Пустая группа', rows: [] }] }] },
])('shows an unpublished state when the response has no result rows', async data => {
  vi.spyOn(publicApi, 'results').mockResolvedValue(data)
  showPage()
  expect(await screen.findByRole('heading', { name: 'Результаты ещё не опубликованы', level: 2 })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть табло' })).toHaveAttribute('href', '/e/demo/live')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

it.each([
  [new TypeError('Failed to fetch'), 'Нет связи. Не удалось обновить данные.'],
  [new PublicApiError(503, 'Попробуйте позже.'), 'Сервер ответил ошибкой (503).'],
  [new PublicApiError(404, 'Не найдено.'), 'Данные не найдены.'],
])('explains load failures and retries the same endpoint', async (error, message) => {
  const load = vi.spyOn(publicApi, 'results').mockRejectedValueOnce(error).mockResolvedValue(results)
  const user = userEvent.setup()
  showPage()
  expect(await screen.findByRole('alert')).toHaveTextContent(message)
  await user.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findAllByRole('table')).toHaveLength(2)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(load).toHaveBeenCalledTimes(2)
})

it('keeps rows and the last successful timestamp during failed polling, then recovers', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-24T10:00:00Z'))
  const load = vi.spyOn(publicApi, 'results')
    .mockResolvedValueOnce(results).mockRejectedValueOnce(new TypeError('offline'))
    .mockResolvedValue({ disciplines: [{ ...results.disciplines[0], overall: [row],
      byAgeGroup: [{ code: '2013', name: '2013 год', rows: [row] }] }] })
  await act(async () => { showPage() })
  const table = screen.getAllByRole('table')[0]
  const timestamp = document.querySelector('time')!.dateTime
  await act(async () => { await vi.advanceTimersByTimeAsync(RESULTS_POLL_MS) })
  expect(screen.getAllByRole('table')[0]).toBe(table)
  expect(screen.getByRole('alert')).toHaveTextContent('Показаны последние успешно загруженные результаты.')
  expect(document.querySelector('time')!.dateTime).toBe(timestamp)
  expect(screen.queryByText('Загружаем результаты…')).not.toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(RESULTS_POLL_MS) })
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(document.querySelector('time')!.dateTime).not.toBe(timestamp)
  expect(screen.getAllByRole('table')).toHaveLength(1)
  expect(load).toHaveBeenCalledTimes(3)
})

it('keeps API places and ordering, highlights all podium places and never mutates rows', () => {
  const input: ResultRow[] = [
    { ...row, lastName: 'Третий', timeMs: 35000, display: '00:35.00', place: 3 },
    ...rows,
    { ...row, lastName: 'Без старта', status: 'DNS', place: 9, timeMs: null, display: null },
  ]
  const copy = structuredClone(input)
  render(<ResultsTable rows={input} caption="Итоговый протокол" />)
  const tableRows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
  expect(tableRows.map(tr => within(tr).getByRole('rowheader').textContent)).toEqual(['1', '2', '3', '—'])
  for (const tr of tableRows.slice(0, 3)) expect(tr.querySelector('th span')!.className).toMatch(/medal/)
  expect(input).toEqual(copy)
})

it('explains exceptional statuses using the shared dictionary, including touch and keyboard tooltips', async () => {
  const statuses = ['DNS', 'DNF', 'DSQ', 'CORRECTED'] as const
  render(<ResultsTable caption="Статусы" rows={statuses.map(status => ({ ...row, status }))} />)
  const legend = screen.getByLabelText('Обозначения статусов результатов')
  expect(legend).toHaveTextContent('DNS — не стартовал')
  expect(legend).toHaveTextContent('DNF — не финишировал')
  expect(legend).toHaveTextContent('DSQ — дисквалифицирован')
  expect(legend).toHaveTextContent('исправлено — результат исправлен после публикации')
  const dsq = screen.getAllByLabelText('DSQ — Дисквалифицирован')[0]
  const user = userEvent.setup()
  await user.click(dsq)
  expect(screen.getByRole('tooltip')).toHaveTextContent('Дисквалифицирован')
  await user.keyboard('{Escape}')
  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  dsq.focus()
  await user.keyboard('{Enter}')
  expect(screen.getByRole('tooltip')).toHaveTextContent('Дисквалифицирован')
})

it('keeps PDF endpoints and invokes manual print', async () => {
  vi.spyOn(publicApi, 'results').mockResolvedValue(results)
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  const user = userEvent.setup()
  showPage()
  await screen.findAllByRole('table')
  await user.click(screen.getByRole('button', { name: 'Скачать PDF' }))
  expect(downloadProtocol).toHaveBeenCalledWith('/api/v1/public/events/demo/results.pdf', 'Итоговый-протокол.pdf', undefined)
  await user.click(screen.getByRole('button', { name: 'Лист награждения (PDF)' }))
  expect(downloadProtocol).toHaveBeenCalledWith('/api/v1/public/events/demo/awards.pdf', 'Лист-награждения.pdf', undefined)
  await user.click(screen.getByRole('button', { name: 'Печать' }))
  expect(print).toHaveBeenCalledTimes(1)
})

it('waits for data and fonts before automatic printing and prints only once across polls', async () => {
  vi.useFakeTimers()
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  Object.defineProperty(document, 'fonts', { configurable: true, value: { ready: Promise.resolve() } })
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => window.setTimeout(() => callback(0), 0))
  vi.stubGlobal('cancelAnimationFrame', (id: number) => window.clearTimeout(id))
  vi.spyOn(publicApi, 'results').mockResolvedValue(results)
  await act(async () => { showPage('/?print=1') })
  await act(async () => { await vi.advanceTimersByTimeAsync(1) })
  expect(print).toHaveBeenCalledTimes(1)
  await act(async () => { await vi.advanceTimersByTimeAsync(RESULTS_POLL_MS * 2) })
  expect(print).toHaveBeenCalledTimes(1)
})
