import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { OrganizerPaymentsPage } from './OrganizerPaymentsPage'
import { createFixtures } from '../../mocks/fixtures'
import { ApiError } from '../../api/http'
import type { EventContext } from '../organizer/organizer-event-context'
import { paymentSummary, bulkPayment, paymentAction } from '../../api/registration-payments.api'

const state = vi.hoisted(() => ({ context: undefined as EventContext | undefined }))
vi.mock('../organizer/organizer-event-context', () => ({ useOrganizerEvent: () => state.context }))
vi.mock('../../api/registration-payments.api', async original => ({
  ...await original<typeof import('../../api/registration-payments.api')>(),
  paymentSummary: vi.fn(), bulkPayment: vi.fn(), paymentAction: vi.fn(),
}))
const summary = { currency: 'RUB', totals: { due: 1000, paid: 500, pending: 500, waived: 0, refunded: 0 }, byClub: [
  { club: 'Клуб Волна', participants: 2, registrations: 2, due: 1000, paid: 500, pending: 500, pendingRegistrationIds: ['registration'] },
] }
beforeEach(() => {
  const fixture = createFixtures()
  state.context = {
    ...fixture, event: { ...fixture.event, status: 'REGISTRATION_OPEN' }, registrations: [{ ...fixture.registrations[0], id: 'registration', paymentStatus: 'PENDING', feeAmount: 500 }],
    accessToken: 'test', busy: false, error: '', registerDialog: vi.fn(() => vi.fn()),
    run: vi.fn(async (action: () => Promise<unknown>) => { await action(); return true }),
  } as unknown as EventContext
  vi.mocked(paymentSummary).mockReset().mockResolvedValue(structuredClone(summary))
  vi.mocked(bulkPayment).mockReset().mockResolvedValue({ updated: 1, skipped: [] })
  vi.mocked(paymentAction).mockReset().mockResolvedValue({})
})
const show = () => render(<MemoryRouter><OrganizerPaymentsPage /></MemoryRouter>)
it('protects the page when the outlet context is absent (UI-001)', () => {
  state.context = undefined
  show()
  expect(screen.getByRole('alert')).toHaveTextContent('Не удалось загрузить соревнование')
  expect(screen.getByRole('button', { name: 'Повторить' })).toBeEnabled()
  expect(paymentSummary).not.toHaveBeenCalled()
})
it('shows loading, distinguishes a server failure and retries without losing entries', async () => {
  let reject!: (error: Error) => void
  vi.mocked(paymentSummary).mockReturnValueOnce(new Promise((_, no) => { reject = no }))
  show()
  expect(screen.getByRole('status')).toHaveTextContent('Загружаем сводку оплаты')
  reject(new ApiError(503, 'Повторите позже'))
  expect(await screen.findByRole('alert')).toHaveTextContent('Сервер ответил ошибкой (503)')
  expect(screen.getByRole('table', { name: 'Оплата заявок' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByRole('table', { name: 'Взносы и долги по клубам' })).toBeInTheDocument()
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
})
it('shows the empty states with a next step', async () => {
  state.context!.registrations = []
  vi.mocked(paymentSummary).mockResolvedValue({ ...summary, byClub: [] })
  show()
  expect(await screen.findByText('Платежей пока нет')).toBeInTheDocument()
  expect(screen.getByText('Заявок пока нет')).toBeInTheDocument()
  expect(screen.getAllByRole('link', { name: 'Открыть заявки' })).toHaveLength(2)
})
it('confirms bulk payment with the existing contract', async () => {
  show()
  await userEvent.click(await screen.findByRole('button', { name: 'Отметить оплаченными' }))
  const dialog = screen.getByRole('dialog', { name: 'Отметить оплату выбранных' })
  expect(bulkPayment).not.toHaveBeenCalled()
  await userEvent.click(within(dialog).getByRole('button', { name: 'Подтвердить' }))
  await waitFor(() => expect(bulkPayment).toHaveBeenCalledWith(state.context!.event.id, ['registration'], expect.objectContaining({ method: 'CLUB_INVOICE' }), 'test'))
  expect(await within(dialog).findByText('Обновлено: 1')).toBeInTheDocument()
})
it('opens a refund from the row menu and requires a comment', async () => {
  show()
  const table = screen.getByRole('table', { name: 'Оплата заявок' })
  await userEvent.click(within(table).getByRole('button', { name: /Действия оплаты:/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Вернуть оплату' }))
  const dialog = screen.getByRole('dialog', { name: 'Вернуть оплату' })
  expect(within(dialog).getByLabelText('Комментарий')).toBeRequired()
  await userEvent.type(within(dialog).getByLabelText('Комментарий'), 'Ошибочный взнос')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Подтвердить' }))
  await waitFor(() => expect(paymentAction).toHaveBeenCalledWith('registration', 'refund-payment', expect.objectContaining({ comment: 'Ошибочный взнос' }), 'test'))
})
it('shows a reason and prevents payment mutations for archived events', async () => {
  state.context!.event.status = 'ARCHIVED'
  show()
  const bulk = await screen.findByRole('button', { name: 'Отметить оплаченными' })
  expect(bulk).toBeDisabled()
  expect(bulk).toHaveAttribute('title', 'Соревнование в архиве: доступен только просмотр')
  expect(screen.getByText('Соревнование в архиве: доступен только просмотр')).toBeInTheDocument()
})
