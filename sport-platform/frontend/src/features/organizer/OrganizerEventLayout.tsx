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
import { getCompetitionUnitDetails } from '../../api/results.api'
import type {
  OrganizerEventDetails,
  OrganizerRegistration,
  Session,
  Heat,
  GenerationResult,
  GenerationSummary,
} from '../../api/organizer.types'
import { getHeats } from '../../api/scheduling.api'
import {
  formatDate,
  type EventContext,
  type RefreshTarget,
} from './organizer-event-context'
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
  const [data, setData] = useState<{
    event: OrganizerEventDetails
    registrations: OrganizerRegistration[]
    sessions: Session[]
    heats: Heat[]
  } | null>(null)
  const [generationResults, setGenerationResults] = useState<
    Record<string, GenerationResult>
  >({})
  const [lastGeneration, setLastGeneration] =
    useState<GenerationSummary | null>(null)
  const [dialogCount, setDialogCount] = useState(0)
  const registerDialog = useCallback(() => {
    setDialogCount((count) => count + 1)
    return () => setDialogCount((count) => count - 1)
  }, [])
  function saveGeneration(
    results: { eventDisciplineId: string; result: GenerationResult }[],
    durationMs: number,
    violations: GenerationSummary['violations'] = [],
  ) {
    setGenerationResults((previous) => ({
      ...previous,
      ...Object.fromEntries(
        results.map((d) => [d.eventDisciplineId, d.result]),
      ),
    }))
    setLastGeneration({
      durationMs,
      violations: [
        ...violations,
        ...results.flatMap((d) =>
          d.result.violations.map((v) => ({
            ...v,
            eventDisciplineId: v.eventDisciplineId ?? d.eventDisciplineId,
          })),
        ),
      ],
      excludedRegistrationIds: results.flatMap(
        (d) => d.result.excludedRegistrationIds,
      ),
    })
  }
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
  async function run(
    action: () => Promise<unknown>,
    target: RefreshTarget = 'event',
  ) {
    setBusy(true)
    setError('')
    try {
      await action()
      if (target === 'event') await refresh()
      else if (target === 'registrations') {
        const registrations = await getOrganizerRegistrations(eventId, token)
        setData((previous) =>
          previous ? { ...previous, registrations } : previous,
        )
      } else if (typeof target === 'object') {
        const heat = await getCompetitionUnitDetails(target.heatId, token)
        setData((previous) =>
          previous
            ? {
                ...previous,
                heats: previous.heats.map((h) =>
                  h.id === target.heatId ? { ...h, ...heat } : h,
                ),
              }
            : previous,
        )
      }
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
      {data.event.status === 'ARCHIVED' && <p role="status">Событие в архиве. Доступен только просмотр.</p>}
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
      {error && dialogCount === 0 && (
        <ErrorState title="Ошибка" message={error} />
      )}
      {busy && <LoadingState message="Выполняем действие…" />}
      <Outlet
        context={
          {
            ...data,
            accessToken: token,
            busy,
            error,
            generationResults,
            lastGeneration,
            saveGeneration,
            registerDialog,
            refresh,
            run,
          } satisfies EventContext
        }
      />
    </div>
  )
}
