import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { StatusBadge } from './StatusBadge'
import type { StatusKind } from './status-dictionary'

it.each<[StatusKind, string, string, string]>([
  ['registration', 'CONFIRMED', 'Подтверждена', 'success'],
  ['registration', 'NEEDS_CORRECTION', 'На исправлении', 'warning'],
  ['registration', 'REJECTED', 'Отклонена', 'danger'],
  ['registration', 'SUBMITTED', 'На рассмотрении', 'info'],
  ['result', 'DNS', 'DNS', 'neutral'],
  ['result', 'DNF', 'DNF', 'neutral'],
  ['result', 'DSQ', 'DSQ', 'danger'],
  ['result', 'CORRECTED', 'исправлено', 'warning'],
])('renders %s/%s as readable text, without relying on color', (kind, code, label, tone) => {
  render(<StatusBadge kind={kind} code={code} />)
  const badge = screen.getByText(label)
  expect(badge).toHaveClass(`fl-badge-${tone}`)
  expect(badge).toHaveAttribute('data-status', code)
  expect(badge).toHaveAttribute('data-status-kind', kind)
})
it('normalizes an API alias and keeps unknown statuses visible', () => {
  render(<><StatusBadge kind="heat" code=" started " /><StatusBadge kind="result" code="NEW_STATUS" /></>)
  expect(screen.getByText('Идёт')).toHaveClass('fl-badge-live')
  expect(screen.getByText('Идёт')).toHaveAttribute('data-status', 'IN_PROGRESS')
  expect(screen.getByText('NEW_STATUS')).toHaveClass('fl-badge-neutral')
})
