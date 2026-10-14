import { useCallback } from 'react'
import { publicApi } from '../../api/public.api'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { localTimeLabel, time } from './format'
import { SCHEDULE_POLL_MS, usePublicData } from './usePublicData'
import styles from './Public.module.css'

export function SchedulePage() {
  const event = usePublicEvent()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.schedule(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load, SCHEDULE_POLL_MS)
  const sessions = [...new Set(state.data?.map((item) => item.sessionName))]
  return (
    <>
      <h1>Расписание</h1>
      <p className={styles.muted}>
        {localTimeLabel(event.eventStartAt, event.venue?.timezone)}. Время старта ориентировочное. Текущий заплыв выделен.
      </p>
      {state.data?.find((item) => item.scheduleShiftedAt)?.scheduleShiftedAt && (
        <p role="status">Расписание сдвинуто в {time(state.data.find((item) => item.scheduleShiftedAt)!.scheduleShiftedAt!, event.venue?.timezone)}</p>
      )}
      <DataState {...state} />
      {state.data && !state.data.length && (
        <p>Расписание ещё не опубликовано.</p>
      )}
      {sessions.map((session) => (
        <section key={session} className={styles.card}>
          <h2>{session}</h2>
          <ol className={styles.list}>
            {state.data
              ?.filter((item) => item.sessionName === session)
              .sort((a, b) => a.sequenceInSession - b.sequenceInSession)
              .map((item) => (
                <li
                  key={`${session}-${item.sequenceInSession}`}
                  className={
                    item.status === 'IN_PROGRESS'
                      ? styles.current
                      : item.status === 'FINISHED' ||
                          item.status === 'COMPLETED'
                        ? styles.past
                        : undefined
                  }
                >
                  <strong>
                    {time(item.scheduledAt, event.venue?.timezone)} · Заплыв {item.heatNumber}
                  </strong>
                  <p>{item.disciplineName}</p>
                  <small>
                    {item.status === 'IN_PROGRESS'
                      ? 'Идёт сейчас'
                      : item.status === 'FINISHED' ||
                          item.status === 'COMPLETED'
                        ? 'Завершён'
                        : item.status === 'CANCELLED'
                          ? 'Отменён'
                          : 'Ожидается'}
                  </small>
                </li>
              ))}
          </ol>
        </section>
      ))}
    </>
  )
}
