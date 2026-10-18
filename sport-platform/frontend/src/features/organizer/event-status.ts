import { statusLabel, statusTone, normalizeStatus } from '../../components/ui/status-dictionary'
import type { EventStatus } from '../../api/events.api'

export function normalizeEventStatus(status: EventStatus | null | undefined): string {
  return normalizeStatus(String(status ?? ''))
}

export const getEventStatusLabel = (status: EventStatus) => statusLabel('event', status)
export const getEventStatusTone = (status: EventStatus) => statusTone('event', status)

export function canPublishEvent(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'DRAFT'
}

export function canOpenRegistration(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'PUBLISHED'
}

export function canCloseRegistration(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'REGISTRATION_OPEN'
}

export function canCompleteEvent(status: EventStatus): boolean {
  const normalized = normalizeEventStatus(status)
  return normalized === 'REGISTRATION_CLOSED' || normalized === 'LIVE'
}