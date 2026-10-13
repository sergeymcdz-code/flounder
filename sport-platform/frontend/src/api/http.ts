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
      payload?.message ?? `Ошибка запроса: HTTP ${response.status}`,
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

function retryAfterSeconds(response: Response): number | undefined {
  const value = response.headers.get('Retry-After')
  if (!value || !/^\d+$/.test(value)) return undefined
  const seconds = Number(value)
  return Number.isFinite(seconds) ? seconds : undefined
}

let refreshInFlight: Promise<string> | null = null

async function refreshAccessToken(failedToken: string): Promise<string> {
  const session = getAuthState().session
  if (session && session.accessToken !== failedToken) return session.accessToken
  if (refreshInFlight) return refreshInFlight
  if (!session?.refreshToken) {
    if (session?.accessToken === failedToken) setAuthSession(null, SESSION_EXPIRED)
    throw new ApiError(401, SESSION_EXPIRED, 'identity.refresh_token_invalid')
  }
  refreshInFlight = (async () => {
    try {
      const response = await apiFetch<LoginResponse>('/api/v1/auth/refresh', {
        method: 'POST', body: JSON.stringify({ refreshToken: session.refreshToken }), skipRefresh: true,
      })
      if (getAuthState().session !== session) {
        // Revoke a rotated token if logout or a different login won the race.
        void apiFetch('/api/v1/auth/logout', {
          method: 'POST', body: JSON.stringify({ refreshToken: response.refreshToken }), skipRefresh: true,
        }).catch(() => {})
        throw new ApiError(401, SESSION_EXPIRED)
      }
      setAuthSession({
        accessToken: response.accessToken, refreshToken: response.refreshToken,
        tokenType: response.tokenType, expiresAt: Date.now() + response.expiresIn * 1000,
      })
      return response.accessToken
    } catch {
      if (getAuthState().session === session) setAuthSession(null, SESSION_EXPIRED)
      throw new ApiError(401, SESSION_EXPIRED, 'identity.refresh_token_invalid')
    }
  })()
  try { return await refreshInFlight } finally { refreshInFlight = null }
}

async function authenticatedFetch(path: string, options: RequestInit, accessToken?: string | null, skipRefresh = false) {
  const response = await fetch(`${API_BASE_URL}${path}`, options)
  if (response.status !== 401 || !accessToken || skipRefresh) return response
  const token = await refreshAccessToken(accessToken)
  const headers = new Headers(options.headers)
  headers.set('Authorization', `Bearer ${token}`)
  const retried = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
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
      response.status, payload.message ?? 'Не удалось скачать файл.', payload.code, payload.violations, retryAfterSeconds(response),
    )
  }
  return response.blob()
}
