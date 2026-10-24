import { expect, it, vi } from 'vitest'
import { server } from '../test/setup'
import { authHandlers, mockAuthSession } from './auth-handlers'
import { apiFetch } from '../api/http'
import type { LoginResponse } from '../features/auth/auth.types'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
const cookieHeader = (response: Response) => response.headers.get('Set-Cookie')!.split(';')[0]
it('models cookie refresh rotation and logout in MSW without returning secrets', async () => {
  server.use(...authHandlers)
  const login = mockAuthSession('access', 'user@example.org')
  expect(await login.clone().json()).not.toHaveProperty('refreshToken')
  const original = cookieHeader(login)
  const response = await fetch('http://localhost/api/v1/auth/refresh', { method: 'POST', headers: { Cookie: original } })
  expect(response.status).toBe(200)
  expect(await response.clone().json()).not.toHaveProperty('refreshToken')
  const rotated = cookieHeader(response)
  expect(rotated).not.toBe(original)
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST', headers: { Cookie: original } })).rejects.toMatchObject({ status: 401 })
  await apiFetch('/api/v1/auth/logout', { method: 'POST', headers: { Cookie: rotated } })
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST', headers: { Cookie: rotated } })).rejects.toMatchObject({ status: 401 })
})
it('models password change, password verification and revocation in MSW', async () => {
  server.use(...authHandlers)
  const email = 'm02-mock@example.org'
  await apiFetch('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password: 'Initial123!' }) })
  const pair = await apiFetch<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'Initial123!' }) })
  await expect(apiFetch('/api/v1/auth/change-password', {
    method: 'POST', accessToken: pair.accessToken,
    body: JSON.stringify({ currentPassword: 'wrong', newPassword: 'Changed123!' }),
  })).rejects.toMatchObject({ code: 'identity.password_mismatch' })
  await apiFetch('/api/v1/auth/change-password', {
    method: 'POST', accessToken: pair.accessToken,
    body: JSON.stringify({ currentPassword: 'Initial123!', newPassword: 'Changed123!' }),
  })
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST',  })).rejects.toMatchObject({ status: 401 })
  await expect(apiFetch('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'Changed123!' }) })).resolves.toHaveProperty('accessToken')
})

it('extends the access expiration when the mock refresh rotates an expired session', async () => {
  server.use(...authHandlers)
  const expired = mockAuthSession(`demo.${btoa(JSON.stringify({ roles: ['organizer'] }))}.demo`, 'org@flounder.demo', -1)
  const refreshed = await apiFetch<LoginResponse>('/api/v1/auth/refresh', { method: 'POST', headers: { Cookie: cookieHeader(expired) } })
  const payload = JSON.parse(atob(refreshed.accessToken.split('.')[1])) as { exp: number; roles: string[] }
  expect(payload.exp).toBeGreaterThan(Date.now() / 1000)
  expect(payload.roles).toEqual(['organizer'])
})
