import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { it, expect, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { NotificationSettingsPage } from './NotificationSettingsPage'
import { OrganizerNotificationsPage } from './OrganizerNotificationsPage'
import type { NotificationSetting } from '../../api/notifications.api'
import { server } from '../../test/setup'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: { accessToken: 'test-token' } }) }))
vi.mock('./organizer-event-context', () => ({ useOrganizerEvent: () => ({ accessToken: 'test-token' }) }))
function journal() {
  render(<MemoryRouter initialEntries={['/organizer/events/cup/notifications']}><Routes>
    <Route path="/organizer/events/:eventId/notifications" element={<OrganizerNotificationsPage />} />
  </Routes></MemoryRouter>)
}
it('loads settings and persists toggles with a visible success message', async () => {
  let saved: NotificationSetting[] = []
  server.use(
    http.get('*/api/v1/organizations', () => HttpResponse.json([{ id: 'club', name: 'Волна' }])),
    http.get('*/api/v1/organizations/club/notification-settings', () => HttpResponse.json([{ templateCode: 'registration_confirmed', enabled: true }])),
    http.put('*/api/v1/organizations/club/notification-settings', async ({ request }) => { saved = await request.json() as NotificationSetting[]; return HttpResponse.json(saved) }),
  )
  render(<NotificationSettingsPage />)
  const checkbox = await screen.findByRole('checkbox', { name: 'Заявка подтверждена' })
  expect(checkbox).toBeChecked()
  await userEvent.click(checkbox)
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
  await screen.findByText('Настройки сохранены.')
  expect(saved).toEqual([{ templateCode: 'registration_confirmed', enabled: false }])
})
it('shows the owner access error and never offers unavailable settings', async () => {
  server.use(
    http.get('*/api/v1/organizations', () => HttpResponse.json([{ id: 'club', name: 'Волна' }])),
    http.get('*/api/v1/organizations/club/notification-settings', () => HttpResponse.json({ message: 'Только владелец или администратор организации может менять уведомления' }, { status: 403 })),
  )
  render(<NotificationSettingsPage />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Только владелец')
  expect(screen.queryByRole('button', { name: 'Сохранить' })).not.toBeInTheDocument()
})
it('shows an empty journal and reloads with the selected status', async () => {
  const queries: string[] = []
  server.use(http.get('*/api/v1/events/cup/notifications', ({ request }) => {
    queries.push(new URL(request.url).search)
    return HttpResponse.json({ items: [], page: 0, totalElements: 0, totalPages: 0 })
  }))
  journal()
  await screen.findByText('Уведомлений пока нет.')
  await userEvent.selectOptions(screen.getByLabelText('Статус'), 'FAILED')
  await waitFor(() => expect(queries).toContain('?page=0&status=FAILED'))
  await screen.findByText('Уведомлений пока нет.')
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled()
})
it('paginates the journal and displays Russian delivery states', async () => {
  const pages: number[] = []
  server.use(http.get('*/api/v1/events/cup/notifications', ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page'))
    pages.push(page)
    return HttpResponse.json({ items: [{ id: String(page), recipientEmail: `participant${page}@example.org`,
      templateCode: 'results_published', status: 'SENT', attempts: 1, createdAt: '2026-10-19T10:00:00Z' }],
    page, totalElements: 51, totalPages: 2 })
  }))
  journal()
  await screen.findByText('participant0@example.org')
  expect(screen.getByRole('cell', { name: 'Отправлено' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }))
  await screen.findByText('participant1@example.org')
  expect(pages).toEqual([0, 1])
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled()
})
it('shows an access denial for a foreign event', async () => {
  server.use(http.get('*/api/v1/events/cup/notifications', () => HttpResponse.json({ message: 'Нет доступа к управлению этим событием' }, { status: 403 })))
  journal()
  expect(await screen.findByRole('alert')).toHaveTextContent('Нет доступа')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})
