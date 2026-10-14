import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { OrganizerEntriesPage } from './OrganizerEntriesPage'
vi.mock('./organizer-event-context', async (original) => ({
  ...await original<typeof import('./organizer-event-context')>(),
  useOrganizerEvent: () => ({
    event: { id: 'event', disciplines: [] }, accessToken: 'token', heats: [], generationResults: {}, busy: false, run: vi.fn(),
    registrations: [
      { id: 'with-medical', participantProfileId: 'child-1', participantSnapshot: { lastName: 'СоСправкой', firstName: 'Иван' }, documents: { MEDICAL_CERT: 'PENDING' }, admissionWarnings: ['Справка истекает до начала соревнования'], status: 'CONFIRMED', paymentStatus: 'NOT_REQUIRED' },
      { id: 'without-medical', participantProfileId: 'child-2', participantSnapshot: { lastName: 'БезСправки', firstName: 'Пётр' }, documents: { MEDICAL_CERT: 'NOT_UPLOADED' }, status: 'CONFIRMED', paymentStatus: 'NOT_REQUIRED' },
    ],
  }),
}))
it('shows document states and warnings and filters participants without a medical certificate', () => {
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByText('Справка истекает до начала соревнования')).toBeVisible()
  expect(screen.getByTitle('Медицинская справка: На проверке')).toBeVisible()
  fireEvent.click(screen.getByLabelText('Нет справки'))
  expect(screen.getByText('БезСправки Пётр')).toBeVisible()
  expect(screen.queryByText('СоСправкой Иван')).not.toBeInTheDocument()
  expect(screen.getByText('Найдено: 1')).toBeVisible()
})
