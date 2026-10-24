import { statusLabel } from '../../components/ui/status-dictionary'
import { useEffect, useState } from 'react'
import { Navigate, NavLink, useSearchParams } from 'react-router'
import { getMyEvents, type Event } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import { EmptyState } from '../../components/ui/EmptyState'
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
  const { session, isOrganizer, currentOrgId, memberships = [], isPlatformAdmin } = useAuth()
  const canCreate = isPlatformAdmin || memberships.some(m => m.organizationId === currentOrgId && ['owner', 'admin', 'organizer'].includes(m.memberRole))
  const [search] = useSearchParams()
  const accessToken = session?.accessToken

  const [statusFilter, setStatusFilter] = useState('')
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
          const data = await getMyEvents(authenticatedAccessToken, isOrganizer || isPlatformAdmin, isPlatformAdmin ? undefined : currentOrgId)

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
    }, [accessToken, currentOrgId, isOrganizer, isPlatformAdmin])

  const visibleEvents = events.filter(event => !statusFilter || event.status === statusFilter)
  const activeEvents = events.filter(event => event.status !== 'ARCHIVED')
  if (!isLoading && !errorMessage && activeEvents.length === 1 && search.get('all') !== '1') return <Navigate replace to={`/organizer/events/${activeEvents[0].id}`} />

  return (
    <section className={styles.page}>
      <div className={styles.titleRow}>
        <div>
          <p className={styles.eyebrow}>Организатор</p>
          <h1 className={styles.title}>Мои соревнования</h1>
        </div>

        {canCreate && <NavLink className="fl-btn fl-btn-primary" to="/organizer/events/new">
          Создать соревнование
        </NavLink>}
      </div>

      {!canCreate && <p className={styles.stateCard}>Для создания соревнования нужны права владельца, администратора или организатора в выбранной организации. Обратитесь к её администратору.</p>}
      <label className={`fl-field ${styles.filter}`}>Статус соревнования<select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="">Все статусы</option>{[...new Set(events.map(event => event.status))].map(status => <option key={status} value={status}>{statusLabel('event', status)}</option>)}</select></label>
      {isLoading ? (
        <div className={styles.stateCard}>Загружаем соревнования…</div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        visibleEvents.length === 0 ? (
          <EmptyState title={events.length ? 'Соревнований с таким статусом нет' : 'У вас пока нет соревнований'}
            action={events.length ? <button className="fl-btn fl-btn-secondary" onClick={() => setStatusFilter('')}>Сбросить фильтр</button>
              : canCreate ? <NavLink className="fl-btn fl-btn-primary" to="/organizer/events/new">Создать соревнование</NavLink> : null} />
        ) : (
          <div className={`${styles.tableWrap} fl-table-scroll`}>
            <table className={`${styles.table} fl-table fl-events-table`}>
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
                {visibleEvents.map((event) => (
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
                          className="fl-btn fl-btn-secondary fl-btn-sm"
                          to={`/organizer/events/${encodeURIComponent(event.id)}`}
                        >
                          Открыть
                        </NavLink>

                        {event.publicSlug ? (
                          <NavLink
                            className="fl-btn fl-btn-ghost fl-btn-sm"
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