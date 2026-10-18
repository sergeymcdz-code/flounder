import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { PaymentHistoryDialog } from '../payments/PaymentHistoryDialog'
import { OrganizerEntriesPage } from './OrganizerEntriesPage'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', disciplines: [{ id: 'discipline', name: '50 м' }] },
  registrations: [{ id: 'registration', eventDisciplineId: 'discipline', status: 'PAID', paymentStatus: 'PAID', feeAmount: 500, feeCurrency: 'RUB', participantSnapshot: { lastName: 'Тестов', firstName: 'Иван' }, seedTimeMs: 32000 }],
  heats: [], generationResults: {}, accessToken: 'test', busy: false, clearError: vi.fn(), run: vi.fn(async (action: () => Promise<unknown>) => { await action(); return true }), registerDialog: vi.fn(() => vi.fn()), error: '',
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
it.each([['PENDING', 'Ожидает оплаты'], ['PAID', 'Оплачено'], ['WAIVED', 'Освобождён'], ['REFUNDED', 'Возврат']])('displays the %s payment badge', (payment, label) => {
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
  await user.click(screen.getByLabelText('Выбрать заявку Тестов Иван'))
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
it.each(['PENDING', 'NOT_REQUIRED'])('offers payment for a confirmed entry with %s payment', paymentStatus => {
  context.registrations[0].status = 'CONFIRMED'
  context.registrations[0].paymentStatus = paymentStatus
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByRole('button', { name: 'Отметить оплату…' })).toBeVisible()
  expect(screen.getByText('Снять').closest('details')).not.toBeNull()
})
it('confirms bulk review, runs sequentially, retains failed selection and refreshes once', async () => {
  const user = (await import('@testing-library/user-event')).default.setup()
  const { reviewRegistration } = await import('../../api/registrations.api')
  const review = vi.spyOn(await import('../../api/registrations.api'), 'reviewRegistration')
  context.registrations = ['Первый', 'Второй'].map((name, index) => ({ ...context.registrations[0], id: `r${index}`, status: 'SUBMITTED', participantSnapshot: { lastName: name, firstName: 'Иван' } }))
  const refresh = vi.fn().mockResolvedValue(undefined)
  Object.assign(context, { refresh })
  let release: () => void = () => {}
  review.mockImplementationOnce(() => new Promise(resolve => { release = () => resolve({} as Awaited<ReturnType<typeof reviewRegistration>>) })).mockRejectedValueOnce(new Error('Нет доступа'))
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  await user.click(screen.getByRole('checkbox', { name: 'Выбрать все видимые заявки' }))
  const bar = document.querySelector('.fl-bulk-bar')!
  const { within, waitFor } = await import('@testing-library/react')
  await user.click(within(bar as HTMLElement).getByRole('button', { name: 'Подтвердить' }))
  expect(review).not.toHaveBeenCalled()
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Применить' }))
  expect(review).toHaveBeenCalledTimes(1)
  release()
  await waitFor(() => expect(review).toHaveBeenCalledTimes(2))
  await screen.findByText(/Второй Иван: Нет доступа/)
  expect(refresh).toHaveBeenCalledTimes(1)
  expect(screen.getByRole('checkbox', { name: 'Выбрать заявку Первый Иван' })).not.toBeChecked()
  expect(screen.getByRole('checkbox', { name: 'Выбрать заявку Второй Иван' })).toBeChecked()
  review.mockRestore()
})

it('includes confirmed zero-fee entries in bulk payment', async () => {
  const user = (await import('@testing-library/user-event')).default.setup()
  const { within, waitFor } = await import('@testing-library/react')
  bulkPayment.mockClear().mockResolvedValue({ updated: 1, skipped: [] })
  context.registrations = [{ ...context.registrations[0], id: 'zero-fee', status: 'CONFIRMED', paymentStatus: 'NOT_REQUIRED' }]
  Object.assign(context, { refresh: vi.fn().mockResolvedValue(undefined) })
  try {
    render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
    await user.click(screen.getByRole('checkbox', { name: 'Выбрать все видимые заявки' }))
    const bar = document.querySelector('.fl-bulk-bar') as HTMLElement
    await user.click(within(bar).getByRole('button', { name: 'Отметить оплату выбранных (1)' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Заявок: 1')
    expect(bulkPayment).not.toHaveBeenCalled()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Подтвердить' }))
    await waitFor(() => expect(bulkPayment).toHaveBeenCalledWith('event', expect.objectContaining({ registrationIds: ['zero-fee'] }), 'test'))
  } finally { bulkPayment.mockClear() }
})
