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
it('persists refresh tokens after login and calls logout with the current refresh token', async () => {
  let revoked: unknown
  server.use(
    http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer', expiresIn: 7200 })),
    http.post('*/api/v1/auth/logout', async ({ request }) => { revoked = await request.json(); return new HttpResponse(null, { status: 204 }) }),
  )
  render(<AuthProvider><Controls /></AuthProvider>)
  await userEvent.click(screen.getByRole('button', { name: 'Вход' }))
  expect(await screen.findByText('Выполнен вход')).toBeInTheDocument()
  expect(getAuthState().session?.refreshToken).toBe('refresh')
  await userEvent.click(screen.getByRole('button', { name: 'Выход' }))
  await vi.waitFor(() => expect(revoked).toEqual({ refreshToken: 'refresh' }))
  expect(screen.getByText('Нет сессии')).toBeInTheDocument()
})
it('restores sessions with expired access tokens when a refresh token exists', () => {
  sessionStorage.setItem('flounder.auth-session', JSON.stringify({ accessToken: 'expired', refreshToken: 'refresh', tokenType: 'Bearer', expiresAt: 1 }))
  render(<AuthProvider><Controls /></AuthProvider>)
  expect(screen.getByText('Выполнен вход')).toBeInTheDocument()
})
it('shows the expiration message after automatic sign-out', () => {
  setAuthSession(null, SESSION_EXPIRED)
  render(<MemoryRouter><AuthProvider><AppLayout /></AuthProvider></MemoryRouter>)
  expect(screen.getByRole('alert')).toHaveTextContent(SESSION_EXPIRED)
})
