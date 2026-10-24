import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ResultStatus } from './ResultStatus'
import { StatusLegend } from './StatusLegend'
import { resultStatusCode, resultStatusDictionary } from './results-ui'

describe('Result status explanations', () => {
  it('covers every special code with Russian hints and legend descriptions', () => {
    expect(resultStatusDictionary).toEqual({
      NT: { hint: 'Нет времени (результат не зафиксирован)', legend: 'нет времени (результат не зафиксирован)' },
      DNS: { hint: 'Не стартовал', legend: 'не стартовал' },
      DNF: { hint: 'Не финишировал', legend: 'не финишировал' },
      DSQ: { hint: 'Дисквалифицирован', legend: 'дисквалифицирован' },
      CORRECTED: { hint: 'Результат исправлен после публикации', legend: 'результат исправлен после публикации' },
    })
    expect(resultStatusCode('DID_NOT_START')).toBe('DNS')
    expect(resultStatusCode('DID_NOT_FINISH')).toBe('DNF')
    expect(resultStatusCode('DISQUALIFIED')).toBe('DSQ')
    expect(resultStatusCode('unknown')).toBeUndefined()
    expect(resultStatusCode('constructor')).toBeUndefined()
  })

  it('shows only present statuses once and updates when the table changes', () => {
    const { rerender } = render(<StatusLegend statuses={['VALID', 'DNS', 'DID_NOT_START', 'CORRECTED', null]} />)
    expect(screen.getByLabelText('Обозначения статусов результатов')).toHaveTextContent(
      /^DNS — не стартовал · CORRECTED — результат исправлен после публикации$/,
    )
    rerender(<StatusLegend statuses={['DSQ', 'NT', 'DNF']} />)
    expect(screen.getByLabelText('Обозначения статусов результатов')).toHaveTextContent(
      /^NT — нет времени \(результат не зафиксирован\) · DNF — не финишировал · DSQ — дисквалифицирован$/,
    )
    rerender(<StatusLegend statuses={['VALID', 'PENDING', undefined]} />)
    expect(screen.queryByLabelText('Обозначения статусов результатов')).not.toBeInTheDocument()
    rerender(<StatusLegend statuses={[]} />)
    expect(screen.queryByLabelText('Обозначения статусов результатов')).not.toBeInTheDocument()
  })

  it('opens hints on tap and keyboard and dismisses them on Escape or blur', async () => {
    const user = userEvent.setup()
    render(<><ResultStatus status="DNS" /><button>Далее</button></>)
    const code = screen.getByLabelText('DNS — Не стартовал')
    expect(code).toHaveAttribute('title', 'Не стартовал')
    await user.click(code)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Не стартовал')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    await user.keyboard('{Enter}')
    expect(screen.getByRole('tooltip')).toBeVisible()
    await user.tab()
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('keeps the judge button name, description and action, with a tooltip only on touch', async () => {
    const user = userEvent.setup()
    const action = vi.fn()
    render(<ResultStatus status="DSQ" buttonProps={{ onClick: action, type: 'button' }} />)
    const button = screen.getByRole('button', { name: 'DSQ' })
    expect(button).toHaveAttribute('title', 'Дисквалифицирован')
    expect(button).toHaveAccessibleDescription('Дисквалифицирован')
    await user.click(button)
    expect(action).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    await user.pointer([{ keys: '[TouchA>]', target: button }, { keys: '[/TouchA]' }])
    expect(action).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Дисквалифицирован')
    expect(button).toHaveAccessibleDescription('Дисквалифицирован')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})
