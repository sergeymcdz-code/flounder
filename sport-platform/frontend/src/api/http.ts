export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ||
  ((import.meta.env.VITE_API_MOCK === 'true' || import.meta.env.PROD) ? window.location.origin : '')

export class ApiError extends Error {
  public readonly status: number
  public readonly code?: string
  public readonly violations: FieldViolation[]

  constructor(
    status: number,
    message: string,
    code?: string,
    violations: FieldViolation[] = [],
  ) {
    super(message)

    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.violations = violations ?? []
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
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  if (!API_BASE_URL) {
    throw new Error('Не задан адрес API (VITE_API_BASE_URL).')
  }

  const { accessToken, ...requestOptions } = options
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

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers,
  })

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

export async function apiDownload(path: string, accessToken: string): Promise<Blob> {
  if (!API_BASE_URL) throw new Error('Не задан адрес API (VITE_API_BASE_URL).')
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as ApiErrorResponse
    throw new ApiError(
      response.status,
      payload.message ?? 'Не удалось скачать шаблон.',
      payload.code,
      payload.violations,
    )
  }
  return response.blob()
}
