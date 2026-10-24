import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { SessionTimeline } from './SessionTimeline'

it('renders local times, current/completed/cancelled statuses and unknown start times', () => {
  const { container } = render(<SessionTimeline timezone="Europe/Samara" headingLevel={2} sessions={[{
    id: 'morning', name: 'Утро', startsAt: '2026-10-15T07:00:00Z', entries: [
      { id: '1', title: 'Заплыв 1', startsAt: null, status: 'IN_PROGRESS', detail: '50 м вольный стиль' },
      { id: '2', title: 'Заплыв 2', startsAt: '2026-10-15T07:02:00Z', status: 'FINISHED' },
      { id: '3', title: 'Заплыв 3', status: 'CANCELLED' },
    ],
  }]} />)
  expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('11:00 · Утро')
  expect(screen.getByText('Время уточняется · Заплыв 1')).toBeInTheDocument()
  expect(screen.getByText('11:02 · Заплыв 2')).toBeInTheDocument()
  expect(screen.getByText('Идёт')).toHaveAttribute('data-status-kind', 'heat')
  expect(screen.getByText('Завершён')).toBeInTheDocument()
  expect(screen.getByText('Отменён')).toBeInTheDocument()
  expect(container.querySelectorAll('[data-current]')).toHaveLength(1)
})

it('explains an empty session and uses h3 within an event program', () => {
  render(<SessionTimeline sessions={[{ id: 'later', name: 'День', entries: [] }]} />)
  expect(screen.getByRole('heading', { level: 3 })).toHaveTextContent('День')
  expect(screen.getByText('Виды программы пока не добавлены.')).toBeInTheDocument()
})
