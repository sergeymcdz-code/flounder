import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { OverflowMenu } from './OverflowMenu'

it('opens with menu semantics, skips disabled actions and returns focus on Escape', async () => {
  render(<OverflowMenu items={[{ label: 'Недоступно', disabled: true, disabledReason: 'Нет прав' }, { label: 'Открыть' }, { label: 'Скачать' }]} />)
  const trigger = screen.getByRole('button', { name: 'Другие действия' })
  await userEvent.click(trigger)
  expect(trigger).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByRole('menu')).toHaveAttribute('id', trigger.getAttribute('aria-controls'))
  expect(screen.getByRole('menuitem', { name: 'Открыть' })).toHaveFocus()
  await userEvent.keyboard('{ArrowDown}')
  expect(screen.getByRole('menuitem', { name: 'Скачать' })).toHaveFocus()
  await userEvent.keyboard('{Home}')
  expect(screen.getByRole('menuitem', { name: 'Открыть' })).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  expect(trigger).toHaveFocus()
})
it('executes an action once, blocks disabled actions and closes on outside click', async () => {
  const select = vi.fn()
  render(<><button>Снаружи</button><OverflowMenu items={[{ label: 'Удалить', onSelect: select }, { label: 'Недоступно', disabled: true, onSelect: select }]} /></>)
  const trigger = screen.getByRole('button', { name: 'Другие действия' })
  await userEvent.click(trigger)
  await userEvent.click(screen.getByRole('menuitem', { name: 'Недоступно' }))
  expect(select).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('menuitem', { name: 'Удалить' }))
  expect(select).toHaveBeenCalledOnce()
  expect(trigger).toHaveFocus()
  await userEvent.click(trigger)
  await userEvent.click(screen.getByRole('button', { name: 'Снаружи' }))
  expect(screen.queryByRole('menu')).not.toBeInTheDocument()
})
it('supports route links and opening from the keyboard', async () => {
  render(<MemoryRouter><OverflowMenu items={[{ label: 'Грамоты', to: '/diploma' }]} /></MemoryRouter>)
  screen.getByRole('button').focus()
  await userEvent.keyboard('{ArrowDown}')
  expect(screen.getByRole('menuitem', { name: 'Грамоты' })).toHaveAttribute('href', '/diploma')
  await userEvent.keyboard('{Tab}')
  expect(screen.queryByRole('menu')).not.toBeInTheDocument()
})
