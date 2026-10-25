import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it } from 'vitest'
import App from './App'
import { AuthProvider } from './providers/AuthProvider'

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App /></AuthProvider></MemoryRouter>)
}

it.each(['/no-such-page-qa', '/e', '/organizer-typo/events'])('shows a not-found page for an unknown route %s', async path => {
  renderAt(path)
  expect(await screen.findByRole('heading', { level: 1, name: 'Страница не найдена' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Соревнования' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'К календарю соревнований' })).toHaveAttribute('href', '/')
  expect(document.title).toBe('Страница не найдена · SportGate')
})

it('keeps known public routes on their own pages', async () => {
  renderAt('/')
  expect(await screen.findByRole('heading', { level: 1, name: 'Соревнования' })).toBeInTheDocument()
  expect(screen.queryByText('Страница не найдена')).not.toBeInTheDocument()
})
