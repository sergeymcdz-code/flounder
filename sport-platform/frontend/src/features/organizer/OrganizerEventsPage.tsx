import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { getMyEvents, type Event } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import { getEventStatusLabel, getEventStatusTone } from './event-status'
import styles from './OrganizerEventsPage.module.css'

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к списку мероприятий организатора.'
    }

    return error.message || 'Не удалось загрузить мероприятия.'
  }

  return 'Не удалось загрузить мероприятия.'
}

function formatDate(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'Дата не указана'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function statusClassName(status: string): string {
  switch (getEventStatusTone(status)) {
    case 'draft':
      return `${styles.statusBadge} ${styles.statusDraft}`
    case 'published':
      return `${styles.statusBadge} ${styles.statusPublished}`
    case 'open':
      return `${styles.statusBadge} ${styles.statusOpen}`
    case 'closed':
      return `${styles.statusBadge} ${styles.statusClosed}`
    case 'live':
      return `${styles.statusBadge} ${styles.statusLive}`
    case 'completed':
      return `${styles.statusBadge} ${styles.statusCompleted}`
    case 'archived':
      return `${styles.statusBadge} ${styles.statusArchived}`
    default:
      return `${styles.statusBadge} ${styles.statusNeutral}`
  }
}

export function OrganizerEventsPage() {
  const { session } = useAuth()
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
          const data = await getMyEvents(authenticatedAccessToken)

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
    }, [accessToken])

  return (
    <section className={styles.page}>
      <div className={styles.titleRow}>
        <div>
          <p className={styles.eyebrow}>Организатор</p>
          <h1 className={styles.title}>Мои мероприятия</h1>
        </div>

        <NavLink className={styles.createLink} to="/organizer/events/new">
          Создать мероприятие
        </NavLink>
      </div>

      {isLoading ? (
        <div className={styles.stateCard}>Загружаем мероприятия…</div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        events.length === 0 ? (
          <div className={styles.stateCard}>
            У вас пока нет мероприятий. Создайте первое событие, чтобы начать работу.
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Мероприятие</th>
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
                        <span className={styles.eventCode}>{event.publicSlug}</span>
                      ) : null}

                      {event.description ? (
                        <p className={styles.eventDescription}>{event.description}</p>
                      ) : null}
                    </td>

                    <td>{formatDate(event.eventStartAt)}</td>

                    <td>
                      <span className={statusClassName(event.status)}>
                        {getEventStatusLabel(event.status)}
                      </span>
                    </td>

                    <td>
                      <div className={styles.actions}>
                        <NavLink
                          className={styles.secondaryLink}
                          to={`/organizer/events/${encodeURIComponent(event.id)}/registrations`}
                        >
                          Заявки
                        </NavLink>

                        {event.publicSlug ? (
                          <NavLink
                            className={styles.openLink}
                            to={`/events/${encodeURIComponent(event.publicSlug)}`}
                          >
                            Открыть
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