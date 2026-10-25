import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../test/setup'
import App from './App'
import { AuthProvider } from './providers/AuthProvider'

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App /></AuthProvider></MemoryRouter>)
}

it.each(['/no-such-page-qa', '/e', '/e/', '/organizer-typo/events', '/organizer/xyz'])('shows a not-found page for an unknown route %s', async path => {
  let emptySlugRequests = 0
  server.use(http.get('*/api/v1/public/events/', () => {
    emptySlugRequests++
    return HttpResponse.json([])
  }))
  renderAt(path)
  expect(await screen.findByRole('heading', { level: 1, name: 'Страница не найдена' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Соревнования' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'К календарю соревнований' })).toHaveAttribute('href', '/')
  expect(document.title).toBe('Страница не найдена · SportGate')
  expect(emptySlugRequests).toBe(0)
})

it('shows an unknown event tab inside the event shell without requesting an empty slug', async () => {
  let emptySlugRequests = 0
  server.use(http.get('*/api/v1/public/events/', () => {
    emptySlugRequests++
    return HttpResponse.json([])
  }))
  renderAt('/e/demo/xyz')
  expect(await screen.findByRole('heading', { level: 1, name: 'Страница не найдена' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Осенний кубок SportGate' })).toHaveAttribute('href', '/e/demo')
  expect(screen.getByRole('link', { name: 'Табло' })).toHaveAttribute('href', '/e/demo/live')
  expect(document.title).toBe('Страница не найдена · SportGate')
  expect(emptySlugRequests).toBe(0)
})

it('keeps known public routes on their own pages', async () => {
  renderAt('/')
  expect(await screen.findByRole('heading', { level: 1, name: 'Соревнования' })).toBeInTheDocument()
  expect(screen.queryByText('Страница не найдена')).not.toBeInTheDocument()
})
