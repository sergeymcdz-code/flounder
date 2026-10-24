import { render, screen, fireEvent, waitFor, within, act } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, expect, it, vi } from 'vitest'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { calendarApi } from '../../api/calendar.api'
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
  fireEvent.click(screen.getByRole('button', { name: 'Самара' }))
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
  expect(screen.getByRole('heading', { name: 'Соревнования', level: 1 })).toBeInTheDocument()
  expect(screen.getByText('Календарь стартов')).toBeInTheDocument()
  expect(document.title).toBe('Соревнования · SportGate')
  expect(screen.queryByText(/плавани/i)).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Вид спорта/ })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Прошедшие' }))
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('status')).toBe('past'))
  fireEvent.change(screen.getByLabelText('Поиск по названию'), { target: { value: 'Кубок' } })
  expect(screen.queryByRole('button', { name: 'Найти' })).not.toBeInTheDocument()
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('q')).toBe('Кубок'))
  expect(await screen.findByText('Соревнований не найдено')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Сбросить фильтры' })).toBeInTheDocument()
})
it('renders organization contacts, results, metadata and archive links', async () => {
  server.use(http.get('*/api/v1/public/organizations/volna', () => HttpResponse.json({ name: 'Клуб Волна', publicSlug: 'volna', city: 'Самара', description: 'Плаваем вместе', website: 'https://example.org', publicEmail: 'club@example.org', publicPhone: '+7 (900) 123-45-67', hasLogo: false, stats: { eventsTotal: 12, eventsUpcoming: 1, participantsTotal: 400 }, upcoming: [card], past: [{ ...card, slug: 'past', title: 'Прошедший кубок' }] })))
  const view = render(<MemoryRouter initialEntries={['/o/volna']}><Routes><Route path="/o/:slug" element={<PublicOrganizationPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByRole('heading', { name: 'Клуб Волна' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'club@example.org' })).toHaveAttribute('href', 'mailto:club@example.org')
  expect(screen.getByRole('link', { name: '+7 (900) 123-45-67' })).toHaveAttribute('href', 'tel:+79001234567')
  expect(screen.getByRole('link', { name: 'Все прошедшие старты →' })).toHaveAttribute('href', '/?organization=volna&status=past')
  expect(document.title).toBe('Клуб Волна · SportGate')
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
  expect(nav.getByRole('button', { name: 'Выйти' })).toBeInTheDocument()
  expect(await screen.findByRole('heading', { name: 'Соревнования' })).toBeInTheDocument()
})
it('directory offers login and registration to guests', () => {
  directory()
  const nav = within(screen.getByRole('navigation', { name: 'Основная навигация' }))
  expect(nav.getByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login')
  expect(nav.getByRole('link', { name: 'Регистрация' })).toHaveAttribute('href', '/register')
  expect(nav.queryByRole('link', { name: 'Мои заявки' })).not.toBeInTheDocument()
  expect(nav.queryByRole('link', { name: 'Мои соревнования' })).not.toBeInTheDocument()
})
it('participant logout returns the directory to guest navigation', async () => {
  directory(['participant'])
  fireEvent.click(screen.getByRole('button', { name: 'Выйти' }))
  expect(await screen.findByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login')
  expect(screen.queryByRole('link', { name: 'Мои заявки' })).not.toBeInTheDocument()
})
it('registration deadline uses the venue timezone only while registration is open', () => {
  const datedCard = { ...card, registrationCloseAt: '2026-10-19T20:30:00Z', venueTimezone: 'Europe/Samara' }
  const view = render(<MemoryRouter><CalendarCards items={[{ ...datedCard, registrationOpen: true }]} /></MemoryRouter>)
  expect(screen.getByText('Регистрация до 20.10')).toBeInTheDocument()
  view.rerender(<MemoryRouter><CalendarCards items={[{ ...datedCard, status: 'COMPLETED', registrationOpen: false }]} /></MemoryRouter>)
  expect(screen.getByText('Регистрация закрыта')).toBeInTheDocument()
  expect(screen.queryByText(/Регистрация до/)).not.toBeInTheDocument()
  view.rerender(<MemoryRouter><CalendarCards items={[{ ...card, registrationOpen: true, registrationCloseAt: null }]} /></MemoryRouter>)
  expect(screen.getByText('Регистрация открыта')).toBeInTheDocument()
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
  const nav = within(screen.getByRole('navigation', { name: 'Основная навигация' }))
  expect(nav.getByRole('link', { name: 'Календарь' })).toHaveAttribute('href', '/')
  expect(await nav.findByRole('link', { name: 'Страница организации' })).toHaveAttribute('href', '/o/volna')
  expect(screen.getAllByText(/Организатор:/)).toHaveLength(1)
  expect(screen.getByRole('navigation', { name: 'Разделы соревнования' })).toBeInTheDocument()
})

it('debounces title input for 300 ms without losing focus', async () => {
  const load = vi.spyOn(calendarApi, 'calendar').mockResolvedValue({ items: [card], page: 0, size: 20, total: 1, cities: ['Самара'] })
  calendar()
  await screen.findByText(card.title)
  vi.useFakeTimers()
  try {
    const input = screen.getByRole('searchbox')
    input.focus()
    fireEvent.change(input, { target: { value: 'Ку' } })
    act(() => { vi.advanceTimersByTime(200) })
    fireEvent.change(input, { target: { value: 'Кубок' } })
    act(() => { vi.advanceTimersByTime(299) })
    expect(load).toHaveBeenCalledTimes(1)
    await act(async () => { vi.advanceTimersByTime(1) })
    expect(load).toHaveBeenCalledTimes(2)
    expect(load.mock.calls[1][0].get('q')).toBe('Кубок')
    expect(input).toHaveFocus()
    expect(input).toHaveValue('Кубок')
  } finally { vi.useRealTimers(); load.mockRestore() }
})

it('resets search, city, period and pagination while preserving organization scope', async () => {
  const requests: URL[] = []
  server.use(http.get('*/api/v1/public/calendar', ({ request }) => {
    requests.push(new URL(request.url))
    return HttpResponse.json({ items: [], page: 0, size: 20, total: 0, cities: ['Самара'] })
  }))
  render(<MemoryRouter initialEntries={['/?q=Кубок&city=Самара&status=past&page=3&organization=volna']}><CalendarPage /></MemoryRouter>)
  await screen.findByText('Соревнований не найдено')
  expect(screen.getByRole('searchbox')).toHaveValue('Кубок')
  expect(screen.getByRole('button', { name: 'Самара' })).toHaveAttribute('aria-pressed', 'true')
  fireEvent.click(screen.getByRole('button', { name: 'Сбросить фильтры' }))
  await waitFor(() => expect(requests).toHaveLength(2))
  expect(Object.fromEntries(requests[1].searchParams)).toEqual({ organization: 'volna', page: '0', size: '20' })
  expect(screen.getByRole('searchbox')).toHaveValue('')
  expect(screen.getByRole('button', { name: 'Ближайшие' })).toHaveAttribute('aria-pressed', 'true')
})

it.each([
  ['network', 'Нет связи. Проверьте подключение к интернету.'],
  ['server', 'Сервер ответил ошибкой (503).'],
  ['missing', 'Данные не найдены.'],
])('shows loading, a useful %s error and retries', async (mode, message) => {
  let resolve!: () => void
  let failed = false
  server.use(http.get('*/api/v1/public/calendar', async () => {
    if (!failed) {
      failed = true
      await new Promise<void>(done => { resolve = done })
      return mode === 'network' ? HttpResponse.error() : HttpResponse.json({ message: 'Попробуйте позже.' }, { status: mode === 'missing' ? 404 : 503 })
    }
    return HttpResponse.json({ items: [card], page: 0, size: 20, total: 1, cities: [] })
  }))
  calendar()
  expect(screen.getByRole('status', { name: 'Загружаем соревнования…' })).toBeInTheDocument()
  await waitFor(() => expect(resolve).toBeTypeOf('function'))
  await act(async () => { resolve() })
  expect(await screen.findByRole('alert')).toHaveTextContent(message)
  fireEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText(card.title)).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('uses event status, a single title destination, conditional actions and missing dates', () => {
  render(<MemoryRouter><CalendarCards grouped items={[
    { ...card, status: 'LIVE', hasResults: true, registrationOpen: false },
    { ...card, slug: 'pending', title: 'Будущий старт', startDate: null, endDate: null, city: null, organization: null, hasResults: false, status: 'PUBLISHED' },
  ]} /></MemoryRouter>)
  const live = screen.getByRole('heading', { name: card.title }).closest('article')!
  expect(within(live).getByText('Идёт')).toHaveAttribute('data-status', 'LIVE')
  expect(within(live).getByRole('link', { name: card.title })).toHaveAttribute('href', '/e/cup')
  expect(within(live).getByRole('link', { name: 'Табло' })).toHaveAttribute('href', '/e/cup/live')
  expect(within(live).getByRole('link', { name: 'Результаты' })).toHaveAttribute('href', '/e/cup/results')
  expect(within(live).queryByRole('link', { name: /Подробнее/ })).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Дата уточняется' })).toBeInTheDocument()
  expect(within(screen.getByRole('heading', { name: 'Будущий старт' }).closest('article')!).queryByRole('link', { name: 'Табло' })).not.toBeInTheDocument()
})

it('offers past events when there are no search or city filters and preserves organization scope', async () => {
  const requests: URL[] = []
  server.use(http.get('*/api/v1/public/calendar', ({ request }) => {
    requests.push(new URL(request.url))
    return HttpResponse.json({ items: [], page: 0, size: 20, total: 0, cities: [] })
  }))
  render(<MemoryRouter initialEntries={['/?organization=volna&page=3']}><CalendarPage /></MemoryRouter>)
  expect(await screen.findByText('Пока нет соревнований')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Сбросить фильтры' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Показать прошедшие' }))
  await waitFor(() => expect(requests).toHaveLength(2))
  expect(Object.fromEntries(requests[1].searchParams)).toEqual({ organization: 'volna', status: 'past', page: '0', size: '20' })
})
