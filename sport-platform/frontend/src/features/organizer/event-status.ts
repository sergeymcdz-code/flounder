import type { EventStatus } from '../../api/events.api'

export function normalizeEventStatus(status: EventStatus | null | undefined): string {
  return String(status ?? '').trim().toUpperCase()
}

export function getEventStatusLabel(status: EventStatus): string {
  switch (normalizeEventStatus(status)) {
    case 'DRAFT':
      return 'Черновик'
    case 'PUBLISHED':
      return 'Опубликовано'
    case 'REGISTRATIONOPEN':
      return 'Регистрация открыта'
    case 'REGISTRATIONCLOSED':
      return 'Регистрация закрыта'
    case 'LIVE':
      return 'Идёт'
    case 'COMPLETED':
      return 'Завершено'
    case 'ARCHIVED':
      return 'Архив'
    default:
      return status
    }
}

export function getEventStatusTone(status: EventStatus): string {
  switch (normalizeEventStatus(status)) {
    case 'DRAFT':
      return 'draft'
    case 'PUBLISHED':
      return 'published'
    case 'REGISTRATIONOPEN':
      return 'open'
    case 'REGISTRATIONCLOSED':
      return 'closed'
    case 'LIVE':
      return 'live'
    case 'COMPLETED':
      return 'completed'
    case 'ARCHIVED':
      return 'archived'
    default:
      return 'default'
  }
}

export function canPublishEvent(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'DRAFT'
}

export function canOpenRegistration(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'PUBLISHED'
}

export function canCloseRegistration(status: EventStatus): boolean {
  return normalizeEventStatus(status) === 'REGISTRATIONOPEN'
}

export function canCompleteEvent(status: EventStatus): boolean {
  const normalized = normalizeEventStatus(status)
  return normalized === 'REGISTRATIONCLOSED' || normalized === 'LIVE'
}