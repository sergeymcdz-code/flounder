import type { Registration } from './registration.types'

export function participantName(registration: Registration) {
  const snapshot = registration.participantSnapshot
  return registration.participantFullName || [snapshot?.lastName, snapshot?.firstName, snapshot?.middleName].filter(Boolean).join(' ') || 'Участник без имени'
}

export function eventLabel(registration: Registration) {
  const date = registration.eventStartAt
    ? new Intl.DateTimeFormat('ru-RU', { timeZone: registration.venueTimezone || 'UTC' }).format(new Date(registration.eventStartAt))
    : 'Дата не указана'
  return `${registration.eventTitle || 'Название не указано'} · ${date}`
}
