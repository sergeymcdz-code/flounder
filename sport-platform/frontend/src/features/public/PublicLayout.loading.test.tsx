import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { publicApi } from '../../api/public.api'
import type { Standings } from '../../api/standings.api'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { demoEvent } from '../../mocks/demo-data'
import { PosterPage } from './PosterPage'
import { PublicLayout, SLOW_LOADING_MS } from './PublicLayout'

afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider>
    <Routes><Route path="/e/:slug" element={<PublicLayout />}>
      <Route index element={<p>Карточка события</p>} />
      <Route path="poster" element={<PosterPage />} />
    </Route></Routes>
  </AuthProvider></MemoryRouter>)
}

it('shows loading at once and explains a slow cold start', async () => {
  vi.useFakeTimers()
  vi.spyOn(publicApi, 'event').mockImplementation(() => new Promise(() => {}))
  const standings = vi.spyOn(publicApi, 'standings').mockImplementation(() => new Promise(() => {}))
  renderAt('/e/demo')
  expect(screen.getByRole('status')).toHaveTextContent('Загружаем данные…')
  // Standings are requested in parallel with the event, not after it.
  expect(standings).toHaveBeenCalledTimes(1)
  expect(screen.queryByText(/дольше обычного/)).not.toBeInTheDocument()
  await act(async () => { await vi.advanceTimersByTimeAsync(SLOW_LOADING_MS) })
  expect(screen.getByRole('status')).toHaveTextContent('Сервер отвечает дольше обычного')
})

it('renders the page as soon as the event arrives while standings are still pending', async () => {
  const standings = vi.spyOn(publicApi, 'standings').mockImplementation(() => new Promise<Standings>(() => {}))
  vi.spyOn(publicApi, 'event').mockResolvedValue(demoEvent)
  renderAt('/e/demo')
  expect(await screen.findByText('Карточка события')).toBeInTheDocument()
  // Both requests start together instead of one after the other.
  expect(standings).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('link', { name: 'Командный зачёт' })).not.toBeInTheDocument()
})

it('does not request standings for the poster', async () => {
  const standings = vi.spyOn(publicApi, 'standings')
  vi.spyOn(publicApi, 'event').mockResolvedValue(demoEvent)
  renderAt('/e/demo/poster')
  expect(await screen.findByRole('button', { name: 'Печатать QR-плакат A4' })).toBeInTheDocument()
  expect(standings).not.toHaveBeenCalled()
})
