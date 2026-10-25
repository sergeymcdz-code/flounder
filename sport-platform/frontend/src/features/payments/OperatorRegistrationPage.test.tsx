import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { getRegistration } from '../../api/registrations.api'
import { getPaymentForReview, confirmPayment } from '../../api/payments.api'
import { ApiError } from '../../api/http'
import { createFixtures } from '../../mocks/fixtures'
import { OperatorRegistrationPage } from './OperatorRegistrationPage'

vi.mock('../../api/registrations.api', () => ({ getRegistration: vi.fn(), reviewRegistration: vi.fn() }))
vi.mock('../../api/payments.api', () => ({ getPaymentForReview: vi.fn(), confirmPayment: vi.fn() }))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: { accessToken: 'test' }, hasRole: (role: string) => role === 'operator' }) }))
const row = { ...createFixtures().registrations[0], eventTitle: 'Кубок города', disciplineName: '50 м вольный стиль', participantFullName: 'Иванова Анна', status: 'CONFIRMED' as const }
const payment = { id: 'payment-internal-id', registrationId: row.id, amount: 1500, currency: 'RUB', status: 'PENDING', provider: 'MANUAL', confirmationUrl: null, expiresAt: null, paidAt: null, canceledAt: null, createdAt: '', updatedAt: '' }
function mount() { return render(<MemoryRouter initialEntries={[`/operator/registrations/${row.id}`]}><Routes><Route path="/operator/registrations/:registrationId" element={<OperatorRegistrationPage />} /></Routes></MemoryRouter>) }
beforeEach(() => { vi.clearAllMocks(); vi.mocked(getRegistration).mockResolvedValue(row); vi.mocked(getPaymentForReview).mockResolvedValue(payment) })
it('shows names instead of internal identifiers and confirms manual payment', async () => {
  vi.mocked(confirmPayment).mockResolvedValue({ ...payment, status: 'SUCCEEDED' })
  const { container } = mount()
  await userEvent.click(await screen.findByRole('button', { name: 'Подтвердить ручную оплату' }))
  expect(await screen.findByText('Ручная оплата подтверждена.')).toBeInTheDocument()
  expect(confirmPayment).toHaveBeenCalledWith(payment.id, 'test')
  expect(screen.getByText('Оплачено')).toBeInTheDocument()
  expect(container.textContent).not.toContain(row.id)
  expect(container.textContent).not.toContain(row.eventId)
  expect(container.textContent).not.toContain(payment.id)
  expect(screen.getByText('50 м вольный стиль')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Подтвердить заявку' })).not.toBeInTheDocument()
})
it('keeps a heading and retry after loading fails, and explains a missing payment', async () => {
  vi.mocked(getRegistration).mockRejectedValueOnce(new ApiError(503, 'Сервер недоступен'))
  vi.mocked(getPaymentForReview).mockRejectedValueOnce(new ApiError(404, 'Нет платежа'))
  mount()
  expect(await screen.findByRole('alert')).toHaveTextContent('Сервер недоступен')
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText('Платёж для этой заявки ещё не создан.')).toBeInTheDocument()
})
