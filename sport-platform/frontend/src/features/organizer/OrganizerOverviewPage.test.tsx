import { Link, MemoryRouter } from 'react-router'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/ui/Toast'
import { createFixtures } from '../../mocks/fixtures'
import type { OrganizerRegistration } from '../../api/organizer.types'
import { OrganizerOverviewPage } from './OrganizerOverviewPage'
import { publishEvent, closeEventRegistration, openEventRegistration, patchEvent } from '../../api/events.api'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', publicSlug: 'cup', title: 'Кубок', eventStartAt: '2030-06-01T10:00:00Z', eventEndAt: '2030-06-01T18:00:00Z', maxParticipants: 1, disciplines: [] },
  registrations: [{ participantProfileId: 'profile', status: 'CONFIRMED' }] as OrganizerRegistration[],
  sessions: [], heats: [], accessToken: 'test', busy: false, error: '',
  refresh: vi.fn().mockResolvedValue(undefined),
  registerDialog: vi.fn(() => vi.fn()),
  clearError: vi.fn(),
  run: vi.fn(async (action: () => Promise<unknown>) => { await action(); return true }),
}))
vi.mock('./organizer-event-context', async (original) => ({
  ...await original<typeof import('./organizer-event-context')>(),
  useOrganizerEvent: () => context,
}))
vi.mock('../../api/events.api', () => ({
  publishEvent: vi.fn(), completeEvent: vi.fn(), archiveEvent: vi.fn(),
  closeEventRegistration: vi.fn().mockResolvedValue({}),
  openEventRegistration: vi.fn().mockResolvedValue({}),
  patchEvent: vi.fn().mockResolvedValue({}),
}))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ isOrganizer: true, isPlatformAdmin: false }) }))
beforeEach(() => {
  context.event.status = 'REGISTRATION_OPEN'
  context.event.maxParticipants = 1
  context.registrations = [{ participantProfileId: 'profile', status: 'CONFIRMED', paymentStatus: 'PAID' }] as OrganizerRegistration[]
  context.busy = false
  context.error = ''
  vi.clearAllMocks()
})

