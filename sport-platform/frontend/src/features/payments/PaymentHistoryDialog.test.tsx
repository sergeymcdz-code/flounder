import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { PaymentHistoryDialog } from './PaymentHistoryDialog'
import { paymentHistory } from '../../api/registration-payments.api'

vi.mock('../organizer/organizer-event-context', () => ({ useOrganizerEvent: () => undefined }))
vi.mock('../../api/registration-payments.api', async original => ({ ...await original<typeof import('../../api/registration-payments.api')>(), paymentHistory: vi.fn() }))
beforeEach(() => vi.mocked(paymentHistory).mockReset())
it('does not show an empty state while loading and retries a network failure', async () => {
  vi.mocked(paymentHistory).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce([])
  const onClose = vi.fn()
  render(<MemoryRouter><PaymentHistoryDialog id="entry" token="test" onClose={onClose} /></MemoryRouter>)
  expect(screen.getByRole('status')).toHaveTextContent('Загружаем историю оплаты')
  expect(screen.queryByText('Операций пока нет')).not.toBeInTheDocument()
  expect(await screen.findByRole('alert')).toHaveTextContent('Нет связи')
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText('Операций пока нет')).toBeInTheDocument()
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  await userEvent.click(screen.getAllByRole('button', { name: 'Закрыть' })[1])
  expect(onClose).toHaveBeenCalled()
})
