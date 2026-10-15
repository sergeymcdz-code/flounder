import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it } from 'vitest'
import { createFixtures } from '../../mocks/fixtures'
import { EventChecklist, checklistSteps } from './EventChecklist'
it('marks an empty competition at the program step', () => {
  const context = createFixtures(); context.event.disciplines = []; context.sessions = []; context.registrations = []; context.heats = []
  render(<MemoryRouter><EventChecklist context={context} onTransition={() => {}} /></MemoryRouter>)
  expect(screen.getAllByLabelText('текущий шаг')).toHaveLength(1)
  expect(screen.getByRole('link', { name: 'Добавить сессии и виды' })).toHaveClass('fl-button-primary')
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
