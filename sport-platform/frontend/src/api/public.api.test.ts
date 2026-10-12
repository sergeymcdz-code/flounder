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
