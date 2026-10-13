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
    expect(within(navigation).getByRole('link', { name: 'Результаты' }))
      .toHaveAttribute('href', '/operator/results')
  } finally {
    sessionStorage.removeItem('flounder.auth-session')
  }
})
