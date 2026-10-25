import { useCallback } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { publicApi } from '../../api/public.api'
import { usePublicData } from './usePublicData'

function EventDetails({ slug }: { slug: string }) {
  const load = useCallback(
    (signal: AbortSignal) => publicApi.event(slug, signal),
    [slug],
  )
  const { data, error, loading } = usePublicData(load)
  return (
    <>
      {loading && <p role="status">Загружаем</p>}
      {error && <p role="alert">{error}</p>}
      {data && <h1>{data.title}</h1>}
    </>
  )
}

it('does not show the previous competition when the next slug is missing', async () => {
  const { rerender } = render(<EventDetails slug="demo" />)
  expect(
    await screen.findByRole('heading', { name: 'Осенний кубок SportGate' }),
  ).toBeInTheDocument()
  rerender(<EventDetails slug="missing" />)
  expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Соревнование не найдено.',
  )
  expect(screen.queryByRole('heading')).not.toBeInTheDocument()
})

it('skips requests while disabled and aborts an active request when disabled', async () => {
  const load = vi.fn((signal: AbortSignal) => new Promise<string>((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
  }))
  function OptionalData({ enabled }: { enabled: boolean }) {
    const state = usePublicData(load, 1000, enabled)
    return <p>{state.loading ? 'Загружаем' : 'Отключено'}{state.error}</p>
  }
  const { rerender } = render(<OptionalData enabled={false} />)
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(screen.getByText('Отключено')).toBeInTheDocument()
  expect(load).not.toHaveBeenCalled()
  rerender(<OptionalData enabled />)
  await waitFor(() => expect(load).toHaveBeenCalledTimes(1))
  const signal = load.mock.calls[0][0]
  rerender(<OptionalData enabled={false} />)
  expect(signal.aborted).toBe(true)
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(screen.getByText('Отключено')).toBeInTheDocument()
  expect(load).toHaveBeenCalledTimes(1)
})
