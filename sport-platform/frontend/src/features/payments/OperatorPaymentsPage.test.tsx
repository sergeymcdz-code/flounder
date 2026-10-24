import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { OperatorPaymentsPage } from './OperatorPaymentsPage'
import { createFixtures } from '../../mocks/fixtures'
import { getRegistrations } from '../../api/registrations.api'

vi.mock('../../api/registrations.api', () => ({ getRegistrations: vi.fn() }))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: { accessToken: 'test' } }) }))
const base = { ...createFixtures().registrations[0], status: 'SUBMITTED' as const }
const rows = [
  { ...base, participantFullName: 'Иванова Анна Сергеевна', participantBirthYear: 2012, participantClubName: 'Волна', eventTitle: 'Кубок Москвы', eventStartAt: '2026-10-24T10:00:00Z', feeAmount: 1500, paymentStatus: 'PENDING' },
  { ...base, id: '22222222-2222-4222-8222-222222222222', eventId: '33333333-3333-4333-8333-333333333333', participantFullName: 'Петров Иван', eventTitle: 'Первенство области', eventStartAt: '2026-11-01T10:00:00Z' },
]
beforeEach(() => vi.mocked(getRegistrations).mockResolvedValue(rows))
it('renders participant and event context, amounts and statuses without UUID text', async () => {
  const { container } = render(<MemoryRouter><OperatorPaymentsPage /></MemoryRouter>)
  expect(await screen.findByText('Иванова Анна Сергеевна')).toBeInTheDocument()
  expect(screen.getByText('2012 г. р. · Волна')).toBeInTheDocument()
  expect(screen.getAllByText('Кубок Москвы · 24.10.2026')).toHaveLength(2)
  expect(screen.getByText(/1\s500,00/)).toBeInTheDocument()
  expect(screen.getByText('Ожидает')).toBeInTheDocument()
  expect(container.textContent).not.toMatch(/[a-f0-9]{8}-[a-f0-9-]{27}/i)
  expect(screen.getByRole('link', { name: /Открыть заявку: Иванова/ })).toHaveAttribute('href', `/operator/registrations/${base.id}`)
})
it('combines case-insensitive search with the event filter and handles empty results', async () => {
  render(<MemoryRouter><OperatorPaymentsPage /></MemoryRouter>)
  await screen.findByText('Иванова Анна Сергеевна')
  const search = screen.getByRole('searchbox')
  await userEvent.type(search, '  иВаНоВа ')
  expect(screen.queryByText('Петров Иван')).not.toBeInTheDocument()
  await userEvent.selectOptions(screen.getByLabelText('Соревнование'), rows[1].eventId)
  expect(screen.getByText(/Ничего не найдено/)).toBeInTheDocument()
  await userEvent.clear(search)
  expect(screen.getByText('Петров Иван')).toBeInTheDocument()
  await userEvent.selectOptions(screen.getByLabelText('Соревнование'), '')
  await userEvent.type(search, 'кубок')
  expect(screen.getByText('Иванова Анна Сергеевна')).toBeInTheDocument()
  expect(screen.queryByText('Петров Иван')).not.toBeInTheDocument()
})
it('shows an empty state', async () => {
  vi.mocked(getRegistrations).mockResolvedValue([])
  render(<MemoryRouter><OperatorPaymentsPage /></MemoryRouter>)
  expect(await screen.findByText('Заявок пока нет.')).toBeInTheDocument()
})
it('shows a list loading failure', async () => {
  vi.mocked(getRegistrations).mockRejectedValueOnce(new Error('network'))
  render(<MemoryRouter><OperatorPaymentsPage /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось загрузить список заявок.')
})
