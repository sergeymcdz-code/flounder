import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { OrganizerEntriesPage } from './OrganizerEntriesPage'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', disciplines: [{ id: 'discipline', name: '50 м' }] },
  registrations: [{ id: 'registration', eventDisciplineId: 'discipline', status: 'PAID', paymentStatus: 'PAID', participantSnapshot: { lastName: 'Тестов', firstName: 'Иван' }, seedTimeMs: 32000 }],
  heats: [], generationResults: {}, accessToken: 'test', busy: false, run: vi.fn(),
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
