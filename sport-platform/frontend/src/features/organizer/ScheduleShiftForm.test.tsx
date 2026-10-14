import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterAll, expect, it, vi } from 'vitest'
import { server } from '../../test/setup'
import { ScheduleShiftForm } from './ScheduleShiftForm'
import type { Heat } from '../../api/organizer.types'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())
const heats: Heat[] = [{ id: 'one', eventDisciplineId: 'd', label: 'Заплыв 1', sequenceNumber: 1,
  scheduledAt: '2026-10-17T06:00:00Z', status: 'PUBLISHED', entries: [] }]

it('shows the server preview in venue time and confirms the requested shift', async () => {
  const onApply = vi.fn()
  server.use(http.post('*/api/v1/events/event/schedule/shift/preview', async ({ request }) => {
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(await request.json()).toEqual({ fromUnitId: 'one', deltaSec: 1200 })
    return HttpResponse.json({ units: [{ id: 'one', previousScheduledAt: heats[0].scheduledAt, scheduledAt: '2026-10-17T06:20:00Z' }], shiftedAt: null })
  }))
  render(<ScheduleShiftForm eventId="event" heats={heats} accessToken="test-token" timeZone="Europe/Samara" busy={false} onApply={onApply} onClose={vi.fn()} />)
  expect(screen.getByRole('button', { name: 'Подтвердить сдвиг' })).toBeDisabled()
  expect(await screen.findByText('Первый заплыв: 10:00 → 10:20')).toBeInTheDocument()
  expect(screen.getByText('Последний заплыв: 10:00 → 10:20')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Подтвердить сдвиг' }))
  expect(onApply).toHaveBeenCalledWith('one', 1200)
})

it('disables stale preview after a change and displays backend validation errors', async () => {
  const onApply = vi.fn()
  server.use(http.post('*/api/v1/events/event/schedule/shift/preview', async ({ request }) => {
    const body = await request.json() as { deltaSec: number }
    return body.deltaSec === 1200
      ? HttpResponse.json({ units: [{ id: 'one', previousScheduledAt: heats[0].scheduledAt, scheduledAt: '2026-10-17T06:20:00Z' }], shiftedAt: null })
      : HttpResponse.json({ message: 'Сдвиг пересекает предыдущий заплыв или начало сессии' }, { status: 400 })
  }))
  render(<ScheduleShiftForm eventId="event" heats={heats} accessToken="test-token" busy={false} onApply={onApply} onClose={vi.fn()} />)
  const confirm = screen.getByRole('button', { name: 'Подтвердить сдвиг' })
  await waitFor(() => expect(confirm).toBeEnabled())
  await userEvent.clear(screen.getByRole('spinbutton'))
  expect(confirm).toBeDisabled()
  await userEvent.type(screen.getByRole('spinbutton'), '-2')
  expect(await screen.findByRole('alert')).toHaveTextContent('Сдвиг пересекает предыдущий заплыв')
  expect(confirm).toBeDisabled()
  expect(onApply).not.toHaveBeenCalled()
})