it('M06 closes registration only after confirmation', async () => {
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Закрыть регистрацию' }))
  expect(closeEventRegistration).not.toHaveBeenCalled()
  expect(context.clearError).toHaveBeenCalledOnce()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Закрыть регистрацию' }))
  await waitFor(() => expect(closeEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(screen.getByText('1/1')).toBeInTheDocument()
})

it('M06 extension saves the new deadline before reopening', async () => {
  context.event.status = 'REGISTRATION_CLOSED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  expect(screen.queryByLabelText('Новая дата закрытия регистрации')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Продлить' }))
  fireEvent.change(screen.getByLabelText('Новая дата закрытия регистрации'), { target: { value: '2099-06-01T12:00' } })
  expect(patchEvent).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Продлить' }))
  await waitFor(() => expect(openEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(patchEvent).toHaveBeenCalledWith('event', { registrationCloseAt: new Date('2099-06-01T12:00').toISOString() }, 'test')
  expect(vi.mocked(patchEvent).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(openEventRegistration).mock.invocationCallOrder[0])
})

it('M06 archive has no status changes or limit editor', () => {
  context.event.status = 'ARCHIVED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  expect(screen.getByRole('region', { name: 'Сводка' })).toBeInTheDocument()
  expect(document.querySelector('.fl-btn-primary')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Сохранить' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Закрыть регистрацию' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Продлить' })).not.toBeInTheDocument()
})

it('opens the archive query on the mounted overview and can open it again after cancellation', async () => {
  context.event.status = 'COMPLETED'
  render(<MemoryRouter><Link to="?archive=1">Архив из меню</Link><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('link', { name: 'Архив из меню' }))
  expect(await screen.findByRole('dialog', { name: 'Отправить соревнование в архив?' })).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Отмена' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('link', { name: 'Архив из меню' }))
  expect(await screen.findByRole('dialog', { name: 'Отправить соревнование в архив?' })).toBeVisible()
  expect(context.clearError).toHaveBeenCalledTimes(2)
})

it.each([
  ['DRAFT', 'Опубликовать'], ['PUBLISHED', 'Открыть регистрацию'],
  ['REGISTRATION_OPEN', 'Закрыть регистрацию'], ['REGISTRATION_CLOSED', 'Завершить соревнование'],
  ['LIVE', 'Завершить соревнование'], ['COMPLETED', 'В архив'],
])('shows one lifecycle primary for %s', (status, label) => {
  context.event.status = status
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  expect(document.querySelectorAll('.fl-btn-primary')).toHaveLength(1)
  expect(screen.getByRole('button', { name: label })).toHaveClass('fl-btn-primary')
  expect(screen.getByRole('button', { name: label })).toBeEnabled()
})

it('publishes only once after confirmation', async () => {
  context.event.status = 'DRAFT'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Опубликовать' }))
  expect(publishEvent).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Опубликовать' }))
  await waitFor(() => expect(publishEvent).toHaveBeenCalledExactlyOnceWith('event', 'test'))
})

it('keeps zero summary links and a next step when no registrations exist', () => {
  context.registrations = []
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  const summary = screen.getByRole('region', { name: 'Сводка' })
  expect(within(summary).getByRole('link', { name: 'Заявок 0' })).toHaveAttribute('href', '/organizer/events/event/entries')
  expect(within(summary).getByText('Заявок пока нет')).toBeVisible()
})

it('puts pending work before statistics and preserves the existing destinations', () => {
  context.registrations = createFixtures().registrations
  context.registrations[0].status = 'SUBMITTED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  const attention = screen.getByRole('region', { name: 'Требует внимания' })
  expect(within(attention).getByRole('link', { name: /Заявки на проверке/ })).toHaveAttribute('href', '/organizer/events/event/entries?status=SUBMITTED')
  expect(within(attention).getByRole('link', { name: /Неоплаченные взносы/ })).toHaveAttribute('href', '/organizer/events/event/payments')
  expect(attention.compareDocumentPosition(screen.getByRole('region', { name: 'Сводка' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
})

it('keeps data visible and offers retry while the layout owns the global error', () => {
  context.error = 'Сервер ответил ошибкой (500).'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Сводка' })).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(context.run).toHaveBeenCalledWith(context.refresh)
})

it('shows a visible reason while a lifecycle action is loading', () => {
  context.busy = true
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  const button = screen.getByRole('button', { name: 'Закрыть регистрацию' })
  expect(button).toBeDisabled()
  expect(button).toHaveAttribute('aria-busy', 'true')
  expect(button).toHaveAccessibleDescription('Дождитесь завершения текущего действия')
})

it('rejects a past extension date without changing the event', () => {
  context.event.status = 'REGISTRATION_CLOSED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Продлить' }))
  fireEvent.change(screen.getByLabelText('Новая дата закрытия регистрации'), { target: { value: '2020-06-01T12:00' } })
  fireEvent.submit(screen.getByLabelText('Новая дата закрытия регистрации').closest('form')!)
  expect(screen.getByRole('alert')).toHaveTextContent('Новая дата закрытия должна быть в будущем.')
  expect(patchEvent).not.toHaveBeenCalled()
  expect(openEventRegistration).not.toHaveBeenCalled()
})

it('reports clipboard success through a toast and failure with a useful next step', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  render(<MemoryRouter><ToastProvider><OrganizerOverviewPage /></ToastProvider></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Копировать' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Ссылка скопирована'))
  expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/e/cup`)
  writeText.mockRejectedValueOnce(new Error('Denied'))
  fireEvent.click(screen.getByRole('button', { name: 'Копировать' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Выделите ссылку и скопируйте вручную.')
})

it('does not flag waived, refunded or free registrations as unpaid contributions', () => {
  context.registrations = ['PAID', 'WAIVED', 'REFUNDED', 'NOT_REQUIRED'].map(paymentStatus => ({
    participantProfileId: paymentStatus, status: 'CONFIRMED', paymentStatus,
  })) as OrganizerRegistration[]
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  const attention = screen.getByRole('region', { name: 'Требует внимания' })
  expect(within(attention).queryByRole('link', { name: /Неоплаченные взносы/ })).not.toBeInTheDocument()
  expect(within(attention).getByText(/Срочных задач нет/)).toBeVisible()
})
