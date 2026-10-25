import { useMemo } from 'react'
import { useHeaderContext } from '../../components/header-context'
import { Button } from '../../components/ui/Button'
import { OverflowMenu } from '../../components/ui/OverflowMenu'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { PageHeader } from '../../components/ui/PageHeader'
import { Tabs, type TabItem } from '../../components/ui/Tabs'
import { Notice } from '../../components/ui/Notice'
import { isHeatPublished } from './organizer-event-context'
import { useCallback, useEffect, useEffectEvent, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
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
import { RouteErrorBoundary } from '../../components/RouteErrorBoundary'
import { ErrorState } from '../../components/ErrorState'
import styles from './OrganizerEventPage.module.css'

export function OrganizerEventLayout() {
  const { eventId = '' } = useParams()
  return <OrganizerEventContent key={eventId} />
}

function OrganizerEventContent() {
  const { eventId = '' } = useParams()
  const { session, logout, isOrganizer, isPlatformAdmin, isOperator, hasRole } = useAuth()
  const token = session?.accessToken ?? ''
  const judgeOnly = hasRole('judge') && !isOrganizer && !isPlatformAdmin && !isOperator
  const navigate = useNavigate()
  const location = useLocation()
  const [wide, setWide] = useState(() => window.matchMedia?.('(min-width: 1440px)').matches ?? false)
  useEffect(() => { const media = window.matchMedia?.('(min-width: 1440px)'); const update = () => setWide(media?.matches ?? false); media?.addEventListener('change', update); return () => media?.removeEventListener('change', update) }, [])
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
  const clearError = useCallback(() => setError(''), [])
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
          ? 'Нет доступа к этому соревнованию.'
          : cause instanceof ApiError && cause.status === 404 ? 'Соревнование не найдено.'
          : cause instanceof Error
            ? cause.message
            : 'Не удалось выполнить действие.',
      )
    },
    [logout, navigate, location.pathname],
  )
  const load = useCallback(async () => {
    const [event, registrations, sessions] = await Promise.all([
      getOrganizerEvent(eventId, token, isOrganizer || isPlatformAdmin),
      judgeOnly ? Promise.resolve([]) : getOrganizerRegistrations(eventId, token),
      getSessions(eventId, token),
    ])
    const heats = (
      await Promise.all(event.disciplines.map((d) => getHeats(d.id, token)))
    ).flat()
    return { event, registrations, sessions, heats }
  }, [eventId, token, judgeOnly, isOrganizer, isPlatformAdmin])
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
  useEffect(() => { if (data?.event) document.title = `${data.event.title} · Соревнование · SportGate` }, [data?.event.title])
  const headerContext = useMemo(() => data ? <>{data.event.title} · {formatDate(data.event.eventStartAt, data.event.venue?.timezone)}{data.event.venue?.city ? ` · ${data.event.venue.city}` : ''}</> : null, [data])
  useHeaderContext(headerContext)
  if (loading) return <><PageHeader title="Соревнование" /><LoadingState message="Загружаем соревнование…" /></>
  if (!data)
    return (
      <><PageHeader title="Соревнование" /><ErrorState
        message={error || 'Соревнование не найдено.'}
        onRetry={() => {
          void run(refresh)
        }}
      /></>
    )
  return (
    <div className={styles.page}>
      <Button as={Link} variant="ghost" size="sm" to={judgeOnly ? '/operator/results' : '/organizer/events?all=1'} className="fl-back-link">
        {judgeOnly ? '← Судейство' : '← Все соревнования'}
      </Button>
      <PageHeader title={data.event.title} badge={<StatusBadge kind="event" code={data.event.status} />} meta={<>{formatDate(data.event.eventStartAt, data.event.venue?.timezone)} — {formatDate(data.event.eventEndAt, data.event.venue?.timezone)} · {data.event.venue?.name ?? 'Площадка соревнования'}{data.event.venue?.city ? ` · ${data.event.venue.city}` : ''}{data.event.venue?.lanes ? ` · ${data.event.venue.lanes} дорожек` : ''}{data.event.venue?.courseLength ? ` · ${data.event.venue.courseLength} м` : ''}</>} actions={<><Link className="fl-btn fl-btn-ghost" to={`/e/${data.event.publicSlug}`}>Публичная страница ↗</Link><OverflowMenu label="Действия соревнования" items={[{ label: 'QR-плакат A4', to: `/e/${data.event.publicSlug}/poster` }, ...(data.event.status === 'COMPLETED' && (isOrganizer || isPlatformAdmin) ? [{ label: 'В архив', to: `/organizer/events/${eventId}?archive=1` }] : [])]} /></>} />
      {data.event.status === 'ARCHIVED' && <Notice>Соревнование в архиве. Доступен только просмотр.</Notice>}
      <div className={styles.tabsViewport}>
      <Tabs items={([
        { path: '', label: 'Главная' },
        { path: 'program', label: 'Программа', count: data.event.disciplines.length },
        { path: 'entries', label: 'Заявки', count: data.registrations.length, attention: data.registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW'].includes(r.status)).length },
        ...(isOrganizer || isPlatformAdmin || isOperator ? [{ path: 'payments', label: 'Оплата' }] : []),
        { path: 'heats', label: 'Заплывы', count: data.heats.length, status: data.heats.length && data.heats.every(h => isHeatPublished(h.status)) ? 'опубликовано' : 'черновик' },
        { path: 'results', label: 'Результаты', count: data.heats.filter(h => ['FINISHED', 'COMPLETED'].includes(h.status)).length },
        { path: 'diploma', label: 'Грамоты' },
        { path: 'standings', label: 'Командный зачёт' },
        { path: 'notifications', label: 'Уведомления' },
        ...(isOrganizer || isPlatformAdmin ? [{ path: 'analytics', label: 'Аналитика' }] : []),
      ]).filter(item => (!judgeOnly || item.path === 'results') && (wide || !['diploma', 'standings', 'notifications', 'analytics'].includes(item.path))).map(({ path, ...item }) => ({ ...item, to: `/organizer/events/${eventId}${path ? `/${path}` : ''}` }) satisfies TabItem)} />
      {!wide && !judgeOnly && <OverflowMenu label="Другие разделы соревнования" items={[
        { label: 'Грамоты', to: `/organizer/events/${eventId}/diploma` },
        { label: 'Командный зачёт', to: `/organizer/events/${eventId}/standings` },
        { label: 'Уведомления', to: `/organizer/events/${eventId}/notifications` },
        ...(isOrganizer || isPlatformAdmin ? [{ label: 'Аналитика', to: `/organizer/events/${eventId}/analytics` }] : []),
      ].map(item => ({ ...item, active: location.pathname === item.to }))} />}
      </div>
      {error && dialogCount === 0 && (
        <ErrorState title="Ошибка" message={error} />
      )}
      {busy && <LoadingState message="Выполняем действие…" />}
      <RouteErrorBoundary key={location.pathname}>
      <Outlet
        context={
          {
            ...data,
            accessToken: token,
            busy,
            error,
            clearError,
            generationResults,
            lastGeneration,
            saveGeneration,
            registerDialog,
            refresh,
            run,
          } satisfies EventContext
        }
      />
      </RouteErrorBoundary>
    </div>
  )
}
