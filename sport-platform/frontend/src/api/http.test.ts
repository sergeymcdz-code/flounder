import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../test/setup'
import { apiFetch, apiDownload } from './http'
import { getAuthState, setAuthSession, SESSION_EXPIRED } from './auth-session'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
const pair = { accessToken: 'new-access', tokenType: 'Bearer', expiresIn: 3600 }
beforeEach(() => setAuthSession({ accessToken: 'old-access', refreshToken: 'old-refresh', tokenType: 'Bearer', expiresAt: Date.now() - 1000 }))

it('refreshes once and repeats the original POST with the new bearer and body', async () => {
  const fetchSpy = vi.spyOn(globalThis, 'fetch')
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
      expect(await request.text()).toBe('')
      return HttpResponse.json(pair)
    }),
  )
  expect(await apiFetch('/private', { method: 'POST', body: JSON.stringify({ value: 42 }), accessToken: 'old-access' })).toEqual({ ok: true })
  expect(refreshes).toBe(1)
  expect(auth).toEqual(['Bearer old-access', 'Bearer new-access'])
  expect(payloads).toEqual([{ value: 42 }, { value: 42 }])
  const refreshRequest = fetchSpy.mock.calls.find(([url]) => String(url).endsWith('/api/v1/auth/refresh'))
  expect(refreshRequest?.[1]).toMatchObject({ method: 'POST', credentials: 'include' })
  expect(refreshRequest?.[1]?.body).toBeUndefined()
  expect(getAuthState().session?.refreshToken).toBeUndefined()
  expect(JSON.parse(sessionStorage.getItem('flounder.auth-session')!)).not.toHaveProperty('refreshToken')
})

it('never persists an unexpected refresh secret returned by a legacy server', async () => {
  server.use(
    http.get('*/private', ({ request }) => request.headers.get('Authorization') === 'Bearer new-access'
      ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ ...pair, refreshToken: 'must-not-persist' })),
  )
  await expect(apiFetch('/private', { accessToken: 'old-access' })).resolves.toEqual({ ok: true })
  expect(getAuthState().session).not.toHaveProperty('refreshToken')
  expect(JSON.parse(sessionStorage.getItem('flounder.auth-session')!)).not.toHaveProperty('refreshToken')
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
  const fetchSpy = vi.spyOn(globalThis, 'fetch')
  let finish!: () => void, started!: () => void
  const running = new Promise<void>(resolve => { started = resolve })
  const gate = new Promise<void>(resolve => { finish = resolve })
  let revoked: unknown
  server.use(
    http.get('*/private', () => new HttpResponse(null, { status: 401 })),
    http.post('*/api/v1/auth/refresh', async () => { started(); await gate; return HttpResponse.json(pair) }),
    http.post('*/api/v1/auth/logout', async ({ request }) => { revoked = await request.text(); return new HttpResponse(null, { status: 204 }) }),
  )
  const pending = apiFetch('/private', { accessToken: 'old-access' })
  const outcome = expect(pending).rejects.toMatchObject({ status: 401 })
  await running; setAuthSession(null); finish(); await outcome
  await vi.waitFor(() => expect(revoked).toBe(''))
  const logoutRequest = fetchSpy.mock.calls.find(([url]) => String(url).endsWith('/api/v1/auth/logout'))
  expect(logoutRequest?.[1]).toMatchObject({ method: 'POST', credentials: 'include' })
  expect(logoutRequest?.[1]?.body).toBeUndefined()
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

it.each([apiFetch, apiDownload])('reports loss of connection only when fetch has no response', async (request) => {
  server.use(http.get('*/offline', () => HttpResponse.error()))
  await expect(request('/offline')).rejects.toMatchObject({ status: 0, message: 'Нет связи. Проверьте интернет и повторите' })
})
it.each([404, 500, 503])('reports HTTP %s as a server response for JSON and file requests', async (status) => {
  server.use(http.get('*/unavailable', () => HttpResponse.json({ message: 'Сервер занят формированием PDF' }, { status })))
  for (const request of [apiFetch, apiDownload]) {
    await expect(request('/unavailable')).rejects.toMatchObject({ status, message: 'Сервер занят формированием PDF' })
  }
  server.use(http.get('*/unavailable', () => new HttpResponse('<html>Error</html>', { status })))
  await expect(apiFetch('/unavailable')).rejects.toMatchObject({ status, message: `Сервер вернул ошибку (${status})` })
  await expect(apiDownload('/unavailable')).rejects.toMatchObject({ status, message: 'Не удалось скачать файл.' })
})
it('preserves business validation messages and violations', async () => {
  const violations = [{ field: 'title', message: 'Укажите название' }]
  server.use(http.post('*/invalid', () => HttpResponse.json({ code: 'event.invalid', message: 'Проверьте данные', violations }, { status: 400 })))
  await expect(apiFetch('/invalid', { method: 'POST' })).rejects.toMatchObject({ status: 400, code: 'event.invalid', message: 'Проверьте данные', violations })
})
it('preserves cancellation without turning it into a connection error', async () => {
  const controller = new AbortController()
  controller.abort()
  await expect(apiFetch('/cancelled', { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' })
})
