import { ApiError } from '../../api/http'

export const adminError = (error: unknown) => error instanceof ApiError ? error.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.'
const roleLabels: Record<string, string> = {
  platform_admin: 'Администратор платформы', organizer: 'Организатор', operator: 'Оператор', judge: 'Судья', participant: 'Участник',
}
export const roleLabel = (code: string, fallback?: string) => roleLabels[code] ?? fallback ?? code
