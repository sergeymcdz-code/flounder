import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { OrganizerResultsPage } from './OrganizerResultsPage'
import { createFixtures } from '../../mocks/fixtures'
import { changeCompetitionUnitPublication } from '../../api/results.api'
import { publishDisciplineResults } from '../../api/scheduling.api'

const state = vi.hoisted(() => ({ context: {} as ReturnType<typeof createFixtures> & Record<string, unknown>, live: { data: null as unknown, loading: false, error: '', errorStatus: undefined as number | undefined, retry: vi.fn() }, manage: true, toast: vi.fn() }))
vi.mock('./organizer-event-context', async original => ({ ...await original<typeof import('./organizer-event-context')>(), useOrganizerEvent: () => state.context }))
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ isOrganizer: state.manage }) }))
vi.mock('../../components/ui/Toast', () => ({ useToast: () => state.toast }))
vi.mock('../public/usePublicData', () => ({ LIVE_POLL_MS: 15000, usePublicData: () => state.live }))
vi.mock('../../api/results.api', () => ({ changeCompetitionUnitPublication: vi.fn() }))
vi.mock('../../api/scheduling.api', () => ({ publishDisciplineResults: vi.fn() }))
beforeEach(() => {
  vi.clearAllMocks()
  const fixtures = createFixtures()
  fixtures.event.disciplines = fixtures.event.disciplines.slice(0, 1)
  fixtures.heats = fixtures.heats.filter(h => h.eventDisciplineId === fixtures.event.disciplines[0].id).slice(0, 1)
  fixtures.sessions = fixtures.sessions.slice(0, 1)
  state.context = { ...fixtures, accessToken: 'test-token', busy: false, error: '', clearError: vi.fn(), run: vi.fn(async (action: () => Promise<unknown>) => { await action(); return true }) }
  state.live = { data: null, loading: false, error: '', errorStatus: undefined, retry: vi.fn() }
  state.manage = true
})
const renderPage = () => render(<MemoryRouter><OrganizerResultsPage /></MemoryRouter>)

it('shows reasons for unavailable publication, heat statuses and links to judging without a disabled current-heat action', () => {
  renderPage()
  expect(screen.getByText('Нет текущего заплыва')).toBeVisible()
  expect(screen.queryByRole('link', { name: 'Открыть текущий заплыв' })).toBeNull()
  expect(screen.getAllByRole('button', { name: 'Опубликовать результаты вида' }).every(button => button.hasAttribute('title'))).toBe(true)
  expect(screen.getAllByRole('link', { name: 'Открыть →' })[0]).toHaveAttribute('href', expect.stringContaining('/operator/competition-units/'))
})

it('requires every heat to be ready, confirms publication and keeps its API contract', async () => {
  const d = state.context.event.disciplines[0]
  state.context.event.disciplines = [d]
  state.context.heats = state.context.heats.filter(h => h.eventDisciplineId === d.id).map(h => ({ ...h, status: 'PUBLISHED', entries: h.entries.map(e => ({ ...e, rawValue: '00:32.15', resultStatus: 'VALID' })) }))
  renderPage()
  await userEvent.click(screen.getByRole('button', { name: 'Опубликовать результаты вида' }))
  expect(publishDisciplineResults).not.toHaveBeenCalled()
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^Опубликовать$/ }))
  await waitFor(() => expect(publishDisciplineResults).toHaveBeenCalledWith(state.context.heats, 'test-token'))
  expect(state.toast).toHaveBeenCalled()
})

it('confirms unpublication and highlights corrected entries', async () => {
  const d = state.context.event.disciplines[0]
  state.context.event.disciplines = [d]
  state.context.heats = state.context.heats.filter(h => h.eventDisciplineId === d.id).map(h => ({ ...h, status: 'FINISHED', entries: h.entries.map(e => ({ ...e, resultStatus: 'CORRECTED' })) }))
  renderPage()
  expect(screen.getAllByText('исправлено').length).toBeGreaterThan(0)
  await userEvent.click(screen.getByRole('button', { name: 'Снять публикацию' }))
  expect(changeCompetitionUnitPublication).not.toHaveBeenCalled()
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Снять публикацию' }))
  await waitFor(() => expect(changeCompetitionUnitPublication).toHaveBeenCalledWith(state.context.heats[0].id, false, 'test-token'))
})

it('preserves the heat list on live errors and offers a retry', async () => {
  state.live.error = 'Нет связи. Не удалось обновить данные.'
  renderPage()
  expect(screen.getByRole('alert')).toHaveTextContent('Нет связи')
  expect(screen.getAllByRole('link', { name: 'Открыть →' }).length).toBeGreaterThan(0)
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(state.live.retry).toHaveBeenCalledOnce()
})

it('shows loading and an actionable empty state', () => {
  state.live.loading = true
  state.context.heats = []
  renderPage()
  expect(screen.getByRole('status')).toHaveTextContent('Загружаем текущий заплыв')
  expect(screen.getByRole('link', { name: 'К заплывам' })).toHaveAttribute('href', expect.stringContaining('/heats'))
})

it('renders a neutral permission notice and hides management actions', () => {
  state.manage = false
  renderPage()
  expect(screen.getByText(/нужны права организатора/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Печать ▾' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Опубликовать результаты вида' })).toBeNull()
})

it('leaves a failed confirmation open and does not claim success', async () => {
  const d = state.context.event.disciplines[0]
  state.context.event.disciplines = [d]
  state.context.heats = state.context.heats.filter(h => h.eventDisciplineId === d.id).map(h => ({ ...h, status: 'FINISHED' }))
  state.context.run = vi.fn(async () => false)
  renderPage()
  await userEvent.click(screen.getByRole('button', { name: 'Снять публикацию' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Снять публикацию' }))
  expect(screen.getByRole('dialog')).toBeVisible()
  expect(state.toast).not.toHaveBeenCalled()
})


it('keeps publication disabled when another heat in the same discipline is incomplete', () => {
  const heat = state.context.heats[0]
  state.context.heats = [
    { ...heat, status: 'PUBLISHED', entries: heat.entries.map(e => ({ ...e, rawValue: '00:32.15', resultStatus: 'VALID' })) },
    { ...heat, id: 'incomplete', status: 'PUBLISHED' },
  ]
  renderPage()
  expect(screen.getByRole('button', { name: 'Опубликовать результаты вида' })).toBeDisabled()
  expect(screen.getByText('Не все заплывы завершены')).toBeVisible()
})

it('opens the current heat through the existing judging route', () => {
  const heat = state.context.heats[0]
  state.live.data = { currentUnit: { id: heat.id, number: 1, disciplineName: '50 м' } }
  renderPage()
  expect(screen.getByRole('link', { name: 'Открыть текущий заплыв' })).toHaveAttribute('href', `/operator/competition-units/${heat.id}?eventId=${state.context.event.id}`)
})


it('explains why completed results cannot be unpublished', () => {
  state.context.heats = state.context.heats.map(h => ({ ...h, status: 'COMPLETED' }))
  renderPage()
  expect(screen.getByRole('button', { name: 'Снять публикацию' })).toBeDisabled()
  expect(screen.getByText('Результаты завершённого соревнования нельзя снять с публикации')).toBeVisible()
})
