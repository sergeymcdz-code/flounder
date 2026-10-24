import { useEffect, useMemo, useState } from 'react'
import { NavLink } from 'react-router'
import { getMyEvents, type Event } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import {
  getEventStatusLabel,
  getEventStatusTone,
  normalizeEventStatus,
} from './event-status'
import styles from './OrganizerDashboardPage.module.css'

function getStatusClassName(status: string): string {
  const classes = { neutral: styles.statusDraft, info: styles.statusClosed, success: styles.statusPublished, warning: styles.statusDefault, danger: styles.statusLive }
  return classes[getEventStatusTone(status)]
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

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'Нет доступа к кабинету организатора.'
    }

    return error.message || 'Не удалось загрузить данные кабинета.'
  }

  return 'Не удалось загрузить данные кабинета.'
}

export function OrganizerDashboardPage() {
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

        async function loadDashboard() {
        setIsLoading(true)
        setErrorMessage(null)

        try {
            const result = await getMyEvents(authenticatedAccessToken)

            if (isMounted) {
            setEvents(result)
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

        void loadDashboard()

        return () => {
        isMounted = false
        }
    }, [accessToken])

  const statistics = useMemo(() => {
    const now = Date.now()

    return {
      total: events.length,
      drafts: events.filter(
        (event) => normalizeEventStatus(event.status) === 'DRAFT',
      ).length,
      registrationOpen: events.filter(
        (event) => normalizeEventStatus(event.status) === 'REGISTRATIONOPEN',
      ).length,
      upcoming: events.filter((event) => {
        const startAt = new Date(event.eventStartAt).getTime()
        return !Number.isNaN(startAt) && startAt > now
      }).length,
    }
  }, [events])

  const recentEvents = useMemo(
    () =>
      [...events]
        .sort(
          (left, right) =>
            new Date(right.eventStartAt).getTime() -
            new Date(left.eventStartAt).getTime(),
        )
        .slice(0, 5),
    [events],
  )

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <p className={`${styles.eyebrow} fl-eyebrow`}>Личный кабинет</p>
          <h1 className={styles.title}>Панель организатора</h1>
          <p className={styles.description}>
            Управляйте своими соревнованиями, отслеживайте статусы и быстро переходите
            к ключевым действиям по соревнованиям.
          </p>
        </div>

        <NavLink className={styles.primaryButton} to="/organizer/events/new">
          Создать соревнование
        </NavLink>
      </header>

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {isLoading ? (
        <div className={styles.loadingCard}>Загружаем данные кабинета…</div>
      ) : (
        <>
          <div className={styles.statistics}>
            <article className={styles.statisticCard}>
              <span className={styles.statisticLabel}>Всего соревнований</span>
              <strong className={styles.statisticValue}>{statistics.total}</strong>
            </article>

            <article className={styles.statisticCard}>
              <span className={styles.statisticLabel}>Черновики</span>
              <strong className={styles.statisticValue}>{statistics.drafts}</strong>
            </article>

            <article className={styles.statisticCard}>
              <span className={styles.statisticLabel}>Открыта регистрация</span>
              <strong className={styles.statisticValue}>
                {statistics.registrationOpen}
              </strong>
            </article>

            <article className={styles.statisticCard}>
              <span className={styles.statisticLabel}>Предстоящие</span>
              <strong className={styles.statisticValue}>{statistics.upcoming}</strong>
            </article>
          </div>

          <article className={styles.eventsCard}>
            <div className={styles.sectionHeader}>
              <div>
                <h2 className={styles.sectionTitle}>Ближайшие соревнования</h2>
                <p className={styles.sectionDescription}>
                  Последние соревнования, с которыми вы работаете в кабинете.
                </p>
              </div>

              <NavLink className="fl-btn fl-btn-secondary" to="/organizer/events">
                Все соревнования
              </NavLink>
            </div>

            {recentEvents.length === 0 ? (
              <div className={styles.emptyState}>
                <h3 className={styles.emptyTitle}>Пока нет соревнований</h3>
                <p className={styles.emptyDescription}>
                  Создайте первое соревнование, чтобы открыть регистрацию участников и начать
                  подготовку соревнования.
                </p>
                <NavLink className={styles.primaryButton} to="/organizer/events/new">
                  Создать соревнование
                </NavLink>
              </div>
            ) : (
              <div className={styles.eventList}>
                {recentEvents.map((event) => (
                  <article className={styles.eventItem} key={event.id}>
                    <div className={styles.eventInformation}>
                      <div className={styles.eventHeading}>
                        <h3 className={styles.eventTitle}>{event.title}</h3>
                        <span
                          className={[
                            styles.status,
                            getStatusClassName(event.status),
                          ].join(' ')}
                        >
                          {getEventStatusLabel(event.status)}
                        </span>
                      </div>

                      <p className={styles.eventDate}>{formatDate(event.eventStartAt)}</p>

                      {event.description ? (
                        <p className={styles.eventDescription}>{event.description}</p>
                      ) : null}
                    </div>

                    <div className={styles.eventActions}>
                      <NavLink
                        className="fl-btn fl-btn-secondary"
                        to={`/organizer/events/${encodeURIComponent(event.id)}`}
                      >
                        Управлять соревнованием
                      </NavLink>

                      {event.publicSlug ? (
                        <NavLink
                          className={styles.textLink}
                          to={`/e/${encodeURIComponent(event.publicSlug)}`}
                        >
                          Открыть публичную страницу
                        </NavLink>
                      ) : null}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </article>
        </>
      )}
    </section>
  )
}