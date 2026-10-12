import { useCallback, useEffect, useEffectEvent, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import {
  getOrganizerEvent,
  getOrganizerRegistrations,
} from '../../api/organizer.api'
import { getSessions } from '../../api/sessions.api'
import { getHeats } from '../../api/scheduling.api'
import { formatDate, type EventContext } from './organizer-event-context'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import styles from './OrganizerEventPage.module.css'

export function OrganizerEventLayout() {
  const { eventId = '' } = useParams()
  return <OrganizerEventContent key={eventId} />
}

function OrganizerEventContent() {
  const { eventId = '' } = useParams()
  const { session, logout } = useAuth()
  const token = session?.accessToken ?? ''
  const navigate = useNavigate()
  const location = useLocation()
  const [data, setData] = useState<Omit<
    EventContext,
    'accessToken' | 'busy' | 'refresh' | 'run' | 'error'
  > | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const reportError = useCallback(
    (cause: unknown) => {
      if (cause instanceof ApiError && cause.status === 401) {
        logout()
        navigate('/login', {
          replace: true,
          state: { from: location.pathname },
        })
        return
      }
      setError(
        cause instanceof ApiError && cause.status === 403
          ? 'Нет доступа к этому событию.'
          : cause instanceof Error
            ? cause.message
            : 'Не удалось выполнить действие.',
      )
    },
    [logout, navigate, location.pathname],
  )
  const load = useCallback(async () => {
    const [event, registrations, sessions] = await Promise.all([
      getOrganizerEvent(eventId, token),
      getOrganizerRegistrations(eventId, token),
      getSessions(eventId, token),
    ])
    const heats = (
      await Promise.all(event.disciplines.map((d) => getHeats(d.id, token)))
    ).flat()
    return { event, registrations, sessions, heats }
  }, [eventId, token])
  const refresh = useCallback(async () => {
    setData(await load())
  }, [load])
  const onLoadError = useEffectEvent(reportError)
  useEffect(() => {
    let active = true
    load()
      .then((result) => {
        if (active) setData(result)
      })
      .catch((cause) => {
        if (active) onLoadError(cause)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [load])
  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError('')
    try {
      await action()
      await refresh()
      return true
    } catch (cause) {
      reportError(cause)
      return false
    } finally {
      setBusy(false)
    }
  }
  if (loading) return <LoadingState message="Загружаем событие…" />
  if (!data)
    return (
      <ErrorState
        message={error || 'Событие не найдено.'}
        onRetry={() => {
          void run(refresh)
        }}
      />
    )
  return (
    <div className={styles.page}>
      <Link to="/organizer/events" className={styles.muted}>
        ← Мои события
      </Link>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Кабинет организатора</p>
          <h1>{data.event.title}</h1>
          <p className={styles.muted}>
            {formatDate(data.event.eventStartAt)} ·{' '}
            {data.event.venue?.name ?? 'Площадка события'}
          </p>
        </div>
      </header>
      <nav className={styles.tabs} aria-label="Разделы события">
        {[
          ['', 'Обзор'],
          ['program', 'Сессии и виды'],
          ['entries', 'Заявки'],
          ['heats', 'Заплывы'],
          ['results', 'Результаты'],
        ].map(([path, title]) => (
          <NavLink
            key={path}
            end
            to={`/organizer/events/${eventId}${path ? `/${path}` : ''}`}
            className={({ isActive }) =>
              isActive ? styles.activeTab : styles.tab
            }
          >
            {title}
          </NavLink>
        ))}
      </nav>
      {error && <ErrorState title="Ошибка" message={error} />}
      {busy && <LoadingState message="Выполняем действие…" />}
      <Outlet
        context={
          {
            ...data,
            accessToken: token,
            busy,
            error,
            refresh,
            run,
          } satisfies EventContext
        }
      />
    </div>
  )
}
