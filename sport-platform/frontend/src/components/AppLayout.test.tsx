import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it } from 'vitest'
import { AuthProvider } from '../app/providers/AuthProvider'
import { AppLayout } from './AppLayout'

it('shows the results navigation link for a judge session', () => {
  sessionStorage.setItem('flounder.auth-session', JSON.stringify({
    accessToken: `demo.${btoa(JSON.stringify({ roles: ['judge'] }))}.demo`,
    tokenType: 'Bearer',
    expiresAt: Date.now() + 60_000,
  }))
  try {
    render(<MemoryRouter><AuthProvider><AppLayout /></AuthProvider></MemoryRouter>)
    const navigation = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(navigation).getByRole('link', { name: 'Судейство' }))
      .toHaveAttribute('href', '/operator/results')
  } finally {
    sessionStorage.removeItem('flounder.auth-session')
  }
})

it.each([['participant', 'Мои заявки', '/my/registrations'], ['organizer', 'Мои соревнования', '/organizer/events'], ['operator', 'Мои соревнования', '/organizer/events'], ['platform_admin', 'Мои соревнования', '/organizer/events'], ['judge', 'Судейство', '/operator/results']])('shows one workspace for %s', (role, label, href) => {
  sessionStorage.setItem('flounder.auth-session', JSON.stringify({ accessToken: `demo.${btoa(JSON.stringify({ roles: [role] }))}.demo`, expiresAt: Date.now() + 60000 }))
  render(<MemoryRouter><AuthProvider><AppLayout /></AuthProvider></MemoryRouter>)
  expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href)
  expect(screen.queryByRole('link', { name: 'Заявки' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Заявки и платежи' })).not.toBeInTheDocument()
  expect(screen.getByText('Сменить пароль').closest('details')?.querySelector('summary')).toHaveTextContent(/Профиль|user/)
  sessionStorage.clear()
})
