export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'
export type StatusKind = 'event' | 'heat' | 'registration' | 'payment' | 'document' | 'result'
type Dictionary = Record<string, [string, StatusTone]>
export const statusDictionary: Record<StatusKind, Dictionary> = {
  event: { DRAFT: ['Черновик', 'neutral'], PUBLISHED: ['Опубликовано', 'success'], REGISTRATION_OPEN: ['Регистрация открыта', 'success'], REGISTRATION_CLOSED: ['Регистрация закрыта', 'info'], LIVE: ['Идёт', 'danger'], COMPLETED: ['Завершено', 'neutral'], ARCHIVED: ['Архив', 'neutral'] },
  heat: { DRAFT: ['Черновик', 'neutral'], SCHEDULED: ['В расписании', 'info'], PUBLISHED: ['Опубликован', 'success'], IN_PROGRESS: ['Идёт', 'danger'], FINISHED: ['Завершён', 'neutral'], COMPLETED: ['Завершён', 'neutral'], CANCELLED: ['Отменён', 'danger'] },
  registration: { SUBMITTED: ['На рассмотрении', 'info'], UNDER_REVIEW: ['На рассмотрении', 'info'], CONFIRMED: ['Подтверждена', 'success'], PAID: ['Оплачена', 'success'], ADMITTED: ['Допущена', 'success'], WAITLISTED: ['Лист ожидания', 'warning'], NEEDS_CORRECTION: ['На исправлении', 'warning'], REJECTED: ['Отклонена', 'danger'], WITHDRAWN: ['Снята', 'neutral'], CANCELLED: ['Отменена', 'danger'] },
  payment: { WAIVED: ['Освобождён', 'success'], PENDING: ['Ожидает оплаты', 'info'], PAID: ['Оплачено', 'success'], NOT_REQUIRED: ['Без оплаты', 'neutral'], REFUNDED: ['Возврат', 'neutral'] },
  document: { NOT_UPLOADED: ['Нет', 'neutral'], PENDING: ['На проверке', 'info'], ACCEPTED: ['Принят', 'success'], REJECTED: ['Отклонён', 'danger'] },
  result: { VALID: ['—', 'neutral'], PENDING: ['Ожидает', 'neutral'], DNS: ['DNS', 'neutral'], DNF: ['DNF', 'neutral'], DSQ: ['DSQ', 'danger'], CORRECTED: ['исправлено', 'warning'] },
}
export function normalizeStatus(code: string) {
  const normalized = code.trim().toUpperCase()
  return ({ REGISTRATIONOPEN: 'REGISTRATION_OPEN', REGISTRATIONCLOSED: 'REGISTRATION_CLOSED', STARTED: 'IN_PROGRESS' } as Record<string, string>)[normalized] ?? normalized
}
export const statusLabel = (kind: StatusKind, code: string) => statusDictionary[kind][normalizeStatus(code)]?.[0] ?? code
export const statusTone = (kind: StatusKind, code: string): StatusTone => statusDictionary[kind][normalizeStatus(code)]?.[1] ?? 'neutral'
export const statusLabelsFor = (kind: StatusKind): Record<string, string> => Object.fromEntries(Object.entries(statusDictionary[kind]).map(([code, [label]]) => [code, label]))
