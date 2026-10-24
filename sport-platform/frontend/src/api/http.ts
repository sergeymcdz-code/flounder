import { getAuthState, setAuthSession, SESSION_EXPIRED } from './auth-session'
import type { LoginResponse } from '../features/auth/auth.types'

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.VITE_API_MOCK === 'true' || import.meta.env.PROD
    ? window.location.origin
    : '')

export class ApiError extends Error {
  public readonly status: number
  public readonly code?: string
  public readonly violations: FieldViolation[]
  public readonly retryAfter?: number

  constructor(
    status: number,
    message: string,
    code?: string,
    violations: FieldViolation[] = [],
    retryAfter?: number,
  ) {
    super(message)

    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.violations = violations ?? []
    this.retryAfter = retryAfter
  }
}

export type FieldViolation = { field: string; message: string }

type ApiErrorResponse = {
  violations?: FieldViolation[]
  code?: string
  message?: string
}

type ApiFetchOptions = RequestInit & {
  accessToken?: string | null
  skipRefresh?: boolean
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  if (!API_BASE_URL) {
    throw new Error('Не задан адрес API (VITE_API_BASE_URL).')
  }

  const { accessToken, skipRefresh, ...requestOptions } = options
  const headers = new Headers(requestOptions.headers)

  headers.set('Accept', 'application/json')

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  if (
    typeof requestOptions.body === 'string' &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await authenticatedFetch(path, { ...requestOptions, headers }, accessToken, skipRefresh)

  if (!response.ok) {
    let payload: ApiErrorResponse | undefined

    try {
      payload = (await response.json()) as ApiErrorResponse
    } catch {
      payload = undefined
    }

    throw new ApiError(
      response.status,
      serverErrorMessage(response.status, payload?.message),
      payload?.code,
      payload?.violations,
      retryAfterSeconds(response),
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type')

  if (!contentType?.includes('application/json')) {
    return undefined as T
  }

  return (await response.json()) as T
}

function serverErrorMessage(status: number, message?: string, fallback?: string): string {
  // Business messages from the backend (GlobalExceptionHandler) are shown on any status;
  // the generic text is only for responses without a JSON message (HTML error pages, proxies).
  return message || fallback || `Сервер вернул ошибку (${status})`
}

function retryAfterSeconds(response: Response): number | undefined {
  const value = response.headers.get('Retry-After')
  if (!value || !/^\d+$/.test(value)) return undefined
  const seconds = Number(value)
  return Number.isFinite(seconds) ? seconds : undefined
}

// Upgrade old stored sessions without retaining a refresh secret in browser storage.
export function sanitizeStoredSession() {
  const key = 'flounder.auth-session'
  const stored = sessionStorage.getItem(key)
  if (!stored) return false
  try {
    const parsed = JSON.parse(stored)
    const expired = !!parsed?.accessToken && parsed.expiresAt <= Date.now()
    if (parsed && 'refreshToken' in parsed) {
      delete parsed.refreshToken
      sessionStorage.setItem(key, JSON.stringify(parsed))
    }
    return expired
  } catch { sessionStorage.removeItem(key); return false }
}

let refreshInFlight: Promise<string> | null = null
export async function settleRefresh() { await refreshInFlight?.catch(() => {}) }
export async function restoreCookieSession() { await refreshAccessToken().catch(() => {}) }

async function refreshAccessToken(failedToken?: string): Promise<string> {
  const initialState = getAuthState()
  const session = initialState.session
  if (failedToken && session && session.accessToken !== failedToken) return session.accessToken
  if (refreshInFlight) return refreshInFlight
  if (!session && failedToken) {
    setAuthSession(null, SESSION_EXPIRED)
    throw new ApiError(401, SESSION_EXPIRED, 'identity.refresh_token_invalid')
  }
  refreshInFlight = (async () => {
    try {
      const response = await apiFetch<LoginResponse>('/api/v1/auth/refresh', {
        method: 'POST', skipRefresh: true,
      })
      if (getAuthState() !== initialState) {
        // Revoke a rotated token if logout or a different login won the race.
        await apiFetch('/api/v1/auth/logout', {
          method: 'POST', skipRefresh: true,
        }).catch(() => {})
        throw new ApiError(401, SESSION_EXPIRED)
      }
      setAuthSession({
        accessToken: response.accessToken,
        tokenType: response.tokenType, expiresAt: Date.now() + response.expiresIn * 1000,
      })
      return response.accessToken
    } catch {
      if (getAuthState() === initialState) setAuthSession(null, failedToken ? SESSION_EXPIRED : null)
      throw new ApiError(401, SESSION_EXPIRED, 'identity.refresh_token_invalid')
    }
  })()
  try { return await refreshInFlight } finally { refreshInFlight = null }
}

async function authenticatedFetch(path: string, options: RequestInit, accessToken?: string | null, skipRefresh = false) {
  const response = await networkFetch(`${API_BASE_URL}${path}`, options)
  if (response.status !== 401 || !accessToken || skipRefresh) return response
  const token = await refreshAccessToken(accessToken)
  const headers = new Headers(options.headers)
  headers.set('Authorization', `Bearer ${token}`)
  const retried = await networkFetch(`${API_BASE_URL}${path}`, { ...options, headers })
  if (retried.status === 401 && getAuthState().session?.accessToken === token) {
    setAuthSession(null, SESSION_EXPIRED)
    throw new ApiError(401, SESSION_EXPIRED)
  }
  return retried
}

export async function apiDownload(path: string, accessToken?: string): Promise<Blob> {
  if (!API_BASE_URL) throw new Error('Не задан адрес API (VITE_API_BASE_URL).')
  const response = await authenticatedFetch(path, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  }, accessToken)
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as ApiErrorResponse
    throw new ApiError(
      response.status, serverErrorMessage(response.status, payload?.message, 'Не удалось скачать файл.'), payload?.code, payload?.violations, retryAfterSeconds(response),
    )
  }
  return response.blob()
}

async function networkFetch(url: string, options: RequestInit) {
  try { return await fetch(url, { ...options, credentials: 'include' }) } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Нет связи. Проверьте интернет и повторите')
  }
}
