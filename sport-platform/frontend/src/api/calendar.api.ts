import { PublicApiError } from './public.api'
import type { Calendar, PublicOrganization } from '../features/public/calendar.types'
const base = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
export const organizationLogo = (slug: string) => `${base}/api/v1/public/organizations/${encodeURIComponent(slug)}/logo`
async function read<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${base}/api/v1/public/${path}`, { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new PublicApiError(response.status, error.message ?? 'Не удалось загрузить данные.', error.code)
  }
  return response.json() as Promise<T>
}
export const calendarApi = {
  calendar: (query: URLSearchParams, signal?: AbortSignal) => read<Calendar>(`calendar?${query}`, signal),
  organization: (slug: string, signal?: AbortSignal) => read<PublicOrganization>(`organizations/${encodeURIComponent(slug)}`, signal),
}
