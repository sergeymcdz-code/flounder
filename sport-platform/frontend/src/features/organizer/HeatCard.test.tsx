import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { HeatCard } from './HeatCard'
import type { Heat, SwimDiscipline } from '../../api/organizer.types'

const heat: Heat = { id: 'heat', eventDisciplineId: 'd', label: '1', sequenceNumber: 1, scheduledAt: '2026-10-17T06:00:00Z', status: 'DRAFT', entries: [
  { entryId: 'two', registrationId: 'r2', participantName: 'Иванов Иван', laneOrPosition: 2, rawValue: null, resultStatus: null },
  { entryId: 'one', registrationId: 'r1', participantName: 'Петров Пётр', laneOrPosition: 1, rawValue: null, resultStatus: null },
] }
const discipline = { name: '50 м вольный стиль', ageGroups: [] } as unknown as SwimDiscipline
beforeEach(() => vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))))
afterEach(() => vi.unstubAllGlobals())

it('collapses lanes on mobile and changes a lane only in explicit edit mode', async () => {
  const onChangeLane = vi.fn()
  render(<HeatCard heat={heat} discipline={discipline} registrations={[]} lanes={8} timeZone="Europe/Samara" highlighted={false} onChangeLane={onChangeLane} />)
  expect(screen.getByText('10:00')).toBeVisible()
  const summary = screen.getByText('Дорожки · 2')
  expect(summary.closest('details')).not.toHaveAttribute('open')
  await userEvent.click(summary)
  const edit = screen.getByRole('button', { name: 'Редактировать дорожки' })
  expect(edit).toHaveAttribute('aria-pressed', 'false')
  await userEvent.click(edit)
  const lane = screen.getByLabelText('Дорожка Петров Пётр')
  await userEvent.selectOptions(lane, '3')
  expect(onChangeLane).toHaveBeenCalledExactlyOnceWith('one', 3)
  const table = screen.getByRole('table')
  expect(table).toHaveAccessibleName('Заплыв 1 — дорожки и заявочное время')
  expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Петров Пётр')
})

it('opens lanes immediately on desktop and explains archived editing restrictions', () => {
  vi.mocked(window.matchMedia).mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() } as unknown as MediaQueryList)
  const reason = 'Архивное событие доступно только для чтения'
  render(<HeatCard heat={heat} discipline={discipline} registrations={[]} lanes={8} highlighted={false} disabledReason={reason} onChangeLane={vi.fn()} />)
  expect(screen.getByText('Дорожки · 2').closest('details')).toHaveAttribute('open')
  for (const select of screen.getAllByRole('combobox')) {
    expect(select).toBeDisabled()
    expect(select).toHaveAttribute('title', reason)
  }
  expect(screen.getByText(reason)).toBeInTheDocument()
})
