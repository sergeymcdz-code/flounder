import { useCallback, useEffect, useState } from 'react'
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

export function DataState({
  loading,
  error,
  retry,
}: {
  loading?: boolean
  error?: string
  retry?: () => void
}) {
  if (loading) return <p role="status">Загружаем данные…</p>
  if (!error) return null
  return (
    <div className={styles.notice} role="alert">
      <p>{error}</p>
      {retry && <button onClick={retry}>Повторить</button>}
    </div>
  )
}

export function PublicLayout() {
  const { slug = '' } = useParams()
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
    if (eventState.data)
      document.title = `${eventState.data?.title ?? 'Соревнование'} · Flounder`
  }, [eventState.data])
  const base = `/e/${encodeURIComponent(slug)}`
  const links = [
    ['schedule', 'Расписание'],
    ['live', 'Live'],
    ['results', 'Результаты'],
    ['live#find-me', 'Найти себя'],
  ]
  return (
    <div className={styles.root}>
      <a className={styles.skip} href="#public-content">
        К содержимому
      </a>
      <header className={styles.header}>
        <Link className={styles.brand} to={base}>
          <span className={styles.logo} aria-hidden="true">
            F
          </span>{' '}
          Flounder
        </Link>
        <div>
          <Link to={base}>
            <strong>{eventState.data?.title ?? 'Соревнование'}</strong>
          </Link>
          <p>
            {eventState.data
              ? `${date(eventState.data.eventStartAt)} · ${eventState.data.venue.city}`
              : 'Загружаем сведения…'}
          </p>
        </div>
      </header>
      {(offline || cached) && (
        <div className={`${styles.notice} ${styles.noPrint}`} role="status">
          Нет связи, показаны последние данные. Live обновится после
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
