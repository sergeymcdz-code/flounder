import { http, HttpResponse } from 'msw'
import { templateLabels, type NotificationSetting } from '../api/notifications.api'
const settings = new Map<string, NotificationSetting[]>()
export const notificationHandlers = [
  http.get('*/api/v1/organizations/:id/notification-settings', ({ params }) =>
    HttpResponse.json(settings.get(String(params.id)) ?? Object.keys(templateLabels).filter(code => code !== 'password_reset')
      .map(templateCode => ({ templateCode, enabled: true })))),
  http.put('*/api/v1/organizations/:id/notification-settings', async ({ params, request }) => {
    const rows = await request.json() as NotificationSetting[]
    settings.set(String(params.id), rows)
    return HttpResponse.json(rows)
  }),
  http.get('*/api/v1/events/:eventId/notifications', ({ request }) => HttpResponse.json({
    items: [], page: Number(new URL(request.url).searchParams.get('page') ?? 0), totalElements: 0, totalPages: 0,
  })),
]
