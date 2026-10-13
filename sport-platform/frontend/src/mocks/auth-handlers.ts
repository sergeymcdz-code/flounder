import { http, HttpResponse } from 'msw'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '../features/auth/auth.types'

// Development only: the published demo account from the specification.
const demoAccounts = new Map([['parent@flounder.demo', 'Demo2026!']])
export function isDemoParticipant(email: string): boolean {
  return demoAccounts.has(email.trim().toLowerCase())
}

export const authHandlers = [
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
    const response: LoginResponse = {
      accessToken: token,
      tokenType: 'Bearer',
      expiresIn: 3600,
    }
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
