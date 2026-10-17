import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { publicApi, PublicApiError } from '../../api/public.api'
import type { Standings } from '../../api/standings.api'
import { demoEvent } from '../../mocks/demo-data'
import { PublicLayout } from './PublicLayout'

function Navigation() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/e/other')}>Другое событие</button>
}
afterEach(() => vi.useRealTimers())
describe('Public standings navigation', () => {
  it.each([true, false])('requests standings once per slug when published=%s', async published => {
    vi.useFakeTimers()
    vi.spyOn(publicApi, 'event').mockImplementation(async slug => ({ ...demoEvent, publicSlug: slug }))
    const load = vi.spyOn(publicApi, 'standings').mockImplementation(async () => {
      if (!published) throw new PublicApiError(404, 'Зачёт не опубликован')
      return { title: null, scope: 'AGE_GROUP', medals: [], team: [], unitsPending: 0,
        preliminary: false, disciplinesIncluded: 0, calculatedAt: '2026-10-17T12:00:00Z' } satisfies Standings
    })
    await act(async () => { render(<MemoryRouter initialEntries={['/e/demo']}>
      <Navigation /><Routes><Route path="/e/:slug" element={<PublicLayout />}>
        <Route index element={<p>Карточка события</p>} />
      </Route></Routes>
    </MemoryRouter>) })
    expect(load).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('link', { name: 'Зачёт' }) !== null).toBe(published)
    await act(async () => { await vi.advanceTimersByTimeAsync(90_000) })
    expect(load).toHaveBeenCalledTimes(1)
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Другое событие' })) })
    expect(load).toHaveBeenCalledTimes(2)
    expect(load.mock.calls[1][0]).toBe('other')
  })
})
