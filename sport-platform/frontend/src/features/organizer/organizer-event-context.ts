import { useOutletContext } from 'react-router'
import type {
  OrganizerEventDetails,
  OrganizerRegistration,
  Session,
  Heat,
} from '../../api/organizer.types'

export type EventContext = {
  event: OrganizerEventDetails
  registrations: OrganizerRegistration[]
  sessions: Session[]
  heats: Heat[]
  accessToken: string
  error: string
  busy: boolean
  refresh: () => Promise<void>
  run: (action: () => Promise<unknown>) => Promise<boolean>
}
export const useOrganizerEvent = () => useOutletContext<EventContext>()
export function formatDate(value: string | null) {
  return value
    ? new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Europe/Moscow',
      }).format(new Date(value))
    : 'Не назначено'
}
export function formatSeed(value: number | null | undefined) {
  if (value == null) return '—'
  return `${Math.floor(value / 60000)
    .toString()
    .padStart(2, '0')}:${((value % 60000) / 1000).toFixed(2).padStart(5, '0')}`
}
export const statusLabels: Record<string, string> = {
  DRAFT: 'Черновик',
  PUBLISHED: 'Опубликовано',
  REGISTRATIONOPEN: 'Регистрация открыта',
  REGISTRATIONCLOSED: 'Регистрация закрыта',
  REGISTRATION_OPEN: 'Регистрация открыта',
  REGISTRATION_CLOSED: 'Регистрация закрыта',
  LIVE: 'Идёт соревнование',
  COMPLETED: 'Завершено',
  ARCHIVED: 'В архиве',
  SUBMITTED: 'На рассмотрении',
  CONFIRMED: 'Подтверждена',
  NEEDS_CORRECTION: 'На исправлении',
  REJECTED: 'Отклонена',
  CANCELLED: 'Отменена',
  WITHDRAWN: 'Снята',
  ADMITTED: 'Допущена',
  PAID: 'Оплачено',
  PENDING: 'Ожидает оплаты',
  NOT_REQUIRED: 'Без оплаты',
  IN_PROGRESS: 'Идёт заплыв',
  FINISHED: 'Завершён',
  SCHEDULED: 'В расписании',
  REFUNDED: 'Возврат оплаты',
  IMPORT: 'Импорт',
  WEB: 'Сайт',
  MANUAL: 'Вручную',
}

export const isHeatPublished = (status: string) =>
  ['PUBLISHED', 'IN_PROGRESS', 'FINISHED', 'COMPLETED'].includes(status)
