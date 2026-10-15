import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { calendarApi } from '../../api/calendar.api'
import { usePublicData } from './usePublicData'
import { DataState } from './PublicLayout'
import { CalendarCards } from './CalendarCards'
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
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState(params.get('q') ?? '')
  const load = useCallback(async (signal: AbortSignal) => {
    const pages = await Promise.all(Array.from({ length: page + 1 }, (_, index) => {
      const next = new URLSearchParams(query); next.set('page', String(index)); next.set('size', '20')
      return calendarApi.calendar(next, signal)
    }))
    return { ...pages[0], items: pages.flatMap(p => p.items) }
  }, [query, page])
  const state = usePublicData(load)
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
    {state.data && <><p className={styles.muted} role="status">Найдено соревнований: {state.data.total}</p>
      <CalendarCards items={state.data.items} grouped />
      {state.data.items.length < state.data.total && <button className="fl-button-ghost" onClick={() => setPage(p => p + 1)}>Показать ещё</button>}
    </>}
  </>
}
