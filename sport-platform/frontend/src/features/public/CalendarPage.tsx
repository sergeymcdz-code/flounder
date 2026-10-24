import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { calendarApi } from '../../api/calendar.api'
import { PublicApiError } from '../../api/public.api'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { usePublicData } from './usePublicData'
import { CalendarCards } from './CalendarCards'
import type { CalendarCard } from './calendar.types'
import styles from './CalendarCards.module.css'

function errorMessage(message: string, status?: number) {
  return status === 404 ? 'Данные не найдены.' : status ? `Сервер ответил ошибкой (${status}). ${message}` : 'Нет связи. Проверьте подключение к интернету.'
}

export function CalendarPage() {
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  const titleQuery = params.get('q') ?? ''
  const [search, setSearch] = useState(titleQuery)
  const [previousTitleQuery, setPreviousTitleQuery] = useState(titleQuery)
  if (previousTitleQuery !== titleQuery) {
    setPreviousTitleQuery(titleQuery)
    setSearch(titleQuery)
  }
  const [cities, setCities] = useState<string[]>([])
  useEffect(() => { document.title = 'Соревнования по плаванию · SportGate' }, [])
  useEffect(() => {
    if (search.trim() === titleQuery) return
    const timer = window.setTimeout(() => {
      const next = new URLSearchParams(query); next.delete('page')
      if (search.trim()) next.set('q', search.trim()); else next.delete('q')
      setParams(next, { replace: true })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search, titleQuery, query, setParams])
  function change(key: string, value: string) {
    const next = new URLSearchParams(params); next.delete('page')
    if (value) next.set(key, value); else next.delete(key)
    setParams(next)
  }
  function reset() {
    const next = new URLSearchParams(params)
    for (const key of ['q', 'city', 'status', 'page']) next.delete(key)
    setSearch(''); setParams(next)
  }
  const status = params.get('status') ?? 'upcoming'
  const city = params.get('city') ?? ''
  const segments = <div className={styles.segments} role="group" aria-label="Период соревнований">
    <Button variant="ghost" size="sm" aria-pressed={status === 'upcoming'} onClick={() => change('status', 'upcoming')}>Ближайшие</Button>
    <Button variant="ghost" size="sm" aria-pressed={status === 'past'} onClick={() => change('status', 'past')}>Прошедшие</Button>
  </div>
  return <div className={styles.page}>
    <div><p className={styles.eyebrow}>Календарь стартов</p><PageHeader title="Соревнования по плаванию" actions={segments} /></div>
    <div className={styles.filters}>
      <label className={`fl-field ${styles.citySelect}`}>Город<select aria-label="Город" value={city} onChange={e => change('city', e.target.value)}>
        <option value="">Все города</option>
        {[...new Set([...cities, ...(city ? [city] : [])])].map(name => <option key={name}>{name}</option>)}
      </select></label>
      <label className={`fl-field ${styles.search}`}>Поиск<input type="search" aria-label="Поиск по названию" placeholder="Название соревнования" value={search} onChange={e => setSearch(e.target.value)} maxLength={200} /></label>
      <div className={styles.cities} role="group" aria-label="Город">
        <Button variant="ghost" size="sm" aria-pressed={!city} onClick={() => change('city', '')}>Все города</Button>
        {[...new Set([...cities, ...(city ? [city] : [])])].map(name => <Button key={name} variant="ghost" size="sm" aria-pressed={city === name} onClick={() => change('city', name)}>{name}</Button>)}
      </div>
    </div>
    <CalendarContent key={query} query={query} onCities={setCities} reset={reset} />
  </div>
}

function CalendarContent({ query, onCities, reset }: { query: string; onCities: (cities: string[]) => void; reset: () => void }) {
  const [extra, setExtra] = useState<{ page: number; items: CalendarCard[]; total?: number }>({ page: 0, items: [] })
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState('')
  const request = useRef<AbortController | null>(null)
  const load = useCallback((signal: AbortSignal) => {
    const next = new URLSearchParams(query); next.set('page', '0'); next.set('size', '20')
    return calendarApi.calendar(next, signal)
  }, [query])
  const state = usePublicData(load)
  useEffect(() => { if (state.data) onCities(state.data.cities) }, [state.data, onCities])
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
      if (!controller.signal.aborted) setMoreError(errorMessage(cause instanceof Error ? cause.message : '', cause instanceof PublicApiError ? cause.status : undefined))
    } finally {
      if (!controller.signal.aborted) setLoadingMore(false)
      if (request.current === controller) request.current = null
    }
  }
  return <>
    {state.loading && !state.data && <div role="status" aria-label="Загружаем соревнования…">
      <span className="fl-sr-only">Загружаем соревнования…</span>
      <div className={styles.cards} aria-hidden="true">{[0, 1, 2].map(index => <div key={index} className={`${styles.card} ${styles.skeleton}`}><span /><span /><span /></div>)}</div>
    </div>}
    {state.error && <Notice tone="danger" className={styles.error}>
      <p>{errorMessage(state.error, state.errorStatus)}</p><Button onClick={state.retry}>Повторить</Button>
    </Notice>}
    {state.data && <>
      <p className={styles.count} role="status">Найдено: {total}</p>
      {items.length ? <CalendarCards items={items} grouped /> : <EmptyState title="Соревнований не найдено" action={<Button onClick={reset}>Сбросить фильтры</Button>}><p>Попробуйте другое название или город.</p></EmptyState>}
      {moreError && <Notice tone="danger">{moreError}</Notice>}
      {items.length < total && <Button className={styles.more} disabled={loadingMore} aria-busy={loadingMore} title={loadingMore ? 'Загружаем следующую страницу соревнований' : undefined} onClick={() => void loadMore()}>
        {loadingMore ? 'Загружаем…' : moreError ? 'Повторить загрузку' : 'Показать ещё'}
      </Button>}
    </>}
  </>
}
