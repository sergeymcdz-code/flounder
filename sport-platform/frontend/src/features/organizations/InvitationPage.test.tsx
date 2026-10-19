import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider, useAuth } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { InvitationPage } from './InvitationPage'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost'))
const invitation = { organizationName: 'Клуб Волна', memberRole: 'operator', email: 'invitee@example.org', expiresAt: '2030-01-01T10:00:00Z', status: 'PENDING', userExists: true }
function Destination() {
  const { hasRole, currentOrgId } = useAuth()
  return <p>Кабинет: {hasRole('operator') ? 'Секретарь' : 'Участник'}, {currentOrgId}</p>
}
function renderPage() {
  return render(<MemoryRouter initialEntries={['/invitations/secure-token']}><AuthProvider><Routes>
    <Route path="/invitations/:token" element={<InvitationPage />} />
    <Route path="/organizer/events" element={<Destination />} />
  </Routes></AuthProvider></MemoryRouter>)
}
beforeEach(() => { setAuthSession(null); localStorage.removeItem('flounder.current-org') })
it('shows organization, Russian role and return URLs for login and registration', async () => {
  server.use(http.get('*/api/v1/invitations/secure-token', () => HttpResponse.json(invitation)))
  renderPage()
  expect(await screen.findByText('Клуб Волна')).toBeInTheDocument()
  expect(screen.getByText('Роль: Секретарь')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login?next=%2Finvitations%2Fsecure-token')
  expect(screen.getByRole('link', { name: 'Зарегистрироваться' })).toHaveAttribute('href', '/register?next=%2Finvitations%2Fsecure-token')
})
it('accepts using the bearer token, reloads effective roles and opens the selected organization', async () => {
  let accepted = false
  let authorization = ''
  setAuthSession({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: Date.now() + 60000 })
  server.use(
    http.get('*/api/v1/invitations/secure-token', () => HttpResponse.json(invitation)),
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'user', roles: ['participant'], effectiveRoles: accepted ? ['participant', 'operator'] : ['participant'], memberships: accepted ? [{ organizationId: 'club', organizationName: 'Клуб Волна', memberRole: 'operator' }] : [] })),
    http.post('*/api/v1/invitations/secure-token/accept', ({ request }) => {
      authorization = request.headers.get('Authorization') ?? ''; accepted = true
      return HttpResponse.json({ organizationId: 'club', memberRole: 'operator' })
    }),
  )
  renderPage()
  await userEvent.click(await screen.findByRole('button', { name: 'Принять приглашение' }))
  expect(await screen.findByText('Кабинет: Секретарь, club')).toBeInTheDocument()
  expect(authorization).toBe('Bearer test-token')
  expect(localStorage.getItem('flounder.current-org')).toBe('club')
})
it('shows an expired invitation error without offering acceptance', async () => {
  server.use(http.get('*/api/v1/invitations/secure-token', () => HttpResponse.json({ code: 'organizations.invitation_expired', message: 'Срок приглашения истёк' }, { status: 410 })))
  renderPage()
  expect(await screen.findByRole('alert')).toHaveTextContent('Срок приглашения истёк')
  expect(screen.queryByRole('button', { name: 'Принять приглашение' })).not.toBeInTheDocument()
})
it('explains that the signed in email must match the invitation', async () => {
  setAuthSession({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: Date.now() + 60000 })
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'other', roles: ['participant'], effectiveRoles: ['participant'], memberships: [] })),
    http.get('*/api/v1/invitations/secure-token', () => HttpResponse.json(invitation)),
    http.post('*/api/v1/invitations/secure-token/accept', () => HttpResponse.json({ code: 'organizations.invitation_email_mismatch', message: 'Войдите под email, на который отправлено приглашение' }, { status: 403 })),
  )
  renderPage()
  await userEvent.click(await screen.findByRole('button', { name: 'Принять приглашение' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Войдите под email')
})
