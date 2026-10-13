import { http, HttpResponse } from 'msw'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '../features/auth/auth.types'

// Development only: the published demo account from the specification.
const demoAccounts = new Map([['parent@flounder.demo', 'Demo2026!']])
const otherPasswords = new Map(['admin', 'org', 'sec', 'judge'].map(account => [`${account}@flounder.demo`, 'Demo2026!']))
export function mockPassword(email: string): string | undefined {
  const normalized = email.trim().toLowerCase()
  return demoAccounts.get(normalized) ?? otherPasswords.get(normalized)
}
function replaceMockPassword(email: string, password: string) {
  if (demoAccounts.has(email)) demoAccounts.set(email, password)
  else otherPasswords.set(email, password)
}
export function isDemoParticipant(email: string): boolean {
  return demoAccounts.has(email.trim().toLowerCase())
}

const sessions = new Map<string, { accessToken: string; email: string }>()
const resets = new Map<string, { email: string; expiresAt: number }>()
export function mockAuthSession(accessToken: string, email: string, expiresIn = 7200): LoginResponse {
  const parts = accessToken.split('.')
  if (parts.length === 3) {
    try {
      const payload = JSON.parse(atob(parts[1])) as Record<string, unknown>
      accessToken = `demo.${btoa(JSON.stringify({ ...payload, sub: email, exp: Math.floor(Date.now() / 1000) + expiresIn }))}.demo`
    } catch { /* Keep non-JWT test tokens unchanged. */ }
  }
  const refreshToken = crypto.randomUUID()
  sessions.set(refreshToken, { accessToken, email })
  return { accessToken, refreshToken, tokenType: 'Bearer', expiresIn }
}
const failure = (status: number, code: string, message: string) => HttpResponse.json({ code, message }, { status })
const weak = (password: string) => [...password].length < 8 || !/\p{L}/u.test(password) || !/\p{Nd}/u.test(password) || new TextEncoder().encode(password).length > 72

export const authHandlers = [
  http.post('*/api/v1/auth/refresh', async ({ request }) => {
    const { refreshToken } = await request.json() as { refreshToken: string }
    const session = sessions.get(refreshToken)
    if (!session) return failure(401, 'identity.refresh_token_invalid', 'Сессия истекла, войдите снова')
    sessions.delete(refreshToken)
    return HttpResponse.json(mockAuthSession(session.accessToken, session.email))
  }),
  http.post('*/api/v1/auth/logout', async ({ request }) => {
    const { refreshToken } = await request.json() as { refreshToken: string }
    sessions.delete(refreshToken)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('*/api/v1/auth/logout-all', ({ request }) => {
    const accessToken = request.headers.get('Authorization')?.replace('Bearer ', '')
    if (!accessToken) return failure(401, 'auth.unauthorized', 'Войдите в систему')
    const email = [...sessions.values()].find(s => s.accessToken === accessToken)?.email
    for (const [key, value] of sessions) if (value.email === email) sessions.delete(key)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
    const { email } = await request.json() as { email: string }
    if (mockPassword(email)) {
      const token = crypto.randomUUID()
      resets.set(token, { email: email.trim().toLowerCase(), expiresAt: Date.now() + 3600000 })
      console.info(`DEV Восстановление пароля Flounder: ${window.location.origin}/reset-password?token=${token}`)
    }
    return new HttpResponse(null, { status: 202 })
  }),
  http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
    const { token, newPassword } = await request.json() as { token: string; newPassword: string }
    const reset = resets.get(token)
    if (!reset || reset.expiresAt <= Date.now()) return failure(400, 'identity.reset_token_invalid', 'Ссылка восстановления недействительна или истекла')
    if (weak(newPassword)) return failure(400, 'identity.password_weak', 'Пароль должен содержать не менее 8 символов, буквы и цифры')
    replaceMockPassword(reset.email, newPassword); resets.delete(token)
    for (const [key, value] of sessions) if (value.email === reset.email) sessions.delete(key)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('*/api/v1/auth/change-password', async ({ request }) => {
    const accessToken = request.headers.get('Authorization')?.replace('Bearer ', '')
    const email = [...sessions.values()].find(s => s.accessToken === accessToken)?.email
    if (!email) return failure(401, 'auth.unauthorized', 'Войдите в систему')
    const { currentPassword, newPassword } = await request.json() as { currentPassword: string; newPassword: string }
    if (mockPassword(email) !== currentPassword) return failure(400, 'identity.password_mismatch', 'Текущий пароль указан неверно')
    if (weak(newPassword)) return failure(400, 'identity.password_weak', 'Пароль должен содержать не менее 8 символов, буквы и цифры')
    replaceMockPassword(email, newPassword)
    for (const [key, value] of sessions) if (value.email === email) sessions.delete(key)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginRequest
    if (demoAccounts.get(body.email.trim().toLowerCase()) !== body.password) {
      return HttpResponse.json(
        {
          code: 'auth.invalid_credentials',
          message: 'Используйте демо-аккаунт участника.',
          violations: [],
        },
        { status: 401 },
      )
    }
    const token =
      'demo.' +
      btoa(JSON.stringify({ roles: ['participant'] })).replaceAll('=', '') +
      '.demo'
    const response = mockAuthSession(token, body.email, 3600)
    return HttpResponse.json(response)
  }),
  http.post('*/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterRequest
    demoAccounts.set(body.email.trim().toLowerCase(), body.password)
    const response: RegisterResponse = {
      id: 'demo-participant',
      email: body.email,
      phone: body.phone,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
    }
    return HttpResponse.json(response, { status: 201 })
  }),
]
