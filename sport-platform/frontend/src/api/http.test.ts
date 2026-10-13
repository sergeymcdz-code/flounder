import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../test/setup'
import { apiFetch, apiDownload } from './http'
import { getAuthState, setAuthSession, SESSION_EXPIRED } from './auth-session'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
const pair = { accessToken: 'new-access', refreshToken: 'new-refresh', tokenType: 'Bearer', expiresIn: 3600 }
beforeEach(() => setAuthSession({ accessToken: 'old-access', refreshToken: 'old-refresh', tokenType: 'Bearer', expiresAt: Date.now() - 1000 }))

it('refreshes once and repeats the original POST with the new bearer and body', async () => {
  const auth: (string | null)[] = []
  const payloads: unknown[] = []
  let refreshes = 0
  server.use(
    http.post('*/private', async ({ request }) => {
      auth.push(request.headers.get('Authorization')); payloads.push(await request.json())
      return auth.length === 1 ? new HttpResponse(null, { status: 401 }) : HttpResponse.json({ ok: true })
    }),
    http.post('*/api/v1/auth/refresh', async ({ request }) => {
      refreshes++
      expect(request.headers.has('Authorization')).toBe(false)
      expect(await request.json()).toEqual({ refreshToken: 'old-refresh' })
      return HttpResponse.json(pair)
    }),
  )
  expect(await apiFetch('/private', { method: 'POST', body: JSON.stringify({ value: 42 }), accessToken: 'old-access' })).toEqual({ ok: true })
  expect(refreshes).toBe(1)
  expect(auth).toEqual(['Bearer old-access', 'Bearer new-access'])
  expect(payloads).toEqual([{ value: 42 }, { value: 42 }])
  expect(getAuthState().session?.refreshToken).toBe('new-refresh')
  expect(JSON.parse(sessionStorage.getItem('flounder.auth-session')!).refreshToken).toBe('new-refresh')
})

it('shares one refresh for simultaneous 401 responses and reuses the rotated access token for late responses', async () => {
  let refreshes = 0
  server.use(
    http.get('*/private', ({ request }) => request.headers.get('Authorization') === 'Bearer new-access'
      ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', async () => {
      refreshes++
      await new Promise(resolve => setTimeout(resolve, 30))
      return HttpResponse.json(pair)
    }),
  )
  await Promise.all([apiFetch('/private', { accessToken: 'old-access' }), apiFetch('/private', { accessToken: 'old-access' })])
  await apiFetch('/private', { accessToken: 'old-access' })
  expect(refreshes).toBe(1)
})

it('clears the session and reports expiration when refresh fails', async () => {
  server.use(
    http.get('*/private', () => new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
  )
  await expect(apiFetch('/private', { accessToken: 'old-access' })).rejects.toMatchObject({ status: 401, message: SESSION_EXPIRED })
  expect(getAuthState()).toEqual({ session: null, message: SESSION_EXPIRED })
  expect(sessionStorage.getItem('flounder.auth-session')).toBeNull()
})

it('does not refresh a second time if the retried request also returns 401', async () => {
  let requests = 0, refreshes = 0
  server.use(
    http.get('*/private', () => { requests++; return new HttpResponse(null, { status: 401 }) }),
    http.post('*/api/v1/auth/refresh', () => { refreshes++; return HttpResponse.json(pair) }),
  )
  await expect(apiFetch('/private', { accessToken: 'old-access' })).rejects.toMatchObject({ message: SESSION_EXPIRED })
  expect(requests).toBe(2); expect(refreshes).toBe(1); expect(getAuthState().session).toBeNull()
})

it('does not refresh public login failures or forbidden requests', async () => {
  let refreshes = 0
  server.use(
    http.post('*/api/v1/auth/login', () => new HttpResponse(null, { status: 401 })),
    http.get('*/private', () => new HttpResponse(null, { status: 403 })),
    http.post('*/api/v1/auth/refresh', () => { refreshes++; return HttpResponse.json(pair) }),
  )
  await expect(apiFetch('/api/v1/auth/login', { method: 'POST' })).rejects.toMatchObject({ status: 401 })
  await expect(apiFetch('/private', { accessToken: 'old-access' })).rejects.toMatchObject({ status: 403 })
  expect(refreshes).toBe(0); expect(getAuthState().session).not.toBeNull()
})

it('refreshes authenticated downloads too', async () => {
  server.use(
    http.get('*/file', ({ request }) => request.headers.get('Authorization') === 'Bearer new-access'
      ? new HttpResponse('xlsx') : new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', () => HttpResponse.json(pair)),
  )
  expect((await apiDownload('/file', 'old-access')).size).toBe(4)
})

it('does not refresh an anonymous download and uses the generic file error message', async () => {
  let refreshes = 0
  server.use(
    http.get('*/public-file', ({ request }) => {
      expect(request.headers.has('Authorization')).toBe(false)
      return new HttpResponse(null, { status: 401 })
    }),
    http.post('*/api/v1/auth/refresh', () => { refreshes++; return HttpResponse.json(pair) }),
  )
  await expect(apiDownload('/public-file')).rejects.toMatchObject({ status: 401, message: 'Не удалось скачать файл.' })
  expect(refreshes).toBe(0)
})

it('does not restore a logged-out session when an in-flight refresh completes and revokes the rotated token', async () => {
  let finish!: () => void, started!: () => void
  const running = new Promise<void>(resolve => { started = resolve })
  const gate = new Promise<void>(resolve => { finish = resolve })
  let revoked: unknown
  server.use(
    http.get('*/private', () => new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', async () => { started(); await gate; return HttpResponse.json(pair) }),
    http.post('*/api/v1/auth/logout', async ({ request }) => { revoked = await request.json(); return new HttpResponse(null, { status: 204 }) }),
  )
  const pending = apiFetch('/private', { accessToken: 'old-access' })
  const outcome = expect(pending).rejects.toMatchObject({ status: 401 })
  await running; setAuthSession(null); finish(); await outcome
  await vi.waitFor(() => expect(revoked).toEqual({ refreshToken: 'new-refresh' }))
  expect(getAuthState().session).toBeNull()
})

it('exposes the numeric Retry-After header on API errors', async () => {
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ code: 'identity.too_many_attempts', message: 'Слишком много попыток входа' }, {
    status: 429, headers: { 'Retry-After': '121' },
  })))
  await expect(apiFetch('/api/v1/auth/login', { method: 'POST' })).rejects.toMatchObject({ status: 429, retryAfter: 121 })
})
it('ignores missing or invalid Retry-After values', async () => {
  for (const retryAfter of [undefined, 'invalid', '-5']) {
    server.use(http.get('*/limited', () => new HttpResponse(null, { status: 429, headers: retryAfter === undefined ? {} : { 'Retry-After': retryAfter } })))
    await expect(apiFetch('/limited')).rejects.toMatchObject({ status: 429, retryAfter: undefined })
  }
})
