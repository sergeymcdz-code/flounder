import userEvent from '@testing-library/user-event'
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
import { MyRegistrationsPage } from './MyRegistrationsPage'
it('shows competition, discipline and timezone with every added registration status', async () => {
  const fixtures = createFixtures()
  server.use(http.get('*/api/v1/registrations/me', () => HttpResponse.json(['WITHDRAWN', 'ADMITTED', 'UNDER_REVIEW'].map((status, index) => ({ ...fixtures.registrations[0], id: String(index), status, eventTitle: 'Кубок Самары', disciplineName: '50 м вольный стиль', eventStartAt: '2026-10-17T06:00:00Z', venueTimezone: 'Europe/Samara', paymentStatus: 'PENDING' })))), http.get('*/api/v1/auth/me', () => HttpResponse.json({ email: 'parent@example.test' })))
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  render(<MemoryRouter><AuthProvider><MyRegistrationsPage /></AuthProvider></MemoryRouter>)
  expect(await screen.findAllByText('Кубок Самары')).toHaveLength(3)
  for (const label of ['Снята', 'Допущена', 'На рассмотрении']) expect(screen.getByText(label)).toBeInTheDocument()
  expect(screen.getAllByText(/17.10.2026 10:00/)).toHaveLength(3)
  expect(screen.queryByText(/ID дисциплины/)).not.toBeInTheDocument()
  expect(screen.getByText('Документы спортсмена').closest('details')).not.toHaveAttribute('open')
  setAuthSession(null)
})

it('treats a waived confirmed entry as paid and waits for the start list', async () => {
  const fixtures = createFixtures()
  server.use(http.get('*/api/v1/registrations/me', () => HttpResponse.json([{ ...fixtures.registrations[0], status: 'CONFIRMED', paymentStatus: 'WAIVED' }])), http.get('*/api/v1/auth/me', () => HttpResponse.json({ email: 'parent@example.test' })))
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  render(<MemoryRouter><AuthProvider><MyRegistrationsPage /></AuthProvider></MemoryRouter>)
  expect(await screen.findByText('Оплачено', { selector: '.fl-status' })).toBeInTheDocument()
  expect(screen.getByText(/Ждите стартовый протокол/)).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: /Оплатить/ })).not.toBeInTheDocument()
  setAuthSession(null)
})

it('confirms withdrawal, returns focus on cancel, and preserves context on success', async () => {
  const row = { ...createFixtures().registrations[0], eventTitle: 'Кубок Самары', status: 'SUBMITTED', paymentStatus: 'NOT_REQUIRED' }
  let mutations = 0
  server.use(http.get('*/api/v1/registrations/me', () => HttpResponse.json([row])), http.get('*/api/v1/auth/me', () => HttpResponse.json({ email: 'parent@example.test' })), http.post('*/api/v1/registrations/:id/withdraw', () => { mutations++; return HttpResponse.json({ ...row, status: 'WITHDRAWN' }) }))
  setAuthSession({ accessToken: 'test', expiresAt: Date.now() + 60000, tokenType: 'Bearer' })
  render(<MemoryRouter><AuthProvider><MyRegistrationsPage /></AuthProvider></MemoryRouter>)
  await userEvent.click(await screen.findByText('Подробнее'))
  const trigger = screen.getByRole('button', { name: 'Отозвать заявку' })
  await userEvent.click(trigger)
  expect(screen.getByRole('dialog', { name: 'Отозвать заявку?' })).toBeInTheDocument()
  expect(mutations).toBe(0)
  await userEvent.click(screen.getByRole('button', { name: 'Отмена' }))
  expect(trigger).toHaveFocus()
  await userEvent.click(trigger)
  await userEvent.click(screen.getByRole('dialog').querySelector('button[type="submit"]')!)
  expect(await screen.findByText('Снята')).toBeInTheDocument()
  expect(screen.getByText('Кубок Самары')).toBeInTheDocument()
  expect(mutations).toBe(1)
  setAuthSession(null)
})
