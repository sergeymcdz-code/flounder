import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { expect, it, vi, afterAll } from 'vitest'
vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { server } from '../../test/setup'
import { createFixtures } from '../../mocks/fixtures'
import { CompetitionUnitsPage } from './CompetitionUnitsPage'
it('restores the selected competition and discipline and opens the current heat directly', async () => {
  const { event, heats } = createFixtures()
  localStorage.setItem('sportgate.judge.eventId', event.id); localStorage.setItem('sportgate.judge.disciplineId', event.disciplines[0].id)
  server.use(http.get('*/api/v1/venues', () => HttpResponse.json([])), http.get('*/api/v1/organizer/events', () => HttpResponse.json([event])), http.get('*/api/v1/events/:id', () => HttpResponse.json(event)), http.get('*/api/v1/event-disciplines/:id/competition-units', () => HttpResponse.json(heats)), http.get('*/api/v1/public/events/:slug/live', () => HttpResponse.json({ currentUnit: { id: 'current' }, next: [], updatedAt: new Date().toISOString() })))
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  render(<MemoryRouter><AuthProvider><CompetitionUnitsPage /></AuthProvider></MemoryRouter>)
  expect(await screen.findByRole('link', { name: 'Открыть текущий заплыв' }, { timeout: 5000 })).toHaveAttribute('href', `/operator/competition-units/current?eventId=${event.id}`)
  expect(screen.getByRole('combobox', { name: 'Соревнование' })).toHaveValue(event.id)
  expect(screen.getByRole('combobox', { name: 'Вид программы' })).toHaveValue(event.disciplines[0].id)
  expect(screen.getAllByRole('table').length).toBeGreaterThan(0)
  localStorage.clear(); setAuthSession(null)
})

it('keeps a multi-day live competition in Today after its first day', async () => {
  const { event } = createFixtures()
  event.status = 'LIVE'
  event.eventStartAt = new Date(Date.now() - 86400000).toISOString()
  event.eventEndAt = new Date(Date.now() + 86400000).toISOString()
  server.use(http.get('*/api/v1/organizer/events', () => HttpResponse.json([event])))
  localStorage.clear()
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  try {
    render(<MemoryRouter><AuthProvider><CompetitionUnitsPage /></AuthProvider></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Сегодня' })).toBeVisible()
  } finally { localStorage.clear(); setAuthSession(null) }
})

it('shows the competition empty state only after the initial request completes', async () => {
  let complete!: (response: Response) => void
  const response = new Promise<Response>(resolve => { complete = resolve })
  server.use(http.get('*/api/v1/organizer/events', () => response))
  localStorage.clear()
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  try {
    render(<MemoryRouter><AuthProvider><CompetitionUnitsPage /></AuthProvider></MemoryRouter>)
    expect(screen.queryByText('Нет доступных соревнований')).not.toBeInTheDocument()
    expect(screen.getByText('Загружаем данные…', { selector: 'div' })).toBeVisible()
    complete(HttpResponse.json([]))
    expect(await screen.findByText('Нет доступных соревнований')).toBeVisible()
  } finally { complete(HttpResponse.json([])); localStorage.clear(); setAuthSession(null) }
})
