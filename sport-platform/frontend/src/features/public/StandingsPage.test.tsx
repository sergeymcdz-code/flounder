import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { afterAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { StandingsPage } from './StandingsPage'
import { demoEvent } from '../../mocks/demo-data'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())

function page() {
  return render(<MemoryRouter initialEntries={['/e/cup/standings']}>
    <Routes><Route element={<Outlet context={{ event: demoEvent }} />}><Route path="/e/:slug/standings" element={<StandingsPage />} /></Route></Routes>
  </MemoryRouter>)
}
describe('Public standings', () => {
  it('renders medals, points, preliminary status and PDF without athlete details', async () => {
    server.use(http.get('*/api/v1/public/events/cup/standings', () => HttpResponse.json({
      title: 'Клубный кубок', scope: 'AGE_GROUP', medals: [{ club: 'Волна', gold: 2, silver: 1, bronze: 0 }],
      team: [{ club: 'Волна', points: 25, gold: 2, silver: 1, bronze: 0, scoredResults: 3 }],
      unitsPending: 1, preliminary: true, calculatedAt: '2026-10-15T12:00:00Z', disciplinesIncluded: 2,
    })))
    page()
    expect(await screen.findByRole('heading', { name: 'Клубный кубок' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Предварительный зачёт')
    const tables = screen.getAllByRole('table')
    expect(within(tables[0]).getByRole('rowheader', { name: 'Волна' })).toBeInTheDocument()
    expect(within(tables[1]).getByText('25')).toBeInTheDocument()
    expect(screen.queryByText('Из чего сложилось')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Скачать PDF' })).toBeEnabled()
    expect(screen.queryByRole('link', { name: 'Скачать PDF' })).not.toBeInTheDocument()
  })
  it('hides cached tables and the PDF when publication is unavailable', async () => {
    server.use(http.get('*/api/v1/public/events/cup/standings', () => HttpResponse.json({ code: 'standings.not_published' }, { status: 404 })))
    page()
    expect(await screen.findByRole('heading', { name: 'Зачёт не опубликован' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Скачать PDF' })).not.toBeInTheDocument()
  })
  it.each([
    [404, 'Зачёт не опубликован'],
    [503, 'Генератор PDF занят. Попробуйте позже.'],
  ])('shows a Russian PDF error for HTTP %i and permits retry', async (status, message) => {
    server.use(
      http.get('*/api/v1/public/events/cup/standings', () => HttpResponse.json({
        title: 'Клубный кубок', scope: 'AGE_GROUP', medals: [], team: [], unitsPending: 0,
        preliminary: false, disciplinesIncluded: 0, calculatedAt: '2026-10-17T12:00:00Z',
      })),
      http.get('*/api/v1/public/events/cup/standings.pdf', () => HttpResponse.json({ message }, { status })),
    )
    page()
    fireEvent.click(await screen.findByRole('button', { name: 'Скачать PDF' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(`Сервер вернул ошибку (${status})`)
    expect(screen.getByRole('button', { name: 'Скачать PDF' })).toBeEnabled()
  })

})
