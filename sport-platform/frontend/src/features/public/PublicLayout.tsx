import { useCallback, useContext, useEffect, useState } from 'react'
import {
  Link,
  Navigate,
  Outlet,
  useLocation,
  useParams,
} from 'react-router'
import { publicApi, PublicApiError } from '../../api/public.api'
import { usePublicData } from './usePublicData'
import { date } from './format'
import styles from './Public.module.css'
import { PublicConnectionContext } from './publicContext'
import { DirectoryLayout } from './DirectoryLayout'
import { ProductHeader } from '../../components/ProductHeader'
import { Tabs } from '../../components/ui/Tabs'
import { PageHeader } from '../../components/ui/PageHeader'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'

export const SLOW_LOADING_MS = 3_000
const slowLoadingMessage = 'Загружаем данные… Сервер отвечает дольше обычного, это займёт несколько секунд.'

/** UX-24 (UI-104): a cold public API answers in 6–10 s; explain the wait instead of looking frozen. */
function useSlowLoading(active: boolean) {
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    if (!active) return
    const timer = window.setTimeout(() => setSlow(true), SLOW_LOADING_MS)
    return () => { window.clearTimeout(timer); setSlow(false) }
  }, [active])
  return active && slow
}

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
  const slow = useSlowLoading(Boolean(loading && data === undefined))
  if (data !== undefined && disconnected) return null
  if (loading && data === undefined) return <LoadingState message={slow ? slowLoadingMessage : undefined} />
  if (!error) return null
  return <ErrorState message={`${error}${data !== undefined ? ' Показаны последние данные. Обновление продолжится при подключении.' : ''}`} onRetry={retry} />
}

const disciplineResultsPath = /^\/events\/[^/]+\/disciplines\/[^/]+\/results\/?$/

export function PublicLayout() {
  const { pathname } = useLocation()
  return pathname.startsWith('/e/') || disciplineResultsPath.test(pathname) ? <EventPublicLayout /> : <DirectoryLayout />
}

function EventPublicLayout() {
  const params = useParams()
  const slug = params.slug ?? params.publicSlug ?? ''
  const { pathname } = useLocation()
  // Pages with their own document.title set it after the layout (child effects run first).
  const pageTitle = disciplineResultsPath.test(pathname)
  const load = useCallback(
    (signal: AbortSignal) => publicApi.event(slug, signal),
    [slug],
  )
  const eventState = usePublicData(load)
  // Standings only add a navigation tab: requested in parallel, never awaited by the page.
  // The print-only poster does not need them, so it skips the request on a cold backend.
  const poster = /\/poster\/?$/.test(pathname)
  const loadStandings = useCallback(
    (signal: AbortSignal) => poster ? Promise.reject(new PublicApiError(404, 'Зачёт не нужен плакату')) : publicApi.standings(slug, signal),
    [slug, poster],
  )
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
    else if (eventState.data && !pageTitle)
      document.title = `${eventState.data?.title ?? 'Соревнование'} · SportGate`
  }, [eventState.data, eventState.errorStatus, pageTitle])
  const base = `/e/${encodeURIComponent(slug)}`
  const links = [
    ['start-list', 'Протокол'],
    ['schedule', 'Расписание'],
    ['live', 'Табло'],
    ['results', 'Результаты'],
    ...(standingsState.data && !standingsState.errorStatus ? [['standings', 'Командный зачёт']] : []),
  ]
  return (
    <PublicConnectionContext value={offline || cached}>
      <div className={styles.root}>
        <a className={styles.skip} href="#public-content">
          К содержимому
        </a>
        <ProductHeader publicPage eventContext={eventState.data || (eventState.loading && !eventState.errorStatus) ? <><Link to={base}><strong>{eventState.data?.title ?? 'Соревнование'}</strong></Link>{' · '}<span>{eventState.data ? `${date(eventState.data.eventStartAt, eventState.data.venue?.timezone)} · ${eventState.data.venue?.city ?? 'Площадка не указана'}` : 'Загружаем сведения…'}</span></> : null} />
        {eventState.errorStatus !== 404 && <div className={styles.eventNavigation}>
          <nav className={styles.directoryNav} aria-label="Навигация события"><Button as={Link} to="/" variant="ghost" size="sm" className="fl-back-link">← Календарь</Button>{eventState.data?.organization?.publicSlug && <Button as={Link} variant="ghost" size="sm" to={`/o/${encodeURIComponent(eventState.data.organization.publicSlug)}`}>Страница организации</Button>}</nav>
          {!pathname.replace(/\/$/, '').endsWith('/register') && <div className={styles.nav}><Tabs items={links.map(([path, label]) => ({ to: `${base}/${path}`, label, shortLabel: path === 'standings' ? 'Зачёт' : undefined }))} /></div>}
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
                  {!eventState.data && eventState.errorStatus !== 404 && <PageHeader title="Соревнование" />}
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
