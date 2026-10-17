import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { PaymentHistoryDialog } from '../payments/PaymentHistoryDialog'
import { OrganizerEntriesPage } from './OrganizerEntriesPage'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', disciplines: [{ id: 'discipline', name: '50 м' }] },
  registrations: [{ id: 'registration', eventDisciplineId: 'discipline', status: 'PAID', paymentStatus: 'PAID', feeAmount: 500, feeCurrency: 'RUB', participantSnapshot: { lastName: 'Тестов', firstName: 'Иван' }, seedTimeMs: 32000 }],
  heats: [], generationResults: {}, accessToken: 'test', busy: false, run: vi.fn(async (action: () => Promise<unknown>) => { await action(); return true }), registerDialog: vi.fn(() => vi.fn()), error: '',
}))
vi.mock('./organizer-event-context', async (original) => ({
  ...await original<typeof import('./organizer-event-context')>(),
  useOrganizerEvent: () => context,
}))

it.each(['PAID', 'ADMITTED'])('hides rejection for %s entries', status => {
  context.registrations[0].status = status
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.queryByRole('button', { name: 'Отклонить' })).not.toBeInTheDocument()
})
it.each(['SUBMITTED', 'UNDER_REVIEW', 'WAITLISTED', 'CONFIRMED'])('allows rejection for %s entries', status => {
  context.registrations[0].status = status
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByRole('button', { name: 'Отклонить' })).toBeInTheDocument()
})

const { paymentAction, bulkPayment, paymentHistory } = vi.hoisted(() => ({ paymentAction: vi.fn().mockResolvedValue({}), bulkPayment: vi.fn(), paymentHistory: vi.fn() }))
vi.mock('../../api/registration-payments.api', async original => ({ ...await original<typeof import('../../api/registration-payments.api')>(), paymentAction, bulkPayment, paymentHistory }))
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  context.registrations[0].paymentStatus = 'PENDING'; context.event.status = 'REGISTRATION_OPEN'; paymentAction.mockClear() })
it.each([['PENDING', 'Ожидает'], ['PAID', 'Оплачено'], ['WAIVED', 'Освобождён'], ['REFUNDED', 'Возвращено']])('displays the %s payment badge', (payment, label) => {
  context.registrations[0].paymentStatus = payment
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByText(label, { selector: 'span' })).toBeInTheDocument()
  expect(screen.getByText(/500/)).toBeInTheDocument()
})
it('opens payment details with the frozen fee and submits transfer reference', async () => {
  context.registrations[0].status = 'CONFIRMED'
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Отметить оплату…' }))
  expect(screen.getByRole('dialog', { name: 'Отметить оплату' })).toBeInTheDocument()
  expect(screen.getByLabelText('Сумма')).toHaveValue(500)
  await user.type(screen.getByLabelText('Номер документа'), 'ПП-42')
  await user.click(screen.getByRole('button', { name: 'Подтвердить' }))
  expect(paymentAction).toHaveBeenCalledWith('registration', 'mark-paid', expect.objectContaining({ amount: 500, method: 'TRANSFER', reference: 'ПП-42' }), 'test')
})
it('disables payment changes for archived events', () => {
  context.registrations[0].status = 'CONFIRMED'; context.event.status = 'ARCHIVED'
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByRole('button', { name: 'Отметить оплату…' })).toBeDisabled()
})

it('shows participant names for skipped bulk payments', async () => {
  context.registrations[0].status = 'CONFIRMED'
  bulkPayment.mockResolvedValue({ updated: 0, skipped: [{ id: 'registration', reason: 'Уже оплачена' }] })
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  const user = userEvent.setup()
  await user.click(screen.getByLabelText('Выбрать заявку Тестов'))
  await user.click(screen.getByRole('button', { name: 'Отметить оплату выбранных (1)' }))
  await user.click(screen.getByRole('button', { name: 'Подтвердить' }))
  expect(await screen.findByText('Тестов Иван: Уже оплачена')).toBeInTheDocument()
  expect(screen.queryByText('registration: Уже оплачена')).not.toBeInTheDocument()
})
it('shows the actor name in payment history', async () => {
  paymentHistory.mockResolvedValue([{ id: 'log', action: 'MARK_PAID', performedAt: '2026-10-17T10:00:00Z', performedBy: 'actor', performedByName: 'Иван Петров', amount: 500, currency: 'RUB' }])
  render(<MemoryRouter><PaymentHistoryDialog id="registration" token="test" onClose={() => {}} /></MemoryRouter>)
  expect(await screen.findByText(/Исполнитель: Иван Петров/)).toBeInTheDocument()
})
