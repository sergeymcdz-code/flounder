import { useEffect, useState } from 'react'
import { Navigate, NavLink, useSearchParams } from 'react-router'
import { getMyEvents, type Event } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../utils/formatDateTime'
import styles from './OrganizerEventsPage.module.css'

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к списку соревнований организатора.'
    }

    return error.message || 'Не удалось загрузить соревнования.'
  }

  return 'Не удалось загрузить соревнования.'
}

export function OrganizerEventsPage() {
  const { session, isOrganizer, isPlatformAdmin } = useAuth()
  const [search] = useSearchParams()
  const accessToken = session?.accessToken

  const [events, setEvents] = useState<Event[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

    useEffect(() => {
      if (!accessToken) {
        setIsLoading(false)
        return
      }

      const authenticatedAccessToken = accessToken
      let isMounted = true

      async function loadEvents() {
        setIsLoading(true)
        setErrorMessage(null)

        try {
          const data = await getMyEvents(authenticatedAccessToken, isOrganizer || isPlatformAdmin)

          if (isMounted) {
            setEvents(data)
          }
        } catch (error) {
          if (isMounted) {
            setErrorMessage(getErrorMessage(error))
          }
        } finally {
          if (isMounted) {
            setIsLoading(false)
          }
        }
      }

      void loadEvents()

      return () => {
        isMounted = false
      }
    }, [accessToken, isOrganizer, isPlatformAdmin])

  const activeEvents = events.filter(event => event.status !== 'ARCHIVED')
  if (!isLoading && !errorMessage && activeEvents.length === 1 && search.get('all') !== '1') return <Navigate replace to={`/organizer/events/${activeEvents[0].id}`} />

  return (
    <section className={styles.page}>
      <div className={styles.titleRow}>
        <div>
          <p className={styles.eyebrow}>Организатор</p>
          <h1 className={styles.title}>Мои соревнования</h1>
        </div>

        {(isOrganizer || isPlatformAdmin) && <NavLink className={styles.createLink} to="/organizer/events/new">
          Создать соревнование
        </NavLink>}
      </div>

      {isLoading ? (
        <div className={styles.stateCard}>Загружаем соревнования…</div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        events.length === 0 ? (
          <div className={styles.stateCard}>
            У вас пока нет соревнований. Создайте первое соревнование, чтобы начать работу.
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Соревнование</th>
                  <th scope="col">Дата</th>
                  <th scope="col">Статус</th>
                  <th scope="col">
                    <span className={styles.visuallyHidden}>Действия</span>
                  </th>
                </tr>
              </thead>

              <tbody>
                {events.map((event) => (
                  <tr key={event.id}>
                    <td>
                      <strong className={styles.eventName}>{event.title}</strong>

                      {event.publicSlug ? (
                        <span className={styles.eventCode}>Публичная страница доступна</span>
                      ) : null}

                      {event.description ? (
                        <p className={styles.eventDescription}>{event.description}</p>
                      ) : null}
                    </td>

                    <td>{formatDateTime(event.eventStartAt, event.venue?.timezone)}</td>

                    <td>
                      <StatusBadge kind="event" code={event.status} />
                    </td>

                    <td>
                      <div className={styles.actions}>
                        <NavLink
                          className={styles.secondaryLink}
                          to={`/organizer/events/${encodeURIComponent(event.id)}`}
                        >
                          Открыть
                        </NavLink>

                        {event.publicSlug ? (
                          <NavLink
                            className={styles.openLink}
                            to={`/e/${encodeURIComponent(event.publicSlug)}`}
                          >
                            Публичная страница ↗
                          </NavLink>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}
    </section>
  )
}