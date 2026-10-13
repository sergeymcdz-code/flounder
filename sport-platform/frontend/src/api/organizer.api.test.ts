import { http, HttpResponse } from 'msw'
import { afterAll, expect, it, vi } from 'vitest'
import { server } from '../test/setup'
import { getOrganizerEvent } from './organizer.api'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())

it('QA05-02: loads the event when the venues directory returns 403', async () => {
  server.use(
    http.get('*/api/v1/events/qa05-event', () =>
      HttpResponse.json({ id: 'qa05-event', title: 'Кубок', venueId: 'venue-1' }),
    ),
    http.get('*/api/v1/events/qa05-event/disciplines', () =>
      HttpResponse.json([{ id: 'discipline-1', name: '50 м' }]),
    ),
    http.get('*/api/v1/venues', () =>
      HttpResponse.json({ code: 'access_denied', message: 'Нет доступа' }, { status: 403 }),
    ),
  )
  await expect(getOrganizerEvent('qa05-event', 'test-token')).resolves.toMatchObject({
    id: 'qa05-event',
    title: 'Кубок',
    disciplines: [{ id: 'discipline-1', name: '50 м' }],
    venue: undefined,
  })
})
