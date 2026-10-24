import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { createFixtures } from '../../mocks/fixtures'
import { EventChecklist, checklistSteps } from './EventChecklist'
it('marks an empty competition at the program step', () => {
  const context = createFixtures(); context.event.disciplines = []; context.sessions = []; context.registrations = []; context.heats = []
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} /></MemoryRouter>)
  expect(screen.getAllByLabelText('текущий шаг')).toHaveLength(1)
  expect(screen.getByRole('link', { name: 'Добавить сессии и виды' })).toHaveClass('fl-btn fl-btn-ghost')
  expect(screen.getByLabelText('текущий шаг').parentElement).toHaveTextContent('1. Программа')
})
it('points at reviewing new registrations after opening registration', () => {
  const context = createFixtures(); context.event.status = 'REGISTRATION_OPEN'; context.registrations[0].status = 'SUBMITTED'
  const steps = checklistSteps(context)
  expect(steps.slice(0, 2).every(s => s.done)).toBe(true)
  expect(steps.findIndex(s => !s.done)).toBe(2)
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} /></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'Проверить' })).toHaveAttribute('href', `/organizer/events/${context.event.id}/entries?status=SUBMITTED`)
})
it('marks every step complete when results and competition are completed', () => {
  const context = createFixtures(); context.event.status = 'COMPLETED'
  context.registrations.forEach(r => { r.status = 'CONFIRMED' }); context.heats.forEach(h => { h.status = 'FINISHED' })
  context.event.disciplines = context.event.disciplines.filter(d => context.heats.some(h => h.eventDisciplineId === d.id))
  expect(checklistSteps(context).every(s => s.done)).toBe(true)
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} /></MemoryRouter>)
  expect(screen.getAllByLabelText('выполнено')).toHaveLength(7)
  expect(screen.queryByLabelText('текущий шаг')).not.toBeInTheDocument()
})

it('collapses completed steps by default and exposes all seven when expanded', () => {
  const context = createFixtures()
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} /></MemoryRouter>)
  const details = screen.getByText(/Выполнено: \d из 7/).closest('details')!
  expect(details).not.toHaveAttribute('open')
  fireEvent.click(screen.getByText(/Выполнено: \d из 7/))
  expect(details).toHaveAttribute('open')
  expect(screen.getAllByRole('listitem')).toHaveLength(7)
})

it('keeps a lifecycle action visible even when its registration step is done', () => {
  const context = createFixtures()
  context.event.status = 'REGISTRATION_OPEN'
  const onTransition = vi.fn()
  render(<MemoryRouter><EventChecklist context={context} onTransition={onTransition} action={{ label: 'Закрыть регистрацию', step: 1 }} /></MemoryRouter>)
  const current = screen.getByLabelText('текущий шаг').parentElement!
  expect(within(current).getByRole('button', { name: 'Закрыть регистрацию' })).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Закрыть регистрацию' }))
  expect(onTransition).toHaveBeenCalledExactlyOnceWith(1)
  expect(document.querySelectorAll('.fl-btn-primary')).toHaveLength(1)
})

it('explains why completion is unavailable', () => {
  const context = createFixtures()
  const reason = 'Сначала завершите все заплывы'
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} action={{ label: 'Завершить соревнование', step: 6, reason }} /></MemoryRouter>)
  const button = screen.getByRole('button', { name: 'Завершить соревнование' })
  expect(button).toBeDisabled()
  expect(button).toHaveAttribute('title', reason)
  expect(button).toHaveAccessibleDescription(reason)
  expect(screen.getByText(reason)).toBeVisible()
})
