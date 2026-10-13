import { expect, it, vi } from 'vitest'
import { server } from '../test/setup'
import { authHandlers, mockAuthSession } from './auth-handlers'
import { apiFetch } from '../api/http'
import type { LoginResponse } from '../features/auth/auth.types'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
it('models one-time refresh rotation and logout in MSW', async () => {
  server.use(...authHandlers)
  const original = mockAuthSession('access', 'user@example.org')
  const rotated = await apiFetch<LoginResponse>('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: original.refreshToken }) })
  expect(rotated.refreshToken).not.toBe(original.refreshToken)
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: original.refreshToken }) })).rejects.toMatchObject({ status: 401 })
  await apiFetch('/api/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: rotated.refreshToken }) })
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: rotated.refreshToken }) })).rejects.toMatchObject({ status: 401 })
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
  await expect(apiFetch('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: pair.refreshToken }) })).rejects.toMatchObject({ status: 401 })
  await expect(apiFetch('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'Changed123!' }) })).resolves.toHaveProperty('refreshToken')
})

it('extends the access expiration when the mock refresh rotates an expired session', async () => {
  server.use(...authHandlers)
  const expired = mockAuthSession(`demo.${btoa(JSON.stringify({ roles: ['organizer'] }))}.demo`, 'org@flounder.demo', -1)
  const refreshed = await apiFetch<LoginResponse>('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: expired.refreshToken }) })
  const payload = JSON.parse(atob(refreshed.accessToken.split('.')[1])) as { exp: number; roles: string[] }
  expect(payload.exp).toBeGreaterThan(Date.now() / 1000)
  expect(payload.roles).toEqual(['organizer'])
})
