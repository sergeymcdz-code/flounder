import { useCallback } from 'react'
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
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
    await screen.findByRole('heading', { name: 'Осенний кубок Flounder' }),
  ).toBeInTheDocument()
  rerender(<EventDetails slug="missing" />)
  expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Соревнование не найдено.',
  )
  expect(screen.queryByRole('heading')).not.toBeInTheDocument()
})
