import { MemoryRouter } from 'react-router'
import { ToastProvider } from '../../components/ui/Toast'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterAll, beforeEach, expect, it, vi } from 'vitest'
import { server } from '../../test/setup'
import { OrganizerHeatsPage } from './OrganizerHeatsPage'
import { useOrganizerEvent, type EventContext } from './organizer-event-context'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
vi.mock('./organizer-event-context', async (importOriginal) => ({
  ...await importOriginal<typeof import('./organizer-event-context')>(),
  useOrganizerEvent: vi.fn(),
}))
afterAll(() => vi.unstubAllEnvs())

const context: EventContext = {
  event: {
    id: 'event', sportId: 'swim', title: 'Кубок', description: null,
    publicSlug: 'cup', status: 'DRAFT',
    registrationOpenAt: '2026-10-13T09:00:00Z', registrationCloseAt: '2026-10-16T09:00:00Z',
    eventStartAt: '2026-10-17T09:00:00Z', eventEndAt: '2026-10-17T18:00:00Z',
    disciplines: [{
      id: 'discipline', name: '50 м', competitionFormat: 'INDIVIDUAL',
      participantLimit: null, entryFeeAmount: 0, entryFeeCurrency: 'RUB', settingsJson: null,
      disciplineTemplateId: 'template', distanceM: 50, stroke: 'FREE', gender: 'M',
      seedingMode: 'BY_TIME_ALL', sessionId: null, orderInSession: 1, heatDurationSec: null,
      ageGroups: [], status: 'PUBLISHED',
    }],
  },
  registrations: [], sessions: [], heats: [], accessToken: 'test-token', error: '', busy: false,
  generationResults: {}, lastGeneration: null,
  saveGeneration: vi.fn(), registerDialog: () => () => {}, refresh: async () => {},
  clearError: vi.fn(),
  run: async (action) => { await action(); return true },
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useOrganizerEvent).mockReturnValue(context)
})

it('QA05-01: circle seeding is removed from heat actions and kept for the program editor', () => {
  render(<OrganizerHeatsPage />)
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
})

it.each([
  ['Всё соревнование', 'events', 'event'],
  ['Сформировать по виду', 'event-disciplines', 'discipline'],
])('keeps circle seeding off when clicking %s', async (button, scope, id) => {
  const requestBody = vi.fn()
  const result = { runId: 'run', heats: [], violations: [], excludedRegistrationIds: [], durationMs: 12 }
  server.use(http.post(`*/api/v1/${scope}/${id}/generate-competition-units`, async ({ request }) => {
    requestBody(await request.json())
    return HttpResponse.json(scope === 'events'
      ? { disciplines: [{ eventDisciplineId: 'discipline', result }], durationMs: 12 }
      : result)
  }))
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  if (button === 'Всё соревнование') await userEvent.click(screen.getByText('Сформировать заплывы ▾'))
  await userEvent.click(screen.getByRole('button', { name: button }))
  await waitFor(() => expect(context.saveGeneration).toHaveBeenCalledOnce())
  expect(requestBody).toHaveBeenCalledExactlyOnceWith({ force: false, circleSeedTopHeats: false })
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
})

it('does not publish before confirmation, then shows a toast with a public link', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [{ id: 'heat', eventDisciplineId: 'discipline', label: '1', sequenceNumber: 1, status: 'DRAFT', scheduledAt: null, entries: [] }] })
  const publish = vi.fn()
  server.use(http.post('*/api/v1/events/event/competition-units/publish', () => { publish(); return HttpResponse.json({}) }))
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать протокол' }))
  expect(publish).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать' }))
  await waitFor(() => expect(publish).toHaveBeenCalledOnce())
  expect(await screen.findByRole('link', { name: 'Открыть публичную страницу ↗' })).toHaveAttribute('href', '/e/cup/start-list')
})
