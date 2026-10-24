import { render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { expect, it } from 'vitest'
import { demoEvent } from '../../mocks/demo-data'
import type { PublicEvent } from './public.types'
import { PublicEventPage } from './EventPage'

function renderEvent(changes: Partial<PublicEvent> = {}) {
  const event = structuredClone({ ...demoEvent, ...changes })
  return render(<MemoryRouter><Routes><Route element={<Outlet context={{ event }} />}><Route index element={<PublicEventPage />} /></Route></Routes></MemoryRouter>)
}

it.each([
  ['REGISTRATION_OPEN', true, 'Подать заявку', 'register'],
  ['LIVE', true, 'Открыть табло', 'live'],
  ['COMPLETED', true, 'Результаты', 'results'],
  ['FINISHED', false, 'Результаты', 'results'],
  ['ARCHIVED', false, 'Результаты', 'results'],
  ['REGISTRATION_CLOSED', false, 'Стартовый протокол', 'start-list'],
  ['PUBLISHED', false, 'Стартовый протокол', 'start-list'],
] as const)('chooses one primary action for %s', (status, registrationOpen, label, path) => {
  const { container } = renderEvent({ status, registrationOpen, publicSlug: 'кубок' })
  const primary = container.querySelectorAll('.fl-btn-primary')
  expect(primary).toHaveLength(1)
  expect(primary[0]).toHaveTextContent(label)
  expect(primary[0]).toHaveAttribute('href', `/e/${encodeURIComponent('кубок')}/${path}`)
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
})

it('keeps organization, capacity and poster in the overview', () => {
  renderEvent({ organization: { name: 'Клуб Волна', publicSlug: 'волна', hasLogo: false }, maxParticipants: 20, participantsCount: 20 })
  expect(screen.getByRole('link', { name: 'Клуб Волна' })).toHaveAttribute('href', `/o/${encodeURIComponent('волна')}`)
  expect(screen.getByRole('link', { name: 'На странице организации →' })).toHaveAttribute('href', `/o/${encodeURIComponent('волна')}`)
  expect(screen.getByText('20 из 20')).toBeInTheDocument()
  expect(screen.getByText('Мест нет, запись в лист ожидания.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Подать заявку' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'QR-плакат A4' })).toHaveAttribute('href', '/e/demo/poster')
})

it('sorts sessions and disciplines without mutating the event, including unassigned items', () => {
  const event = structuredClone(demoEvent)
  event.sessions.reverse()
  event.disciplines.reverse()
  event.disciplines[0].sessionId = null
  event.disciplines[0].unitsPublished = false
  const original = structuredClone(event)
  render(<MemoryRouter><Routes><Route element={<Outlet context={{ event }} />}><Route index element={<PublicEventPage />} /></Route></Routes></MemoryRouter>)
  expect(screen.getAllByRole('heading', { level: 3 }).map(e => e.textContent)).toEqual([
    '11:00 · Сессия 1 · утро', '15:00 · Сессия 2 · день', 'Без сессии',
  ])
  expect(screen.getByText('Стартовый протокол ещё не опубликован')).toBeInTheDocument()
  expect(event).toEqual(original)
})

it('shows an empty program and missing venue without inventing data', () => {
  renderEvent({ sessions: [], disciplines: [], venue: null, organization: null, registrationOpen: false, registrationCloseAt: null })
  expect(screen.getByText('Программа пока не опубликована')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть расписание' })).toHaveAttribute('href', '/e/demo/schedule')
  expect(screen.getByText('Регистрация закрыта.')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Подать заявку' })).not.toBeInTheDocument()
})
