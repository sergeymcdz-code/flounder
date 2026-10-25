import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider, useAuth } from './AuthProvider'
import { getAuthState, setAuthSession, SESSION_EXPIRED } from '../../api/auth-session'
import { AppLayout } from '../../components/AppLayout'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
beforeEach(() => setAuthSession(null))
function Controls() {
  const auth = useAuth()
  return <><p>{auth.isAuthenticated ? 'Выполнен вход' : 'Нет сессии'}</p>
    <button onClick={() => void auth.login({ email: 'user@example.org', password: 'Password123' })}>Вход</button>
    <button onClick={() => void auth.logout()}>Выход</button></>
}
it('keeps refresh secrets out of storage and sends credentialed bodyless logout', async () => {
  const fetchSpy = vi.spyOn(globalThis, 'fetch')
  let revoked: unknown
  server.use(
    http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer', expiresIn: 7200 })),
    http.post('*/api/v1/auth/logout', async ({ request }) => { revoked = await request.text(); return new HttpResponse(null, { status: 204 }) }),
  )
  render(<AuthProvider><Controls /></AuthProvider>)
  await userEvent.click(screen.getByRole('button', { name: 'Вход' }))
  expect(await screen.findByText('Выполнен вход')).toBeInTheDocument()
  expect(getAuthState().session?.refreshToken).toBeUndefined()
  expect(sessionStorage.getItem('flounder.auth-session')).not.toContain('refresh')
  await userEvent.click(screen.getByRole('button', { name: 'Выход' }))
  await vi.waitFor(() => expect(revoked).toBe(''))
  expect(screen.getByText('Нет сессии')).toBeInTheDocument()
  const logoutRequest = fetchSpy.mock.calls.find(([url]) => String(url).endsWith('/auth/logout'))
  expect(logoutRequest?.[1]?.credentials).toBe('include')
  fetchSpy.mockRestore()
})
it('removes legacy stored refresh secrets and restores expired access using the cookie', async () => {
  server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'renewed', tokenType: 'Bearer', expiresIn: 7200 })))
  sessionStorage.setItem('flounder.auth-session', JSON.stringify({ accessToken: 'expired', refreshToken: 'refresh', tokenType: 'Bearer', expiresAt: 1 }))
  render(<AuthProvider><Controls /></AuthProvider>)
  expect(await screen.findByText('Выполнен вход')).toBeInTheDocument()
  expect(sessionStorage.getItem('flounder.auth-session')).not.toContain('refresh')
})
it('shows the expiration message after automatic sign-out', () => {
  setAuthSession(null, SESSION_EXPIRED)
  render(<MemoryRouter><AuthProvider><AppLayout /></AuthProvider></MemoryRouter>)
  expect(screen.getByRole('alert')).toHaveTextContent(SESSION_EXPIRED)
})

it.each([false, true])('keeps context values stable on parent rerenders (authenticated=%s)', async authenticated => {
  const snapshots: ReturnType<typeof useAuth>[] = []
  function Probe() {
    const auth = useAuth()
    snapshots.push(auth)
    return <p>{auth.authLoading ? 'Загрузка профиля' : 'Профиль готов'}</p>
  }
  if (authenticated) {
    setAuthSession({ accessToken: 'stable-token', tokenType: 'Bearer', expiresAt: Date.now() + 60000 })
    server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'user', roles: ['organizer'], effectiveRoles: ['organizer'],
      memberships: [{ organizationId: 'club', organizationName: 'Клуб', memberRole: 'owner' }] })))
  }
  const view = render(<AuthProvider><Probe /></AuthProvider>)
  await screen.findByText('Профиль готов')
  const previous = snapshots.at(-1)!
  view.rerender(<AuthProvider><Probe /></AuthProvider>)
  const current = snapshots.at(-1)!
  expect(current.memberships).toBe(previous.memberships)
  expect(current.roles).toBe(previous.roles)
  expect(current.selectOrg).toBe(previous.selectOrg)
  expect(current.hasRole).toBe(previous.hasRole)
  expect(current).toBe(previous)
})

it('clears all registration drafts on logout while preserving unrelated storage', async () => {
  localStorage.setItem('sportgate.registration.demo', 'first draft')
  localStorage.setItem('sportgate.registration.other', 'second draft')
  localStorage.setItem('sportgate.theme', 'dark')
  localStorage.setItem('other.preference', 'keep')
  render(<AuthProvider><Controls /></AuthProvider>)
  await userEvent.click(screen.getByRole('button', { name: 'Выход' }))
  expect(localStorage.getItem('sportgate.registration.demo')).toBeNull()
  expect(localStorage.getItem('sportgate.registration.other')).toBeNull()
  expect(localStorage.getItem('sportgate.theme')).toBe('dark')
  expect(localStorage.getItem('other.preference')).toBe('keep')
})
