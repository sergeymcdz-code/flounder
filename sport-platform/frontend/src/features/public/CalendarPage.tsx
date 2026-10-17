import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { calendarApi } from '../../api/calendar.api'
import { usePublicData } from './usePublicData'
import { DataState } from './PublicLayout'
import { CalendarCards } from './CalendarCards'
import type { CalendarCard } from './calendar.types'
import styles from './Calendar.module.css'
export function CalendarPage() {
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  useEffect(() => { document.title = 'Соревнования по плаванию · SportGate' }, [])
  return <CalendarContent key={query} query={query} change={(key, value) => {
    const next = new URLSearchParams(params); next.delete('page')
    if (value) next.set(key, value); else next.delete(key)
    setParams(next)
  }} />
}
function CalendarContent({ query, change }: { query: string; change: (key: string, value: string) => void }) {
  const params = new URLSearchParams(query)
  const [extra, setExtra] = useState<{ page: number; items: CalendarCard[]; total?: number }>({ page: 0, items: [] })
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState('')
  const request = useRef<AbortController | null>(null)
  const [search, setSearch] = useState(params.get('q') ?? '')
  const load = useCallback((signal: AbortSignal) => {
    const next = new URLSearchParams(query); next.set('page', '0'); next.set('size', '20')
    return calendarApi.calendar(next, signal)
  }, [query])
  const state = usePublicData(load)
  useEffect(() => () => { request.current?.abort() }, [])
  const items = [...new Map([...(state.data?.items ?? []), ...extra.items].map(card => [card.slug, card])).values()]
  const total = extra.total ?? state.data?.total ?? 0
  async function loadMore() {
    if (!state.data || request.current) return
    const controller = new AbortController()
    request.current = controller
    setLoadingMore(true); setMoreError('')
    const next = new URLSearchParams(query); next.set('page', String(extra.page + 1)); next.set('size', '20')
    try {
      const result = await calendarApi.calendar(next, controller.signal)
      if (!controller.signal.aborted) setExtra(previous => ({
        page: previous.page + 1, items: [...previous.items, ...result.items], total: result.total,
      }))
    } catch (cause) {
      if (!controller.signal.aborted) setMoreError(cause instanceof Error ? cause.message : 'Не удалось загрузить следующую страницу.')
    } finally {
      if (!controller.signal.aborted) setLoadingMore(false)
      if (request.current === controller) request.current = null
    }
  }
  const status = params.get('status') ?? 'upcoming'
  return <>
    <p className={styles.eyebrow}>Календарь стартов</p>
    <h1>Соревнования по плаванию</h1>
    <div className={styles.tabs} role="group" aria-label="Период соревнований">
      <button className="fl-button-ghost" aria-pressed={status === 'upcoming'} onClick={() => change('status', 'upcoming')}>Ближайшие</button>
      <button className="fl-button-ghost" aria-pressed={status === 'past'} onClick={() => change('status', 'past')}>Прошедшие</button>
    </div>
    <form className={styles.filters} onSubmit={e => { e.preventDefault(); change('q', search.trim()) }}>
      <label>Город<select aria-label="Город" value={params.get('city') ?? ''} onChange={e => change('city', e.target.value)}>
        <option value="">Все города</option>
        {[...new Set([...(state.data?.cities ?? []), ...(params.get('city') ? [params.get('city')!] : [])])].map(city => <option key={city}>{city}</option>)}
      </select></label>
      <label className={styles.search}>Название соревнования<input type="search" aria-label="Поиск по названию" value={search} onChange={e => setSearch(e.target.value)} maxLength={200} /></label>
      <button className="fl-button-ghost" type="submit">Найти</button>
    </form>
    <DataState {...state} />
    {state.data && <><p className={styles.muted} role="status">Найдено соревнований: {total}</p>
      <CalendarCards items={items} grouped />
      {moreError && <p role="alert">{moreError}</p>}
      {items.length < total && <button className="fl-button-ghost" disabled={loadingMore} onClick={() => void loadMore()}>
        {loadingMore ? 'Загружаем…' : moreError ? 'Повторить загрузку' : 'Показать ещё'}
      </button>}
    </>}
  </>
}
