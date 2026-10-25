import { normalizeStatus } from '../../components/ui/status-dictionary'

let clock = () => Date.now()

/**
 * Test/demo seam: mock data (VITE_API_MOCK, Vitest fixtures) are dated around one demo day,
 * so those environments pin «today» for this guard only. Production always uses the real clock.
 */
export function setEventPhaseClock(next: (() => number) | null) {
  clock = next ?? (() => Date.now())
}

/** «Today» of the mock fixtures: the demo event day before its end. */
export const DEMO_TODAY = Date.parse('2026-10-14T12:00:00+03:00')

const finalStatuses = new Set(['COMPLETED', 'FINISHED', 'ARCHIVED'])

/** End of the event as a timestamp; a bare date (calendar cards) counts until the end of that day. */
function endTime(value: string | null | undefined) {
  if (!value) return null
  // UTC end-of-day intentionally leaves a grace period for venues east of UTC.
  const time = Date.parse(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T23:59:59Z` : value)
  return Number.isNaN(time) ? null : time
}

/**
 * UX-24 (UI-103): a defensive frontend guard until the server advances statuses (OPS-1 `--advance`).
 * An event whose end date has passed is shown as finished even if its status was not moved.
 */
export function endedByDate(status: string | null | undefined, endAt: string | null | undefined, now = clock()) {
  const code = normalizeStatus(String(status ?? ''))
  // LIVE from the server takes precedence over the planned end time (status recovery is OPS-1).
  if (finalStatuses.has(code) || code === 'CANCELLED' || code === 'LIVE') return false
  const end = endTime(endAt)
  return end !== null && end < now
}

/** Status for public badges: «Завершено» once the event is over by status or by date. */
export function publicEventStatus(status: string, endAt: string | null | undefined, now = clock()) {
  return normalizeStatus(status) === 'FINISHED' || endedByDate(status, endAt, now) ? 'COMPLETED' : status
}

/** Finished events no longer change, so live and results polling stops. */
export function isEventOver(status: string | null | undefined, endAt: string | null | undefined, now = clock()) {
  return finalStatuses.has(normalizeStatus(String(status ?? ''))) || endedByDate(status, endAt, now)
}
