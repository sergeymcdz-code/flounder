import { useCallback } from 'react'
import { Link } from 'react-router'
import { publicApi } from '../../api/public.api'
import { LIVE_POLL_MS, usePublicData } from '../public/usePublicData'
export function CurrentHeatButton({ eventId, slug }: { eventId: string; slug?: string }) {
  const load = useCallback((signal: AbortSignal) => slug ? publicApi.live(slug, signal) : Promise.resolve(null), [slug])
  const live = usePublicData(load, LIVE_POLL_MS)
  const current = live.data?.currentUnit
  return <div>{current ? <Link className="fl-button-primary" to={`/operator/competition-units/${current.id}?eventId=${encodeURIComponent(eventId)}`}>Открыть текущий заплыв</Link>
    : <><button className="fl-button-primary" disabled>Открыть текущий заплыв</button><p>{live.error ? 'Не удалось загрузить текущий заплыв' : live.loading ? 'Проверяем текущий заплыв…' : 'Сейчас нет активного заплыва'}</p></>}</div>
}
