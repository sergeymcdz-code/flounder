import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { server } from '../test/setup'
import { publicApi, PublicApiError } from './public.api'

it('preserves the backend error code and per-discipline violations', async () => {
  server.use(
    http.get('*/api/v1/public/events/demo', () =>
      HttpResponse.json(
        {
          code: 'event.unavailable',
          message: 'Событие недоступно.',
          violations: [
            { field: 'items[0].eventDisciplineId', message: 'Вид недоступен.' },
          ],
        },
        { status: 409 },
      ),
    ),
  )
  await expect(publicApi.event('demo')).rejects.toMatchObject({
    status: 409,
    code: 'event.unavailable',
    message: 'Событие недоступно.',
    violations: [
      { field: 'items[0].eventDisciplineId', message: 'Вид недоступен.' },
    ],
  })
})

it('reports an HTML fallback instead of leaving the public page blank', async () => {
  server.use(
    http.get(
      '*/api/v1/public/events/demo',
      () =>
        new HttpResponse('<html>App shell</html>', {
          headers: { 'Content-Type': 'text/html' },
        }),
    ),
  )
  await expect(publicApi.event('demo')).rejects.toBeInstanceOf(PublicApiError)
})

it('preserves the offline marker with revalidated public JSON and clears it on a network response', async () => {
  // Fetch exposes a revalidated browser HTTP-cache response as 200 with its JSON body.
  const events: unknown[] = []
  const listener = (event: Event) => events.push((event as CustomEvent).detail)
  window.addEventListener('flounder:public-cache', listener)
  try {
    for (const cached of [true, false]) {
      server.use(http.get('*/api/v1/public/events/cache-test/live', () =>
        HttpResponse.json({ next: [], updatedAt: '2026-10-15T00:00:00Z' }, {
          headers: {
            ETag: 'W/"public-live"',
            'Cache-Control': 'public, max-age=5, stale-while-revalidate=30',
            ...(cached ? { 'X-Flounder-Cached': 'true' } : {}),
          },
        })))
      await expect(publicApi.live('cache-test')).resolves.toMatchObject({ next: [] })
    }
    expect(events).toEqual([
      { path: 'cache-test/live', cached: true },
      { path: 'cache-test/live', cached: false },
    ])
  } finally {
    window.removeEventListener('flounder:public-cache', listener)
  }
})
