import { MemoryRouter } from 'react-router'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { OrganizerOverviewPage } from './OrganizerOverviewPage'
import { closeEventRegistration, openEventRegistration, patchEvent } from '../../api/events.api'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', publicSlug: 'cup', title: 'Кубок', eventStartAt: '2030-06-01T10:00:00Z', eventEndAt: '2030-06-01T18:00:00Z', maxParticipants: 1, disciplines: [] },
  registrations: [{ participantProfileId: 'profile', status: 'CONFIRMED' }],
  sessions: [], heats: [], accessToken: 'test', busy: false,
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
vi.mock('./OrganizerDialog', () => ({
  OrganizerConfirm: ({ title, onConfirm, onClose }: { title: string; onConfirm: () => void; onClose: () => void }) => (
    <div role="dialog" aria-label={title}>
      <button className="fl-btn fl-btn-secondary" onClick={onConfirm}>Подтвердить</button>
      <button className="fl-btn fl-btn-secondary" onClick={onClose}>Отмена</button>
    </div>
  ),
}))
beforeEach(() => { context.event.status = 'REGISTRATION_OPEN'; vi.clearAllMocks() })

it('M06 closes registration only after confirmation', async () => {
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Закрыть регистрацию' }))
  expect(closeEventRegistration).not.toHaveBeenCalled()
  expect(context.clearError).toHaveBeenCalledOnce()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Закрыть регистрацию' }))
  await waitFor(() => expect(closeEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(screen.getByText('Лимит: 1/1')).toBeInTheDocument()
})

it('M06 extension saves the new deadline before reopening', async () => {
  context.event.status = 'REGISTRATION_CLOSED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('Новая дата закрытия регистрации'), { target: { value: '2099-06-01T12:00' } })
  fireEvent.click(screen.getByRole('button', { name: 'Продлить' }))
  expect(patchEvent).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Продлить' }))
  await waitFor(() => expect(openEventRegistration).toHaveBeenCalledWith('event', 'test'))
  expect(patchEvent).toHaveBeenCalledWith('event', { registrationCloseAt: new Date('2099-06-01T12:00').toISOString() }, 'test')
  expect(vi.mocked(patchEvent).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(openEventRegistration).mock.invocationCallOrder[0])
})

it('M06 archive has no status changes or limit editor', () => {
  context.event.status = 'ARCHIVED'
  render(<MemoryRouter><OrganizerOverviewPage /></MemoryRouter>)
  expect(screen.getByText('Архив')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Сохранить' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Закрыть регистрацию' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Продлить' })).not.toBeInTheDocument()
})
