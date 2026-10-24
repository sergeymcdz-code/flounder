import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Link, useLocation } from 'react-router'
import { expect, it, vi } from 'vitest'
import { Button } from './Button'

it('keeps a rich accessible name and blocks repeated submissions while loading', async () => {
  const click = vi.fn()
  const { rerender } = render(<Button loading onClick={click}><strong>Сохранить</strong> результат</Button>)
  const button = screen.getByRole('button', { name: 'Сохранить результат' })
  await userEvent.click(button)
  expect(click).not.toHaveBeenCalled()
  expect(button).toHaveAttribute('aria-busy', 'true')
  rerender(<Button onClick={click}><strong>Сохранить</strong> результат</Button>)
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить результат' }))
  expect(click).toHaveBeenCalledOnce()
})
it('defaults to a non-submitting button and preserves an explicit submit type', () => {
  const { rerender } = render(<Button>Действие</Button>)
  expect(screen.getByRole('button')).toHaveAttribute('type', 'button')
  rerender(<Button type="submit">Действие</Button>)
  expect(screen.getByRole('button')).toHaveAttribute('type', 'submit')
})
function Location() { return <output>{useLocation().pathname}</output> }
it.each([true, false])('blocks disabled router links (loading=%s) without navigating', async loading => {
  const click = vi.fn()
  render(<MemoryRouter><Button as={Link} to="/next" loading={loading} aria-disabled={!loading} onClick={click}>Далее</Button><Location /></MemoryRouter>)
  const link = screen.getByRole('link', { name: 'Далее' })
  await userEvent.click(link)
  expect(click).not.toHaveBeenCalled()
  expect(screen.getByRole('status')).toHaveTextContent('/')
  expect(link).toHaveAttribute('aria-disabled', 'true')
  expect(link).toHaveAttribute('tabindex', '-1')
})
it('blocks loading anchor handlers and retains an explicit accessible label', async () => {
  const click = vi.fn()
  render(<Button as="a" href="#download" loading aria-label="Скачать протокол" onClick={click}>PDF</Button>)
  await userEvent.click(screen.getByRole('link', { name: 'Скачать протокол' }))
  expect(click).not.toHaveBeenCalled()
})
