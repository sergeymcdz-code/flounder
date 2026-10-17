import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { EventAnalyticsPage, EventAnalyticsView, OrganizationAnalyticsPage, OrganizationsAnalyticsListPage } from './AnalyticsPages'
import type { EventAnalytics } from '../../api/analytics.api'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
const { logout, navigateSession, authFlags } = vi.hoisted(() => ({ logout: vi.fn(), navigateSession: { accessToken: 'analytics-test-token' }, authFlags: { isPlatformAdmin: false } }))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: navigateSession, logout, ...authFlags }) }))
const empty: EventAnalytics = {
  eventId: 'event', title: 'Кубок', timezone: 'Europe/Samara', generatedAt: '2026-10-01T21:30:00Z',
  registrations: { total: 0, participants: 0, byStatus: [], byDay: [{ date: '2026-10-01', count: 0 }], funnel: { submitted: 0, confirmed: 0, paid: 0, admitted: 0 } },
  byDiscipline: [], byAgeGroup: [], byClub: [], byGender: [], sessions: [],
  results: { publishedHeats: 0, medianPublicationSec: null, p95PublicationSec: null, latencySamples: 0, latencySource: 'UNAVAILABLE', corrected: 0 },
  validator: { runId: null, executedAt: null, errors: 0, warnings: 0 },
  importStats: { available: false, reason: 'История импортов пока не сохраняется в базе данных', recent: [] },
}
beforeEach(() => { logout.mockClear(); authFlags.isPlatformAdmin = false })
function eventPage() {
  return render(<MemoryRouter initialEntries={['/organizer/events/event/analytics']}><Routes><Route path="/organizer/events/:eventId/analytics" element={<EventAnalyticsPage />} /><Route path="/login" element={<h1>Вход</h1>} /></Routes></MemoryRouter>)
}
it('shows honest empty states and missing timing instead of zero latency', () => {
  render(<EventAnalyticsView data={empty} />)
  expect(screen.getAllByText('Заявок пока нет').length).toBeGreaterThan(0)
  expect(screen.getByText('Валидатор ещё не запускался')).toBeInTheDocument()
  expect(screen.getByText('История импортов пока не сохраняется в базе данных')).toBeInTheDocument()
  const panel = screen.getByRole('heading', { name: 'Время публикации результатов' }).closest('section')!
  expect(within(panel).getAllByText('—')).toHaveLength(2)
  expect(within(panel).queryByText('Цель достигнута.')).not.toBeInTheDocument()
})
it('renders exact accessible chart values, funnel and target breach', async () => {
  const a = { ...empty, registrations: { ...empty.registrations, total: 10, byDay: [{ date: '2026-10-01', count: 8 }, { date: '2026-10-02', count: 2 }], funnel: { submitted: 10, confirmed: 8, paid: 6, admitted: 4 } }, results: { ...empty.results, latencySource: 'RANKING_LOG', latencySamples: 20, medianPublicationSec: 100, p95PublicationSec: 500 } }
  render(<EventAnalyticsView data={a} />)
  expect(screen.getByRole('img', { name: /Заявки по дням/ })).toBeInTheDocument()
  await userEvent.click(screen.getByText('Показать значения по дням'))
  expect(screen.getByRole('cell', { name: '2026-10-02' })).toBeVisible()
  expect(screen.getByText('1:40')).toBeInTheDocument()
  expect(screen.getByText('8:20')).toBeInTheDocument()
  expect(screen.getByText('Заплывов в выборке')).toBeInTheDocument()
  expect(screen.getByText(/Цель превышена/)).toBeInTheDocument()
  expect(screen.getByText('Допущено в посев')).toBeInTheDocument()
  expect(screen.getByText(/«Подано» включает отклонённые и снятые заявки/)).toBeInTheDocument()
})
it('sends the token for JSON and XLSX and uses the server timezone', async () => {
  let exportToken: string | null = null
  server.use(http.get('*/api/v1/events/event/analytics', ({ request }) => {
    expect(request.headers.get('Authorization')).toBe('Bearer analytics-test-token')
    return HttpResponse.json(empty)
  }), http.get('*/api/v1/events/event/analytics/export.xlsx', ({ request }) => {
    exportToken = request.headers.get('Authorization')
    return new HttpResponse(new Uint8Array([1, 2]), { headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' } })
  }))
  const create = vi.fn(() => 'blob:test'), revoke = vi.fn()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }))
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  eventPage()
  await screen.findByRole('heading', { name: 'Аналитика события' })
  expect(screen.getByText(/02.10.2026, 01:30/)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Скачать XLSX' }))
  await waitFor(() => expect(click).toHaveBeenCalled())
  expect(exportToken).toBe('Bearer analytics-test-token'); expect(create).toHaveBeenCalled()
  click.mockRestore()
})
it('does not show metrics after a forbidden response', async () => {
  server.use(http.get('*/api/v1/events/event/analytics', () => HttpResponse.json({ message: 'Доступ запрещён' }, { status: 403 })))
  eventPage()
  expect(await screen.findByText('Нет доступа к этой аналитике.')).toBeInTheDocument()
  expect(screen.queryByText('Всего заявок')).not.toBeInTheDocument()
})
it('expires the session on 401 and navigates to login', async () => {
  server.use(http.get('*/api/v1/events/event/analytics', () => HttpResponse.json({}, { status: 401 })))
  eventPage()
  expect(await screen.findByRole('heading', { name: 'Вход' })).toBeInTheDocument(); expect(logout).toHaveBeenCalledOnce()
})
it('shows organization participation, repeat profiles and revenue', async () => {
  server.use(http.get('*/api/v1/organizations/org/analytics', () => HttpResponse.json({ organizationId: 'org', name: 'Волна', generatedAt: empty.generatedAt, events: 2, byStatus: [{ key: 'COMPLETED', count: 2 }], participants: 30, uniqueParticipants: 20, repeatParticipants: 10, averageEventSize: 15, revenue: [{ currency: 'RUB', amount: 2500 }] })))
  render(<MemoryRouter initialEntries={['/org/org']}><Routes><Route path="/org/:orgId" element={<OrganizationAnalyticsPage />} /></Routes></MemoryRouter>)
  await screen.findByRole('heading', { name: 'Волна: аналитика' })
  expect(screen.getByText('Повторные участники')).toBeInTheDocument(); expect(screen.getByText('Выручка по оплатам')).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: 'Завершено' })).toBeInTheDocument()
})
it('provides organization analytics links to platform administrators', async () => {
  authFlags.isPlatformAdmin = true
  server.use(http.get('*/api/v1/organizations', () => HttpResponse.json([{ id: 'org', name: 'Волна' }])))
  render(<MemoryRouter><OrganizationsAnalyticsListPage /></MemoryRouter>)
  expect(await screen.findByRole('link', { name: 'Открыть аналитику' })).toHaveAttribute('href', '/organizer/organizations/org/analytics')
})

it('offers links with an access explanation on each card when membership roles are unavailable', async () => {
  server.use(http.get('*/api/v1/organizations', () => HttpResponse.json([{ id: 'org', name: 'Волна' }, { id: 'other', name: 'Чайка' }])))
  render(<MemoryRouter><OrganizationsAnalyticsListPage /></MemoryRouter>)
  await screen.findByRole('heading', { name: 'Волна' })
  for (const [name, id] of [['Волна', 'org'], ['Чайка', 'other']]) {
    const card = screen.getByRole('heading', { name }).closest('section')!
    expect(within(card).getByText('Доступно владельцам и администраторам организации')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Открыть аналитику' })).toHaveAttribute('href', `/organizer/organizations/${id}/analytics`)
  }
})
it('uses the membership access explanation for a forbidden organization', async () => {
  server.use(http.get('*/api/v1/organizations/org/analytics', () => HttpResponse.json({ message: 'Нет доступа' }, { status: 403 })))
  render(<MemoryRouter initialEntries={['/org/org']}><Routes><Route path="/org/:orgId" element={<OrganizationAnalyticsPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('Доступно владельцам и администраторам организации')).toBeInTheDocument()
  expect(screen.queryByText('Уникальные участники')).not.toBeInTheDocument()
})
