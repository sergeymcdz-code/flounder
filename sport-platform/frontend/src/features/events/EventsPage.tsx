import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../utils/formatDateTime'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getPublicEvents, type Event } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import styles from './EventsPage.module.css'

export function EventsPage() {
  const [events, setEvents] = useState<Event[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function loadEvents() {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const response = await getPublicEvents()
      setEvents(response)
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : 'Не удалось загрузить список соревнований.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadEvents()
  }, [])

  if (isLoading) {
    return <LoadingState message="Загружаем соревнования…" />
  }

  if (errorMessage) {
    return <ErrorState message={errorMessage} onRetry={() => void loadEvents()} />
  }

  return (
    <section className={styles.page}>
      <div className={styles.hero}>
        <p className={styles.eyebrow}>Календарь соревнований</p>
        <h1 className={styles.title}>Соревнования по плаванию</h1>
        <p className={styles.subtitle}>
          Расписание, табло и результаты — на телефоне
        </p>
      </div>

      {events.length === 0 ? (
        <div className={styles.emptyState}>
          Сейчас нет опубликованных соревнований.
        </div>
      ) : (
        <div className={styles.grid}>
          {events.map((event) => (
            <article key={event.id} className={styles.card}>
              <div className={styles.cardHeader}>
                <StatusBadge kind="event" code={event.status} />{event.status === 'LIVE' && <span>Сегодня</span>}
              </div>

              <div className={styles.cardBody}>
                <h2 className={styles.cardTitle}>{event.title}</h2>

                <p className={styles.cardDescription}>
                  {event.description?.trim() ||
                    'Подробная информация о соревновании доступна на странице соревнования.'}
                </p>
              </div>

              <dl className={styles.metaList}>


                <div className={styles.metaItem}>
                  <dt>Дата соревнования</dt>
                  <dd>
                    {formatDateTime(event.eventStartAt, event.venue?.timezone)} — {formatDateTime(event.eventEndAt, event.venue?.timezone)}
                  </dd>
                </div>                <div className={styles.metaItem}>
                  <dt>Регистрация</dt>
                  <dd>
                    {formatDateTime(event.registrationOpenAt, event.venue?.timezone)} —{' '}
                    {formatDateTime(event.registrationCloseAt, event.venue?.timezone)}
                  </dd>
                </div>
              </dl>

              <div className={styles.cardFooter}>
                <Link
                  className={styles.primaryAction}
                  to={`/e/${event.publicSlug}`}
                >
                  Подробнее
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}