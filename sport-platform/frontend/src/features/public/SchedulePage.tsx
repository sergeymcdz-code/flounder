import { useCallback, useContext } from 'react'
import { Link } from 'react-router'
import { publicApi } from '../../api/public.api'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { PublicConnectionContext, usePublicEvent } from './publicContext'
import { localTimeLabel, time } from './format'
import { SessionTimeline } from './SessionTimeline'
import { SCHEDULE_POLL_MS, usePublicData } from './usePublicData'
import styles from './EventPage.module.css'

export function SchedulePage() {
  const event = usePublicEvent()
  const disconnected = useContext(PublicConnectionContext)
  const load = useCallback(
    (signal: AbortSignal) => publicApi.schedule(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load, SCHEDULE_POLL_MS)
  const sessions = [...new Set(state.data?.map(item => item.sessionName))].map(name => {
    const items = state.data!.filter(item => item.sessionName === name).sort((a, b) => a.sequenceInSession - b.sequenceInSession)
    return {
      id: name, name, startsAt: items[0]?.scheduledAt,
      entries: items.map(item => ({
        id: `${name}-${item.sequenceInSession}`, title: `Заплыв ${item.heatNumber}`,
        startsAt: item.scheduledAt, detail: item.disciplineName, status: item.status,
      })),
    }
  })
  const shiftedAt = state.data?.find(item => item.scheduleShiftedAt)?.scheduleShiftedAt
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  return <div className={styles.page}>
    <PageHeader title="Расписание" meta={`${localTimeLabel(event.eventStartAt, event.venue?.timezone)}. Время старта ориентировочное. Текущий заплыв выделен.`} />
    {shiftedAt && <Notice tone="warning"><span role="status">Расписание сдвинуто в {time(shiftedAt, event.venue?.timezone)}</span></Notice>}
    {state.loading && <div className={styles.card} role="status" aria-label="Загружаем расписание…">
      <p>Загружаем расписание…</p>
      {[0, 1, 2].map(key => <div key={key} className={styles.skeleton} aria-hidden="true" />)}
    </div>}
    {state.error && !(disconnected && state.data !== undefined) && <Notice tone="danger">
      <p>{state.errorStatus ? `Сервер ответил ошибкой (${state.errorStatus}). ${state.error}` : state.error}</p>
      {state.data !== undefined && <p>Показано последнее расписание. Повторите обновление.</p>}
      <Button onClick={state.retry}>Повторить</Button>
    </Notice>}
    {state.data && !state.data.length && <EmptyState title="Расписание ещё не опубликовано" action={<Button as={Link} to={base}>К соревнованию</Button>}><p>Вернитесь позже: организатор ещё готовит расписание.</p></EmptyState>}
    {sessions.length > 0 && <section className={styles.card} aria-label="Сессии и заплывы">
      <SessionTimeline sessions={sessions} timezone={event.venue?.timezone} headingLevel={2} />
    </section>}
  </div>
}
