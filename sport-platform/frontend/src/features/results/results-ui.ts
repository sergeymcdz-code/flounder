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
