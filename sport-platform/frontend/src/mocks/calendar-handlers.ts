import { http, HttpResponse } from 'msw'
import { demoCalendar, demoOrganization } from './calendar-data'
function calendar(request: Request) {
  const query = new URL(request.url).searchParams
  const items = query.get('status') === 'past' ? [] : demoCalendar.items.filter(card =>
    (!query.get('city') || query.get('city') === card.city) && (!query.get('q') || card.title.toLowerCase().includes(query.get('q')!.toLowerCase())))
  const page = Number(query.get('page') ?? 0), size = Number(query.get('size') ?? 20)
  return { ...demoCalendar, items: items.slice(page * size, (page + 1) * size), total: items.length, page, size }
}
export const calendarHandlers = [
  http.get('*/api/v1/public/calendar', ({ request }) => HttpResponse.json(calendar(request))),
  http.get('*/api/v1/public/organizations/:slug', ({ params }) => params.slug === 'volna' ? HttpResponse.json(demoOrganization) : HttpResponse.json({ message: 'Организация не найдена' }, { status: 404 })),
  http.get('*/api/v1/public/organizations/:slug/events', ({ request }) => HttpResponse.json(calendar(request))),
]
