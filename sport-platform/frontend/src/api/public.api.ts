import type { Standings } from './standings.api'
import type {
  AthleteMatch,
  FieldViolation,
  Live,
  PublicEvent,
  RegistrationForm,
  RegistrationRequest,
  Results,
  ScheduleItem,
  StartList,
} from '../features/public/public.types'

export class PublicApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly violations: FieldViolation[]
  constructor(
    status: number,
    message: string,
    code?: string,
    violations: FieldViolation[] = [],
  ) {
    super(message)
    this.name = 'PublicApiError'
    this.status = status
    this.code = code
    this.violations = violations
  }
}

async function request<T>(
  slug: string,
  resource = '',
  options: RequestInit = {},
): Promise<T> {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  const response = await fetch(
    `${base}/api/v1/public/events/${encodeURIComponent(slug)}${resource}`,
    { ...options, headers },
  )
  if (response.ok)
    window.dispatchEvent(
      new CustomEvent('flounder:public-cache', {
        detail: {
          path: `${slug}${resource}`,
          cached: response.headers.get('X-Flounder-Cached') === 'true',
        },
      }),
    )
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as {
      code?: string
      message?: string
      violations?: FieldViolation[]
    }
    throw new PublicApiError(
      response.status,
      error.message ?? 'Не удалось загрузить данные. Попробуйте ещё раз.',
      error.code,
      error.violations,
    )
  }
  if (
    response.status === 204 ||
    !response.headers.get('content-type')?.includes('application/json')
  ) {
    if (options.method === 'POST') return undefined as T
    throw new PublicApiError(
      response.status,
      'Сервер вернул некорректный ответ. Попробуйте ещё раз.',
    )
  }
  return response.json() as Promise<T>
}

export const publicApi = {
  standings: (slug: string, signal?: AbortSignal) =>
    request<Standings>(slug, '/standings', { signal }),
  event: (slug: string, signal?: AbortSignal) =>
    request<PublicEvent>(slug, '', { signal }),
  startList: (slug: string, signal?: AbortSignal) =>
    request<StartList>(slug, '/start-list', { signal }),
  schedule: (slug: string, signal?: AbortSignal) =>
    request<ScheduleItem[]>(slug, '/schedule', { signal }),
  live: (slug: string, signal?: AbortSignal) =>
    request<Live>(slug, '/live', { signal }),
  results: (slug: string, signal?: AbortSignal) =>
    request<Results>(slug, '/results', { signal }),
  athletes: (slug: string, query: string, signal?: AbortSignal) =>
    request<AthleteMatch[]>(slug, `/athletes?q=${encodeURIComponent(query)}`, {
      signal,
    }),
  registrationForm: (slug: string, signal?: AbortSignal) =>
    request<RegistrationForm>(slug, '/registration-form', { signal }),
  // The spec does not define the success DTO. Do not invent a response shape.
  register: (slug: string, body: RegistrationRequest, token: string) =>
    request<unknown>(slug, '/registrations', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { Authorization: `Bearer ${token}` },
    }),
}
