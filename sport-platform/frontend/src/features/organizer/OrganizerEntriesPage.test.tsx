import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { OrganizerEntriesPage } from './OrganizerEntriesPage'

const context = vi.hoisted(() => ({
  event: { id: 'event', status: 'REGISTRATION_OPEN', disciplines: [{ id: 'discipline', name: '50 м' }] },
  registrations: [{ id: 'registration', eventDisciplineId: 'discipline', status: 'PAID', paymentStatus: 'PAID', participantSnapshot: { lastName: 'Тестов', firstName: 'Иван' }, seedTimeMs: 32000 }],
  heats: [], generationResults: {}, accessToken: 'test', busy: false, clearError: vi.fn(), run: vi.fn(),
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

it.each(['PENDING', 'NOT_REQUIRED'])('offers payment for a confirmed entry with %s payment', paymentStatus => {
  context.registrations[0].status = 'CONFIRMED'
  context.registrations[0].paymentStatus = paymentStatus
  render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
  expect(screen.getByRole('button', { name: 'Оплачено' })).toBeVisible()
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
  const action = vi.spyOn(await import('../../api/organizer.api'), 'registrationAction').mockResolvedValue({} as never)
  context.registrations = [{ ...context.registrations[0], id: 'zero-fee', status: 'CONFIRMED', paymentStatus: 'NOT_REQUIRED' }]
  Object.assign(context, { refresh: vi.fn().mockResolvedValue(undefined) })
  try {
    render(<MemoryRouter><OrganizerEntriesPage /></MemoryRouter>)
    await user.click(screen.getByRole('checkbox', { name: 'Выбрать все видимые заявки' }))
    const bar = document.querySelector('.fl-bulk-bar') as HTMLElement
    await user.click(within(bar).getByRole('button', { name: 'Оплачено' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Подходящих заявок: 1 из 1')
    expect(action).not.toHaveBeenCalled()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Применить' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith('zero-fee', 'mark-paid', 'test'))
  } finally { action.mockRestore() }
})
