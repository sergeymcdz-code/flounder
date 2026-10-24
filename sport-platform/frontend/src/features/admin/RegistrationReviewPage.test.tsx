import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { RegistrationReviewPage } from './RegistrationReviewPage'
import { getRegistrations, reviewRegistration } from '../../api/registrations.api'
import { ApiError } from '../../api/http'
import { createFixtures } from '../../mocks/fixtures'

vi.mock('../../api/registrations.api', () => ({ getRegistrations: vi.fn(), reviewRegistration: vi.fn() }))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: { accessToken: 'test' } }) }))
const row = { ...createFixtures().registrations[0], participantFullName: 'Иванова Анна Сергеевна', participantBirthYear: 2012, participantClubName: 'Волна', eventTitle: 'Кубок Москвы', eventStartAt: '2026-10-24T10:00:00Z', status: 'SUBMITTED' as const }
beforeEach(() => { vi.clearAllMocks(); vi.mocked(getRegistrations).mockResolvedValue([row]) })
it('selects by name, submits the internal ID, and retains event details after review', async () => {
  vi.mocked(reviewRegistration).mockResolvedValue({ ...row, eventTitle: undefined, status: 'NEEDS_CORRECTION', feeAmount: 900, feeCurrency: 'RUB', paymentStatus: 'PENDING' })
  const { container } = render(<RegistrationReviewPage />)
  expect(await screen.findByText(row.participantFullName)).toBeInTheDocument()
  expect(screen.getByText('Панель администратора')).toBeInTheDocument()
  expect(screen.queryByLabelText('ID заявки')).not.toBeInTheDocument()
  await userEvent.type(screen.getByRole('searchbox'), 'кубок')
  await userEvent.selectOptions(screen.getByLabelText('Соревнование'), row.eventId)
  await userEvent.click(screen.getByRole('button', { name: /Выбрать заявку/ }))
  expect(screen.getByRole('heading', { name: 'Решение по заявке' })).toHaveFocus()
  await userEvent.click(screen.getByRole('radio', { name: /Запросить уточнение/ }))
  await userEvent.type(screen.getByLabelText('Комментарий для участника'), '  Уточните клуб  ')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить решение' }))
  await waitFor(() => expect(reviewRegistration).toHaveBeenCalledWith(row.id, { decision: 'NEEDS_CORRECTION', reviewNote: 'Уточните клуб' }, 'test'))
  expect(await screen.findByText(/Заявка успешно обработана/)).toBeInTheDocument()
  expect(screen.getByText('На исправлении')).toBeInTheDocument()
  expect(screen.getByText(/900,00/)).toBeInTheDocument()
  expect(screen.getByText('Ожидает')).toBeInTheDocument()
  expect(screen.getAllByText('Кубок Москвы · 24.10.2026')).toHaveLength(2)
  expect(container.textContent).not.toMatch(/[a-f0-9]{8}-[a-f0-9-]{27}/i)
})
it('keeps selection and note on failure and permits retry', async () => {
  vi.mocked(reviewRegistration).mockRejectedValueOnce(new ApiError(403, 'forbidden')).mockResolvedValueOnce({ ...row, status: 'CONFIRMED' })
  render(<RegistrationReviewPage />)
  await userEvent.click(await screen.findByRole('button', { name: /Выбрать заявку/ }))
  await userEvent.type(screen.getByLabelText('Комментарий для участника'), 'Подтверждено')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить решение' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('нет прав')
  expect(screen.getByLabelText('Комментарий для участника')).toHaveValue('Подтверждено')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить решение' }))
  expect(await screen.findByText(/Заявка успешно обработана/)).toBeInTheDocument()
})
it('loads again after a list error', async () => {
  vi.mocked(getRegistrations).mockRejectedValueOnce(new Error('network'))
  render(<RegistrationReviewPage />)
  await screen.findByRole('alert')
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText(row.participantFullName)).toBeInTheDocument()
})
