import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { server } from '../../test/setup'
import { demoEvent } from '../../mocks/demo-data'
import { SchedulePage } from './SchedulePage'
import type { ScheduleItem } from './public.types'

function renderSchedule() {
  return render(<MemoryRouter><Routes><Route element={<Outlet context={{ event: demoEvent }} />}><Route index element={<SchedulePage />} /></Route></Routes></MemoryRouter>)
}
const path = '*/api/v1/public/events/demo/schedule'
const row: ScheduleItem = { sessionName: 'Утро', disciplineName: '50 м вольный стиль', heatNumber: 1, sequenceInSession: 1, scheduledAt: '2026-10-15T07:00:00Z', status: 'IN_PROGRESS' }

it('announces loading, groups sessions, sorts heats and shows a shift warning', async () => {
  let release!: (response: Response) => void
  server.use(http.get(path, () => new Promise<Response>(resolve => { release = resolve })))
  renderSchedule()
  expect(screen.getByRole('status', { name: 'Загружаем расписание…' })).toBeInTheDocument()
  await waitFor(() => expect(release).toBeTypeOf('function'))
  release(HttpResponse.json([{ ...row, heatNumber: 2, sequenceInSession: 2, status: 'SCHEDULED' }, { ...row, scheduleShiftedAt: '2026-10-15T07:05:00Z' }]))
  expect(await screen.findByText('Расписание сдвинуто в 11:05')).toBeInTheDocument()
  expect(screen.getAllByText(/Заплыв [12]/).map(e => e.textContent)).toEqual(['11:00 · Заплыв 1', '11:00 · Заплыв 2'])
  expect(screen.getByText('Идёт')).toHaveAttribute('data-status', 'IN_PROGRESS')
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
})

it('offers an event link when the schedule is empty', async () => {
  server.use(http.get(path, () => HttpResponse.json([])))
  renderSchedule()
  expect(await screen.findByText('Расписание ещё не опубликовано')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'К соревнованию' })).toHaveAttribute('href', '/e/demo')
})

it('distinguishes HTTP errors, retries and keeps data after a failed refresh', async () => {
  let calls = 0
  server.use(http.get(path, () => ++calls === 2 ? HttpResponse.json([row]) : HttpResponse.json({ message: 'Повторите позже' }, { status: 500 })))
  renderSchedule()
  expect(await screen.findByRole('alert')).toHaveTextContent('Сервер ответил ошибкой (500)')
  fireEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText('11:00 · Заплыв 1')).toBeInTheDocument()
  fireEvent(window, new Event('online'))
  expect(await screen.findByRole('alert')).toHaveTextContent('Показано последнее расписание')
  expect(screen.getByText('11:00 · Заплыв 1')).toBeInTheDocument()
})

it('explains a network error and allows retry', async () => {
  server.use(http.get(path, () => HttpResponse.error()))
  renderSchedule()
  expect(await screen.findByRole('alert')).toHaveTextContent('Нет связи')
  expect(screen.getByRole('button', { name: 'Повторить' })).toBeEnabled()
})
