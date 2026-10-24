import { MemoryRouter } from 'react-router'
import { ToastProvider } from '../../components/ui/Toast'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeEach, expect, it, vi } from 'vitest'
import { server } from '../../test/setup'
import { OrganizerHeatsPage } from './OrganizerHeatsPage'
import { useOrganizerEvent, type EventContext } from './organizer-event-context'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
vi.mock('./organizer-event-context', async (importOriginal) => ({
  ...await importOriginal<typeof import('./organizer-event-context')>(),
  useOrganizerEvent: vi.fn(),
}))
afterAll(() => vi.unstubAllEnvs())
afterEach(() => vi.unstubAllGlobals())

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
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  vi.clearAllMocks()
  vi.mocked(useOrganizerEvent).mockReturnValue(context)
})

it('QA05-01: circle seeding is removed from heat actions and kept for the program editor', () => {
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
})

it.each([
  ['Всё соревнование', 'events', 'event'],
  ['50 м', 'event-disciplines', 'discipline'],
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
  await userEvent.click(screen.getByText('Сформировать заплывы ▾'))
  await userEvent.click(screen.getByRole('button', { name: button }))
  await waitFor(() => expect(context.saveGeneration).toHaveBeenCalledOnce())
  expect(requestBody).toHaveBeenCalledExactlyOnceWith({ force: false, circleSeedTopHeats: false })
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
})

it('does not publish before confirmation, then shows a toast with a public link', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [{ id: 'heat', eventDisciplineId: 'discipline', label: '1', sequenceNumber: 1, status: 'DRAFT', scheduledAt: null, entries: [] }] })
  const publish = vi.fn()
  server.use(http.get('*/api/v1/event-disciplines/discipline/competition-units/validate', () => HttpResponse.json({ violations: [] })))
  server.use(http.post('*/api/v1/events/event/competition-units/publish', () => { publish(); return HttpResponse.json({}) }))
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать протокол' }))
  expect(publish).not.toHaveBeenCalled()
  await userEvent.click(await screen.findByRole('button', { name: 'Опубликовать' }))
  await waitFor(() => expect(publish).toHaveBeenCalledOnce())
  expect(await screen.findByRole('link', { name: 'Открыть публичную страницу ↗' })).toHaveAttribute('href', '/e/cup/start-list')
})


it('assigns bibs from the dialog and reports the assigned count', async () => {
  const body = vi.fn()
  server.use(http.post('*/api/v1/events/event/bib-numbers/assign', async ({ request }) => {
    body(await request.json())
    return HttpResponse.json({ assigned: 12, kept: 3, total: 15 })
  }))
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Печать ▾' }))
  await userEvent.click(screen.getByRole('button', { name: 'Стартовые номера…' }))
  await userEvent.selectOptions(screen.getByLabelText('Порядок'), 'BY_NAME')
  const start = screen.getByLabelText('Начать с')
  await userEvent.clear(start)
  await userEvent.type(start, '10')
  await userEvent.click(screen.getByLabelText('Перенумеровать всех'))
  await userEvent.click(screen.getByRole('button', { name: 'Назначить номера' }))
  await screen.findByText('Назначено 12, сохранено 3, всего 15')
  expect(body).toHaveBeenCalledWith({ mode: 'BY_NAME', startFrom: 10, reassign: true })
})

it('offers both card formats and filters print requests without disabling archived printing', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, event: { ...context.event, status: 'ARCHIVED' } })
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  expect(screen.getAllByRole('button', { name: 'Печать ▾' })).toHaveLength(1)
  await userEvent.click(screen.getByRole('button', { name: 'Печать ▾' }))
  expect(screen.getByRole('button', { name: 'Стартовые номера…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Карточки заплывов (A5)' })).toBeEnabled()
  expect(screen.getByRole('button', { name: 'Карточки заплывов (2 на A4)' })).toBeEnabled()
  await userEvent.selectOptions(screen.getByLabelText('Вид'), 'discipline')
  const requested = vi.fn()
  server.use(http.get('*/api/v1/events/event/documents/heat-cards.pdf', ({ request }) => {
    requested(new URL(request.url).searchParams.get('disciplineId'), new URL(request.url).searchParams.get('format'))
    return new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })
  }))
  await userEvent.click(screen.getByRole('button', { name: 'Карточки заплывов (2 на A4)' }))
  await waitFor(() => expect(requested).toHaveBeenCalledWith('discipline', 'A4x2'))
})

it('uses a valid query string for card printing without filters', async () => {
  const requested = vi.fn()
  server.use(http.get('*/api/v1/events/event/documents/heat-cards.pdf', ({ request }) => {
    requested(new URL(request.url).search)
    return new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } })
  }))
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Печать ▾' }))
  await userEvent.click(screen.getByRole('button', { name: 'Карточки заплывов (A5)' }))
  await waitFor(() => expect(requested).toHaveBeenCalledWith('?format=A5'))
})

