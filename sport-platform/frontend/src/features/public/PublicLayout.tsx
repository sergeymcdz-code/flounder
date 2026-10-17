import { useCallback, useContext, useEffect, useState } from 'react'
import {
  Link,
  NavLink,
  Navigate,
  Outlet,
  useLocation,
  useParams,
} from 'react-router'
import { publicApi } from '../../api/public.api'
import { usePublicData } from './usePublicData'
import { date } from './format'
import styles from './Public.module.css'
import { PublicConnectionContext } from './publicContext'
import { useAuth } from '../../app/providers/AuthProvider'
import { roleHomeTarget } from '../../utils/authReturn'
import { ThemeToggle } from '../../components/ThemeToggle'

export function DataState({
  loading,
  error,
  retry,
  data,
}: {
  loading?: boolean
  error?: string
  data?: unknown
  retry?: () => void
}) {
  const disconnected = useContext(PublicConnectionContext)
  if (data !== undefined && disconnected) return null
  if (loading) return <p role="status">Загружаем данные…</p>
  if (!error) return null
  return (
    <div className={styles.notice} role="alert">
      <p>
        {error}
        {data !== undefined &&
          ' Показаны последние данные. Обновление продолжится при подключении.'}
      </p>
      {retry && <button className="fl-button-ghost" onClick={retry}>Повторить</button>}
    </div>
  )
}

export function PublicLayout() {
  const { slug = '' } = useParams()
  const { roles, isAuthenticated } = useAuth()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.event(slug, signal),
    [slug],
  )
  const eventState = usePublicData(load)
  const [offline, setOffline] = useState(() => !navigator.onLine)
  const [cached, setCached] = useState(false)
  useEffect(() => {
    function update() {
      setOffline(!navigator.onLine)
    }
    function onCache(event: Event) {
      const detail = (event as CustomEvent<{ path: string; cached: boolean }>)
        .detail
      if (detail.path !== slug && !detail.path.startsWith(`${slug}/`)) return
      setCached(detail.cached)
    }
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    window.addEventListener('flounder:public-cache', onCache)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
      window.removeEventListener('flounder:public-cache', onCache)
    }
  }, [slug])
  useEffect(() => {
    if (eventState.errorStatus === 404)
      document.title = 'Соревнование не найдено · SportGate'
    else if (eventState.data)
      document.title = `${eventState.data?.title ?? 'Соревнование'} · SportGate`
  }, [eventState.data, eventState.errorStatus])
  const base = `/e/${encodeURIComponent(slug)}`
  const links = [
    ['start-list', 'Протокол'],
    ['schedule', 'Расписание'],
    ['live', 'Табло'],
    ['results', 'Результаты'],
  ]
  if (eventState.errorStatus === 404) {
    return (
      <main className={styles.main}>
        <h1>Соревнование не найдено</h1>
        <Link className={styles.button} to="/">
          К соревнованиям
        </Link>
      </main>
    )
  }
  return (
    <PublicConnectionContext value={offline || cached}>
      <div className={styles.root}>
        <a className={styles.skip} href="#public-content">
          К содержимому
        </a>
        <header className={styles.header}>
          <Link className={styles.brand} to={roleHomeTarget(roles, isAuthenticated)} aria-label="На главную">
            <span className={styles.logo} aria-hidden="true">
              S
            </span>{' '}
            SportGate
          </Link>
          <div>
            <Link to={base}>
              <strong>{eventState.data?.title ?? 'Соревнование'}</strong>
            </Link>
            <p>
              {eventState.data
                ? `${date(eventState.data.eventStartAt, eventState.data.venue?.timezone)} · ${eventState.data.venue?.city ?? 'Площадка не указана'}`
                : 'Загружаем сведения…'}
            </p>
          </div>
          <ThemeToggle />
        </header>
        {(offline || cached) && (
          <div className={`${styles.notice} ${styles.noPrint}`} role="status">
            Нет связи, показаны последние данные. Табло обновится после
            подключения.
          </div>
        )}
        <table className={styles.printFrame} role="presentation">
          <thead aria-hidden="true">
            <tr>
              <td>
                <div className={styles.printSpacer} />
              </td>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <main id="public-content" className={styles.main}>
                  <DataState {...eventState} />
                  {eventState.data && (
                    <Outlet key={slug} context={{ event: eventState.data }} />
                  )}
                </main>
              </td>
            </tr>
          </tbody>
        </table>
        <footer className={styles.footer}>SportGate · sportgate.pro</footer>
        <nav className={styles.nav} aria-label="Разделы соревнования">
          {links.map(([path, label]) =>
            path.includes('#') ? (
              <Link key={path} to={`${base}/${path}`}>
                {label}
              </Link>
            ) : (
              <NavLink key={path} to={`${base}/${path}`}>
                {label}
              </NavLink>
            ),
          )}
        </nav>
      </div>
    </PublicConnectionContext>
  )
}

export function LegacyEventRedirect() {
  const { eventCode = '' } = useParams()
  const { search, hash } = useLocation()
  return (
    <Navigate
      replace
      to={`/e/${encodeURIComponent(eventCode)}${search}${hash}`}
    />
  )
}
