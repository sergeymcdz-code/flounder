import { useCallback, useId } from 'react'
import { Link } from 'react-router'
import { publicApi } from '../../api/public.api'
import { LIVE_POLL_MS, usePublicData } from '../public/usePublicData'
import styles from './CurrentHeatButton.module.css'

export function CurrentHeatButton({ eventId, slug }: { eventId: string; slug?: string }) {
  const hintId = useId()
  const load = useCallback((signal: AbortSignal) => slug ? publicApi.live(slug, signal) : Promise.resolve(null), [slug])
  const live = usePublicData(load, LIVE_POLL_MS)
  const current = live.data?.currentUnit
  const hint = !eventId ? 'Выберите соревнование ниже.' : !slug ? 'Публичная страница соревнования ещё не доступна.'
    : live.error ? 'Не удалось загрузить текущий заплыв. Выберите заплыв в списке или повторите загрузку.'
    : live.loading ? 'Проверяем текущий заплыв…' : 'Сейчас нет активного заплыва. Выберите его в списке.'
  return <div className={styles.currentHeat}>{current && eventId ? <Link className="fl-btn fl-btn-primary" to={`/operator/competition-units/${current.id}?eventId=${encodeURIComponent(eventId)}`}>Открыть текущий заплыв</Link>
    : <><button className="fl-btn fl-btn-primary" disabled aria-describedby={hintId} title={hint}>Открыть текущий заплыв</button><p id={hintId} role="status">{hint}</p>{live.error && <button className="fl-btn fl-btn-ghost" onClick={live.retry}>Повторить загрузку</button>}</>}</div>
}
