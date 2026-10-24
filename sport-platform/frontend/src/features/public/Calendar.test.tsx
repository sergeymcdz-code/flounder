import { render, screen, fireEvent, waitFor, within, act } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, expect, it } from 'vitest'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { DirectoryLayout } from './DirectoryLayout'
import { PublicLayout } from './PublicLayout'
import { PublicEventPage } from './EventPage'
import { demoEvent } from '../../mocks/demo-data'
import { CalendarCards } from './CalendarCards'
import { CalendarPage } from './CalendarPage'
import { PublicOrganizationPage } from './PublicOrganizationPage'
import type { CalendarCard } from './calendar.types'
const card: CalendarCard = { slug: 'cup', title: 'Кубок Самары', startDate: '2026-10-20', endDate: '2026-10-20', city: 'Самара', venueName: 'Волна', organization: { name: 'Клуб Волна', publicSlug: 'volna', hasLogo: false }, status: 'PUBLISHED', registrationOpen: false, registrationCloseAt: null, disciplinesCount: 2, participantsCount: 30, hasResults: true }
afterEach(() => { act(() => setAuthSession(null)) })
function calendar() { render(<MemoryRouter><CalendarPage /></MemoryRouter>) }
it('groups cards by month, filters by city and resets pagination', async () => {
  const requests: URL[] = []
  server.use(http.get('*/api/v1/public/calendar', ({ request }) => {
    const url = new URL(request.url); requests.push(url)
    const city = url.searchParams.get('city')
    const page = url.searchParams.get('page')
    return HttpResponse.json({ items: page === '1' ? [{ ...card, slug: 'november', title: 'Ноябрьский кубок', startDate: '2026-11-20' }] : [{ ...card, title: city ? 'Городской старт' : card.title }], page: Number(page), size: 20, total: city ? 1 : 2, cities: ['Самара', 'Москва'] })
  }))
  calendar()
  expect(await screen.findByRole('heading', { name: 'Октябрь 2026' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Клуб Волна' })).toHaveAttribute('href', '/o/volna')
  fireEvent.click(screen.getByRole('button', { name: 'Показать ещё' }))
  expect(await screen.findByRole('heading', { name: 'Ноябрь 2026' })).toBeInTheDocument()
  expect(requests.map(r => r.searchParams.get('page'))).toEqual(['0', '1'])
  fireEvent.change(screen.getByLabelText('Город'), { target: { value: 'Самара' } })
  expect(await screen.findByText('Городской старт')).toBeInTheDocument()
  expect(screen.queryByText('Ноябрьский кубок')).not.toBeInTheDocument()
  expect(requests.at(-1)?.searchParams.get('city')).toBe('Самара')
  expect(requests.at(-1)?.searchParams.get('page')).toBe('0')
})
it('shows the empty state and sends past and title filters', async () => {
  const requests: URL[] = []
  server.use(http.get('*/api/v1/public/calendar', ({ request }) => {
    requests.push(new URL(request.url))
    return HttpResponse.json({ items: [], page: 0, size: 20, total: 0, cities: [] })
  }))
  calendar()
  expect(await screen.findByText('Пока нет соревнований')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Прошедшие' }))
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('status')).toBe('past'))
  fireEvent.change(screen.getByLabelText('Поиск по названию'), { target: { value: 'Кубок' } })
  fireEvent.click(screen.getByRole('button', { name: 'Найти' }))
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('q')).toBe('Кубок'))
})
it('renders organization contacts, results, metadata and archive links', async () => {
  server.use(http.get('*/api/v1/public/organizations/volna', () => HttpResponse.json({ name: 'Клуб Волна', publicSlug: 'volna', city: 'Самара', description: 'Плаваем вместе', website: 'https://example.org', publicEmail: 'club@example.org', publicPhone: '+7 (900) 123-45-67', hasLogo: false, stats: { eventsTotal: 12, eventsUpcoming: 1, participantsTotal: 400 }, upcoming: [card], past: [{ ...card, slug: 'past', title: 'Прошедший кубок' }] })))
  const view = render(<MemoryRouter initialEntries={['/o/volna']}><Routes><Route path="/o/:slug" element={<PublicOrganizationPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByRole('heading', { name: 'Клуб Волна' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'club@example.org' })).toHaveAttribute('href', 'mailto:club@example.org')
  expect(screen.getByRole('link', { name: '+7 (900) 123-45-67' })).toHaveAttribute('href', 'tel:+79001234567')
  expect(screen.getByRole('link', { name: 'Все прошедшие старты →' })).toHaveAttribute('href', '/?organization=volna&status=past')
  await waitFor(() => expect(document.title).toBe('Клуб Волна · SportGate'))
  expect(document.querySelector('meta[property="og:description"]')).toHaveAttribute('content', 'Плаваем вместе')
  view.unmount()
  expect(document.querySelector('meta[property="og:description"]')).toBeNull()
})
it('hides organization details when its slug is missing', async () => {
  server.use(http.get('*/api/v1/public/organizations/missing', () => HttpResponse.json({ message: 'Организация не найдена' }, { status: 404 })))
  render(<MemoryRouter initialEntries={['/o/missing']}><Routes><Route path="/o/:slug" element={<PublicOrganizationPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByRole('heading', { name: 'Организация не найдена' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'К календарю' })).toHaveAttribute('href', '/')
})

it.each([
  ['participant', 'Мои заявки', '/my/registrations', '/my/registrations'],
  ['organizer', 'Мои соревнования', '/organizer/events', '/organizer/events'],
  ['operator', 'Мои соревнования', '/organizer/events', '/organizer/events'],
  ['platform_admin', 'Администрирование', '/admin/users', '/organizer/events'],
  ['judge', 'Судейство', '/operator/results', '/operator/results'],
])('directory navigation sends %s to the correct workspace', async (role, label, path, home) => {
  directory([role])
  expect(screen.getByRole('link', { name: 'На главную' })).toHaveAttribute('href', home)
  const nav = within(screen.getByRole('navigation', { name: 'Основная навигация' }))
  expect(nav.getByRole('link', { name: label })).toHaveAttribute('href', path)
  expect(nav.queryByRole('link', { name: 'Кабинет' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByText('Профиль').closest('summary')!)
  expect(await screen.findByRole('button', { name: 'Выйти' })).toBeInTheDocument()
  expect(await screen.findByRole('heading', { name: 'Соревнования по плаванию' })).toBeInTheDocument()
})
it('directory offers the unified login action to guests', () => {
  directory()
  const nav = within(screen.getByRole('navigation', { name: 'Основная навигация' }))
  expect(screen.getByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login')
  expect(nav.queryByRole('link', { name: 'Мои заявки' })).not.toBeInTheDocument()
  expect(nav.queryByRole('link', { name: 'Мои соревнования' })).not.toBeInTheDocument()
})
it('participant logout returns the directory to guest navigation', async () => {
  directory(['participant'])
  fireEvent.click(screen.getByText('Профиль').closest('summary')!)
  fireEvent.click(await screen.findByRole('button', { name: 'Выйти' }))
  expect(await screen.findByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login')
  expect(screen.queryByRole('link', { name: 'Мои заявки' })).not.toBeInTheDocument()
})
it('registration deadline uses the venue timezone across midnight', () => {
  render(<MemoryRouter><CalendarCards items={[{ ...card, registrationOpen: true, registrationCloseAt: '2026-10-19T20:30:00Z', venueTimezone: 'Europe/Samara' }]} /></MemoryRouter>)
  expect(screen.getByText('Регистрация открыта до 20.10')).toBeInTheDocument()
})
it('a failed next page preserves cards and can retry only that page', async () => {
  const pages: string[] = []
  let failed = false
  server.use(http.get('*/api/v1/public/calendar', ({ request }) => {
    const page = new URL(request.url).searchParams.get('page') ?? '0'; pages.push(page)
    if (page === '1' && !failed) { failed = true; return HttpResponse.json({ message: 'Временная ошибка' }, { status: 503 }) }
    return HttpResponse.json({ items: [{ ...card, slug: page === '1' ? 'second' : 'cup', title: page === '1' ? 'Второй старт' : card.title }], page: Number(page), size: 20, total: 2, cities: ['Самара'] })
  }))
  calendar()
  expect(await screen.findByText(card.title)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Показать ещё' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Временная ошибка')
  expect(screen.getByText(card.title)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Повторить загрузку' }))
  expect(await screen.findByText('Второй старт')).toBeInTheDocument()
  expect(pages).toEqual(['0', '1', '1'])
})

function directory(roles?: string[]) {
  setAuthSession(roles ? { accessToken: `test.${btoa(JSON.stringify({ roles }))}.test`, tokenType: 'Bearer', expiresAt: Date.now() + 3600000 } : null)
  server.use(http.get('*/api/v1/public/calendar', () => HttpResponse.json({ items: [card], page: 0, size: 20, total: 1, cities: ['Самара'] })))
  return render(<MemoryRouter><AuthProvider><Routes><Route element={<DirectoryLayout />}><Route index element={<CalendarPage />} /></Route></Routes></AuthProvider></MemoryRouter>)
}

it('public event has exactly one organizer row linking to its public page', () => {
  const event = { ...demoEvent, organization: { name: 'Клуб Волна', publicSlug: 'volna', hasLogo: false } }
  render(<MemoryRouter><Routes><Route element={<Outlet context={{ event }} />}><Route index element={<PublicEventPage />} /></Route></Routes></MemoryRouter>)
  expect(screen.getAllByText(/Организатор:/)).toHaveLength(1)
  expect(screen.getByRole('link', { name: 'Клуб Волна' })).toHaveAttribute('href', '/o/volna')
})

it('public event layout preserves role home and links to the calendar and organization', async () => {
  setAuthSession({ accessToken: `test.${btoa(JSON.stringify({ roles: ['participant'] }))}.test`, tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })
  server.use(http.get('*/api/v1/public/events/demo', () => HttpResponse.json({ ...demoEvent, organization: { name: 'Клуб Волна', publicSlug: 'volna', hasLogo: false } })))
  render(<MemoryRouter initialEntries={['/e/demo']}><AuthProvider><Routes><Route path="/e/:slug" element={<PublicLayout />}><Route index element={<PublicEventPage />} /></Route></Routes></AuthProvider></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'На главную' })).toHaveAttribute('href', '/my/registrations')
  const nav = within(screen.getByRole('navigation', { name: 'Навигация события' }))
  expect(nav.getByRole('link', { name: '← Календарь' })).toHaveAttribute('href', '/')
  expect(await nav.findByRole('link', { name: 'Страница организации' })).toHaveAttribute('href', '/o/volna')
  expect(screen.getAllByText(/Организатор:/)).toHaveLength(1)
  expect(screen.getByRole('navigation', { name: 'Разделы соревнования' })).toBeInTheDocument()
})
