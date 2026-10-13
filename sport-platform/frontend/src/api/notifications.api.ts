import { apiFetch } from './http'

export type NotificationSetting = { templateCode: string; enabled: boolean }
export type NotificationItem = {
  id: string; recipientEmail: string; templateCode: string; status: 'PENDING' | 'SENT' | 'FAILED'
  attempts: number; lastError?: string; createdAt: string; sentAt?: string
}
export type NotificationPage = { items: NotificationItem[]; page: number; totalElements: number; totalPages: number }
export const templateLabels: Record<string, string> = {
  registration_submitted: 'Заявка принята', registration_confirmed: 'Заявка подтверждена',
  registration_rejected: 'Заявка отклонена', start_list_published: 'Стартовый протокол опубликован',
  results_published: 'Результаты опубликованы', password_reset: 'Восстановление пароля',
}
export const statusLabels = { PENDING: 'Ожидает отправки', SENT: 'Отправлено', FAILED: 'Ошибка отправки' }
export const getNotificationSettings = (token: string, id: string) =>
  apiFetch<NotificationSetting[]>(`/api/v1/organizations/${encodeURIComponent(id)}/notification-settings`, { accessToken: token })
export const putNotificationSettings = (token: string, id: string, body: NotificationSetting[]) =>
  apiFetch<NotificationSetting[]>(`/api/v1/organizations/${encodeURIComponent(id)}/notification-settings`, {
    accessToken: token, method: 'PUT', body: JSON.stringify(body),
  })
export const getNotifications = (token: string, eventId: string, status: string, page: number) => {
  const query = new URLSearchParams({ page: String(page) })
  if (status) query.set('status', status)
  return apiFetch<NotificationPage>(`/api/v1/events/${encodeURIComponent(eventId)}/notifications?${query}`, { accessToken: token })
}
