import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useOutletContext } from 'react-router'
import { expect, it, vi } from 'vitest'
import { RequireRole } from '../../components/RequireRole'
import { OrganizerEventLayout } from './OrganizerEventLayout'
import { createFixtures } from '../../mocks/fixtures'
import type { EventContext } from './organizer-event-context'

vi.mock('../../app/providers/AuthProvider', () => ({
  useAuth: () => ({ session: { accessToken: 'test' }, isAuthenticated: true, authLoading: false,
    hasAnyRole: () => true, hasRole: () => false, isOrganizer: true, isPlatformAdmin: false,
    isOperator: false, logout: vi.fn() }),
}))
vi.mock('../../api/organizer.api', () => ({
  getOrganizerEvent: vi.fn(async () => createFixtures().event),
  getOrganizerRegistrations: vi.fn(async () => []),
}))
vi.mock('../../api/sessions.api', () => ({ getSessions: vi.fn(async () => []) }))
vi.mock('../../api/scheduling.api', () => ({ getHeats: vi.fn(async () => []) }))

function Child() {
  const { event } = useOutletContext<EventContext>()
  return <p>Контекст: {event.title}</p>
}
function Broken(): never { throw new Error('Child render failed') }
function mount(child: React.ReactNode) {
  return render(<MemoryRouter initialEntries={['/organizer/events/event/payments']}><Routes>
    <Route path="/organizer/events/:eventId" element={<OrganizerEventLayout />}>
      <Route element={<RequireRole roles={['organizer']} />}>
        <Route path="payments" element={child} />
      </Route>
      <Route path="program" element={<p>Программа открыта</p>} />
    </Route>
  </Routes></MemoryRouter>)
}
it('forwards event outlet context through RequireRole', async () => {
  mount(<Child />)
  expect(await screen.findByText(`Контекст: ${createFixtures().event.title}`)).toBeVisible()
})
it('keeps the event header and tabs when a child throws and allows another route', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  mount(<Broken />)
  expect(await screen.findByRole('heading', { name: 'Не удалось открыть раздел' })).toBeVisible()
  expect(screen.getByRole('heading', { name: createFixtures().event.title })).toBeVisible()
  expect(screen.getByRole('navigation', { name: 'Разделы соревнования' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Обновить' })).toBeVisible()
  fireEvent.click(screen.getByRole('link', { name: 'Программа' }))
  expect(await screen.findByText('Программа открыта')).toBeVisible()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})
it('retries the failed child when the update button is pressed', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  let failed = true
  function Retryable() {
    if (failed) throw new Error('Temporary render error')
    return <p>Раздел открыт</p>
  }
  mount(<Retryable />)
  expect(await screen.findByRole('alert')).toBeVisible()
  failed = false
  fireEvent.click(screen.getByRole('button', { name: 'Обновить' }))
  expect(await screen.findByText('Раздел открыт')).toBeVisible()
})
