import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterAll, beforeEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { demoEvent } from '../../mocks/demo-data'
import { server } from '../../test/setup'
import { PublicLayout } from '../public/PublicLayout'
import { PublicDisciplineResultsPage } from './PublicDisciplineResultsPage'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())

const event = { ...demoEvent, id: 'event', title: 'Кубок Самары', disciplines: [{ ...demoEvent.disciplines[0], id: 'discipline', number: 3, name: '50 м вольный стиль' }] }
const resultsUrl = '*/api/v1/public/events/event/disciplines/discipline/results'
const entry = (status: string, index: number, extra = {}) => ({
  status, participantName: `Участник ${index + 1}`, place: null, clubName: null,
  laneOrPosition: null, rawValue: null, resultType: 'TIME', ...extra,
})

beforeEach(() => {
  server.use(
    http.get('*/api/v1/public/events/demo', () => HttpResponse.json(event)),
    http.get('*/api/v1/public/events/demo/standings', () => HttpResponse.json({ message: 'Нет зачёта' }, { status: 404 })),
  )
})

function renderPage(path = '/events/demo/disciplines/discipline/results') {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider>
    <Routes><Route path="/events/:publicSlug/disciplines/:disciplineId/results" element={<PublicLayout />}>
      <Route index element={<PublicDisciplineResultsPage />} />
    </Route></Routes>
  </AuthProvider></MemoryRouter>)
}

it('shows canonical result codes matching the legend and keeps ordinary status labels', async () => {
  server.use(http.get(resultsUrl, () => HttpResponse.json({
    eventId: 'event', eventDisciplineId: 'discipline', units: [{
      id: 'heat', label: 'Заплыв 1', sequenceNumber: 1,
      entries: ['DNS', 'DNF', 'DSQ', 'CORRECTED', 'DID_NOT_START', 'DID_NOT_FINISH', 'DISQUALIFIED', 'VALID', 'PENDING', 'EXHIBITION'].map(entry),
    }],
  })))
  renderPage()
  const table = within(await screen.findByRole('table'))
  for (const code of ['DNS', 'DNF', 'DSQ', 'CORRECTED']) {
    for (const element of table.getAllByText(code, { exact: true })) expect(element.tagName).toBe('ABBR')
  }
  expect(table.getAllByText('DNS', { exact: true })).toHaveLength(2)
  expect(table.getByText('Учтён')).toBeInTheDocument()
  expect(table.getByText('Ожидает пересчёта')).toBeInTheDocument()
  expect(table.getByText('Вне зачёта')).toBeInTheDocument()
  expect(screen.getByLabelText('Обозначения статусов результатов')).toHaveTextContent(
    'DNS — не стартовал · DNF — не финишировал · DSQ — дисквалифицирован · исправлено — результат исправлен после публикации',
  )
})

it('renders inside the event shell with plain Russian copy, lanes and agreeing counts', async () => {
  const standings = vi.fn()
  server.use(http.get('*/api/v1/public/events/demo/standings', () => { standings(); return HttpResponse.json({}) }))
  server.use(http.get(resultsUrl, () => HttpResponse.json({
    eventId: 'event', eventDisciplineId: 'discipline', units: [
      { id: 'h1', label: 'Заплыв 1', sequenceNumber: 1, entries: [0, 1, 2].map(i => entry('VALID', i, { place: i + 1, laneOrPosition: i + 3, rawValue: '00:3' + i + '.15', clubName: 'Дельфин' })) },
      { id: 'h2', label: 'Заплыв 2', sequenceNumber: 2, entries: [entry('VALID', 5, { place: 4, laneOrPosition: 0 })] },
    ],
  })))
  renderPage()
  expect(await screen.findByRole('heading', { level: 1, name: 'Вид № 3. 50 м вольный стиль' })).toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: 'Навигация события' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Кубок Самары' })).toHaveAttribute('href', '/e/demo')
  expect(screen.getByRole('link', { name: '← Все результаты' })).toHaveAttribute('href', '/e/demo/results')
  expect(await screen.findByText('2 заплыва · 4 участника')).toBeInTheDocument()
  expect(screen.getByText('3 участника')).toBeInTheDocument()
  expect(screen.getByText('1 участник')).toBeInTheDocument()
  expect(screen.getByText('Дорожка 3')).toBeInTheDocument()
  expect(screen.queryByText(/Соревновательн|позиция|дисциплин/i)).not.toBeInTheDocument()
  // A missing lane (0/null) does not leave an empty caption under the name.
  expect(within(screen.getByRole('region', { name: 'Заплыв 2' })).queryByText(/Дорожка/)).not.toBeInTheDocument()
  expect(screen.getAllByRole('region', { name: /прокручивается по горизонтали/ })[0]).toHaveClass('fl-scroll-x')
  expect(document.title).toBe('Результаты: 50 м вольный стиль · Кубок Самары · SportGate')
  expect(standings).not.toHaveBeenCalled()
})

it('shows loading first, then an empty state with the next step', async () => {
  let release!: () => void
  server.use(http.get(resultsUrl, async () => {
    await new Promise<void>(resolve => { release = resolve })
    return HttpResponse.json({ eventId: 'event', eventDisciplineId: 'discipline', units: [] })
  }))
  renderPage()
  expect(await screen.findByText('Загружаем результаты…')).toBeInTheDocument()
  await vi.waitFor(() => expect(release).toBeTypeOf('function'))
  release()
  expect(await screen.findByRole('heading', { name: 'Результаты ещё не опубликованы' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть табло' })).toHaveAttribute('href', '/e/demo/live')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

it('distinguishes server errors and retries', async () => {
  let calls = 0
  server.use(http.get(resultsUrl, () => {
    calls += 1
    return calls === 1 ? HttpResponse.json({ message: 'Ошибка' }, { status: 500 })
      : HttpResponse.json({ eventId: 'event', eventDisciplineId: 'discipline', units: [] })
  }))
  renderPage()
  expect(await screen.findByText('Сервер ответил ошибкой (500). Не удалось загрузить результаты.')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByRole('heading', { name: 'Результаты ещё не опубликованы' })).toBeInTheDocument()
  expect(calls).toBe(2)
})

it('reports a missing discipline without requesting its results', async () => {
  const results = vi.fn()
  server.use(http.get('*/api/v1/public/events/event/disciplines/:id/results', () => { results(); return HttpResponse.json({ units: [] }) }))
  renderPage('/events/demo/disciplines/unknown/results')
  expect(await screen.findByRole('heading', { level: 1, name: 'Вид программы не найден' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть результаты' })).toHaveAttribute('href', '/e/demo/results')
  expect(document.title).toBe('Вид программы не найден · Кубок Самары · SportGate')
})
