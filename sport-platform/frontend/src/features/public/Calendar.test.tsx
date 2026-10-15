import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { server } from '../../test/setup'
import { CalendarPage } from './CalendarPage'
import { PublicOrganizationPage } from './PublicOrganizationPage'
import type { CalendarCard } from './calendar.types'
const card: CalendarCard = { slug: 'cup', title: 'Кубок Самары', startDate: '2026-10-20', endDate: '2026-10-20', city: 'Самара', venueName: 'Волна', organization: { name: 'Клуб Волна', publicSlug: 'volna', hasLogo: false }, status: 'PUBLISHED', registrationOpen: false, registrationCloseAt: null, disciplinesCount: 2, participantsCount: 30, hasResults: true }
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
