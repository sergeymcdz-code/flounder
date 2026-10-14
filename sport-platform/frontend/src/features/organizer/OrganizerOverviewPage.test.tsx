import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { OrganizerOverviewPage } from './OrganizerOverviewPage'
import { closeEventRegistration, openEventRegistration, patchEvent } from '../../api/events.api'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', publicSlug: 'cup', title: 'Кубок', eventStartAt: '2030-06-01T10:00:00Z', eventEndAt: '2030-06-01T18:00:00Z', maxParticipants: 1 },
  registrations: [{ participantProfileId: 'profile', status: 'CONFIRMED' }],
  heats: [], accessToken: 'test', busy: false,
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
vi.mock('./OrganizerDialog', () => ({
  OrganizerConfirm: ({ title, onConfirm, onClose }: { title: string; onConfirm: () => void; onClose: () => void }) => (
    <div role="dialog" aria-label={title}>
      <button onClick={onConfirm}>Подтвердить</button>
      <button onClick={onClose}>Отмена</button>
    </div>
  ),
}))
beforeEach(() => { context.event.status = 'REGISTRATION_OPEN'; vi.clearAllMocks() })

it('M06 closes registration only after confirmation', async () => {
  render(<OrganizerOverviewPage />)
  fireEvent.click(screen.getByRole('button', { name: 'Закрыть регистрацию' }))
  expect(closeEventRegistration).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }))
  await waitFor(() => expect(closeEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(screen.getByText('Лимит: 1/1')).toBeInTheDocument()
})

it('M06 extension saves the new deadline before reopening', async () => {
  context.event.status = 'REGISTRATION_CLOSED'
  render(<OrganizerOverviewPage />)
  fireEvent.change(screen.getByLabelText('Новая дата закрытия регистрации'), { target: { value: '2099-06-01T12:00' } })
  fireEvent.click(screen.getByRole('button', { name: 'Продлить' }))
  expect(patchEvent).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }))
  await waitFor(() => expect(openEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(patchEvent).toHaveBeenCalledWith('event', { registrationCloseAt: new Date('2099-06-01T12:00').toISOString() }, 'test')
  expect(vi.mocked(patchEvent).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(openEventRegistration).mock.invocationCallOrder[0])
})

it('M06 archive has no status changes or limit editor', () => {
  context.event.status = 'ARCHIVED'
  render(<OrganizerOverviewPage />)
  expect(screen.getByText('В архиве')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Сохранить лимит' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Закрыть регистрацию' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Продлить' })).not.toBeInTheDocument()
})
