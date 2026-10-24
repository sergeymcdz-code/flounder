import { statusLabel } from '../../components/ui/status-dictionary'
import { ApiError } from '../../api/http'

export function resultErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Сессия истекла. Войдите в систему снова.'
    if (error.status === 403) return 'У вашей учётной записи нет прав для работы с этими результатами.'
    if (error.status === 404) return 'Событие, вид программы или заплыв не найден. Вернитесь к списку заплывов.'
    return error.message
  }
  return fallback
}

export const heatStatusLabel = (status: string) => statusLabel('heat', status)

export const resultStatusDictionary = {
  NT: { hint: 'Нет времени (результат не зафиксирован)', legend: 'нет времени (результат не зафиксирован)' },
  DNS: { hint: 'Не стартовал', legend: 'не стартовал' },
  DNF: { hint: 'Не финишировал', legend: 'не финишировал' },
  DSQ: { hint: 'Дисквалифицирован', legend: 'дисквалифицирован' },
  CORRECTED: { hint: 'Результат исправлен после публикации', legend: 'результат исправлен после публикации' },
} as const

export type ResultStatusCode = keyof typeof resultStatusDictionary

export function resultStatusCode(status: string | null | undefined): ResultStatusCode | undefined {
  const code = status?.trim().toUpperCase() ?? ''
  const aliases: Record<string, ResultStatusCode> = {
    DID_NOT_START: 'DNS', DID_NOT_FINISH: 'DNF', DISQUALIFIED: 'DSQ',
  }
  return aliases[code] ?? (Object.hasOwn(resultStatusDictionary, code) ? code as ResultStatusCode : undefined)
}