it('disables schedule shifting for archived events with a read-only explanation', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, event: { ...context.event, status: 'ARCHIVED' }, heats: [{
    id: 'heat', eventDisciplineId: 'discipline', label: 'Заплыв 1', sequenceNumber: 1,
    scheduledAt: '2026-10-19T06:00:00Z', status: 'PUBLISHED', entries: [],
  }] })
  render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Расписание ▾' }))
  const button = screen.getByRole('button', { name: 'Сдвинуть расписание…' })
  expect(button).toBeDisabled()
  expect(button).toHaveAttribute('title', 'Архивное событие доступно только для чтения')
})

const draftHeat = { id: 'heat', eventDisciplineId: 'discipline', label: '1', sequenceNumber: 1, status: 'DRAFT', scheduledAt: '2026-10-17T06:00:00Z', entries: [] }
function renderHeats() {
  return render(<MemoryRouter><ToastProvider><OrganizerHeatsPage /></ToastProvider></MemoryRouter>)
}

it('blocks publication on validator errors and allows retry after correction', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [draftHeat] })
  let invalid = true
  const publish = vi.fn()
  server.use(
    http.get('*/api/v1/event-disciplines/discipline/competition-units/validate', () => HttpResponse.json({ violations: invalid ? [{ code: 'lane.conflict', severity: 'ERROR', message: 'Дорожка занята' }] : [] })),
    http.post('*/api/v1/events/event/competition-units/publish', () => { publish(); return HttpResponse.json({}) }),
  )
  renderHeats()
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать протокол' }))
  expect(await screen.findByText('Ошибки блокируют публикацию.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Опубликовать' })).not.toBeInTheDocument()
  expect(publish).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Опубликовать протокол' })).toBeDisabled()
  invalid = false
  await userEvent.click(screen.getByRole('button', { name: 'Повторить проверку' }))
  await screen.findByText('Нарушений не найдено.')
  await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }))
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать протокол' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Опубликовать' }))
  await waitFor(() => expect(publish).toHaveBeenCalledOnce())
})

it('permits publication with warnings and preserves confirmation', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [draftHeat] })
  server.use(http.get('*/api/v1/event-disciplines/discipline/competition-units/validate', () => HttpResponse.json({ violations: [{ code: 'small', severity: 'WARNING', message: 'Мало участников' }] })))
  renderHeats()
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать протокол' }))
  expect(await screen.findByRole('dialog')).toHaveAccessibleName('Опубликовать стартовый протокол?')
})

it('keeps discipline actions outside summaries and restores popover focus on Escape', async () => {
  renderHeats()
  const menu = screen.getByRole('button', { name: 'Действия вида 50 м' })
  expect(menu.closest('summary')).toBeNull()
  await userEvent.click(menu)
  expect(within(screen.getByRole('region', { name: 'Действия вида 50 м' })).getByRole('button', { name: 'Пересобрать' })).toBeVisible()
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('region', { name: 'Действия вида 50 м' })).not.toBeInTheDocument()
  expect(menu).toHaveFocus()
})

it('explains empty, busy and error states while keeping existing heats visible', () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [draftHeat], busy: true, error: 'Сервер ответил ошибкой (500). Повторите попытку.' })
  renderHeats()
  expect(screen.getByRole('article', { name: 'Заплыв 1' })).toBeVisible()
  expect(screen.getByRole('alert')).toHaveTextContent('Сервер ответил ошибкой (500)')
  expect(screen.getByRole('button', { name: 'Опубликовать протокол' })).toHaveAttribute('title', 'Дождитесь завершения текущего действия')
})

it('links to the program when disciplines are missing and has one primary when empty', () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, event: { ...context.event, disciplines: [] } })
  const { container } = renderHeats()
  expect(screen.getByRole('link', { name: 'Открыть программу' })).toHaveAttribute('href', '/organizer/events/event/program')
  expect(container.querySelectorAll('.fl-btn-primary')).toHaveLength(1)
  expect(screen.getByRole('button', { name: 'Сформировать заплывы' })).toBeDisabled()
})

it('confirms before force-regenerating published heats', async () => {
  vi.mocked(useOrganizerEvent).mockReturnValue({ ...context, heats: [{ ...draftHeat, status: 'PUBLISHED' }] })
  const requestBody = vi.fn()
  server.use(http.post('*/api/v1/events/event/generate-competition-units', async ({ request }) => {
    requestBody(await request.json())
    return HttpResponse.json({ disciplines: [], durationMs: 12 })
  }))
  renderHeats()
  await userEvent.click(screen.getByRole('button', { name: 'Сформировать заплывы ▾' }))
  await userEvent.click(screen.getByRole('button', { name: 'Всё соревнование' }))
  expect(requestBody).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Подтвердить' }))
  await waitFor(() => expect(requestBody).toHaveBeenCalledExactlyOnceWith({ force: true, circleSeedTopHeats: false }))
})
