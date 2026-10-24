import { useCallback, useContext, useEffect, useState } from 'react'
import {
  Link,
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
import { DirectoryLayout } from './DirectoryLayout'
import { ProductHeader } from '../../components/ProductHeader'
import { Tabs } from '../../components/ui/Tabs'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'

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
  if (loading && data === undefined) return <LoadingState />
  if (!error) return null
  return <ErrorState message={`${error}${data !== undefined ? ' Показаны последние данные. Обновление продолжится при подключении.' : ''}`} onRetry={retry} />
}

export function PublicLayout() {
  const { pathname } = useLocation()
  return pathname.startsWith('/e/') ? <EventPublicLayout /> : <DirectoryLayout />
}

function EventPublicLayout() {
  const { slug = '' } = useParams()
  const { pathname } = useLocation()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.event(slug, signal),
    [slug],
  )
  const eventState = usePublicData(load)
  const loadStandings = useCallback((signal: AbortSignal) => publicApi.standings(slug, signal), [slug])
  const standingsState = usePublicData(loadStandings)
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
    ...(standingsState.data && !standingsState.errorStatus ? [['standings', 'Зачёт']] : []),
  ]
  return (
    <PublicConnectionContext value={offline || cached}>
      <div className={styles.root}>
        <a className={styles.skip} href="#public-content">
          К содержимому
        </a>
        <ProductHeader publicPage eventContext={<><Link to={base}><strong>{eventState.data?.title ?? 'Соревнование'}</strong></Link>{' · '}<span>{eventState.data ? `${date(eventState.data.eventStartAt, eventState.data.venue?.timezone)} · ${eventState.data.venue?.city ?? 'Площадка не указана'}` : 'Загружаем сведения…'}</span></>} />
        {eventState.errorStatus !== 404 && <div className={styles.eventNavigation}>
          <nav className={styles.directoryNav} aria-label="Навигация события"><Button as={Link} to="/" variant="ghost" size="sm" className="fl-back-link">← Календарь</Button>{eventState.data?.organization?.publicSlug && <Button as={Link} variant="ghost" size="sm" to={`/o/${encodeURIComponent(eventState.data.organization.publicSlug)}`}>Страница организации</Button>}</nav>
          {!pathname.replace(/\/$/, '').endsWith('/register') && <div className={styles.nav}><Tabs items={links.map(([path, label]) => ({ to: `${base}/${path}`, label }))} /></div>}
        </div>}

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
                  {eventState.errorStatus === 404 ? <EmptyState headingLevel={1} title="Соревнование не найдено" action={<Button as={Link} to="/">К соревнованиям</Button>}>Проверьте ссылку или выберите соревнование в календаре.</EmptyState> : <DataState {...eventState} />}
                  {eventState.data && (
                    <Outlet key={slug} context={{ event: eventState.data }} />
                  )}
                </main>
              </td>
            </tr>
          </tbody>
        </table>
        <footer className={styles.footer}>SportGate · sportgate.pro</footer>

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
