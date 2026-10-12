import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import { getEventById, type EventResponse } from '../../api/events.api'
import {
  getRegistrations,
  type GetRegistrationsOptions,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type {
  Registration,
  RegistrationStatus,
  ParticipantSnapshot,
} from '../../features/registrations/registration.types'
import styles from './OrganizerRegistrationsPage.module.css'

type RegistrationFilter = 'ALL' | RegistrationStatus

const FILTER_OPTIONS: Array<{
  value: RegistrationFilter
  label: string
}> = [
  { value: 'ALL', label: 'Все статусы' },
  { value: 'SUBMITTED', label: 'На рассмотрении' },
  { value: 'CONFIRMED', label: 'Подтверждённые' },
  { value: 'NEEDS_CORRECTION', label: 'Требуют уточнения' },
  { value: 'REJECTED', label: 'Отклонённые' },
  { value: 'CANCELLED', label: 'Отменённые' },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для просмотра заявок.'
    }

    if (error.status === 404) {
      return 'Событие не найдено.'
    }

    return error.message
  }

  return fallback
}

function registrationStatusLabel(status: RegistrationStatus) {
  const labels: Record<RegistrationStatus, string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    NEEDS_CORRECTION: 'Требует уточнения',
    REJECTED: 'Отклонена',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function registrationStatusClassName(
  status: RegistrationStatus,
  stylesObject: Record<string, string>,
) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: stylesObject.statusSubmitted,
    CONFIRMED: stylesObject.statusConfirmed,
    NEEDS_CORRECTION: stylesObject.statusCorrection,
    REJECTED: stylesObject.statusRejected,
    CANCELLED: stylesObject.statusCancelled,
  }

  return `${stylesObject.statusBadge} ${classes[status]}`
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Не указано'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

function getSnapshotString(
  snapshot: ParticipantSnapshot | null | undefined,
  property: keyof ParticipantSnapshot,
): string {
  const value = snapshot?.[property]
  return typeof value === 'string' ? value.trim() : ''
}

function getParticipantName(registration: Registration) {
  const snapshot = registration.participantSnapshot

  const lastName = getSnapshotString(snapshot, 'lastName')
  const firstName = getSnapshotString(snapshot, 'firstName')
  const middleName = getSnapshotString(snapshot, 'middleName')

  const fullName = [lastName, firstName, middleName]
    .filter(Boolean)
    .join(' ')

  return fullName || 'Участник без указания ФИО'
}

function getParticipantSecondaryInfo(registration: Registration) {
  const snapshot = registration.participantSnapshot

  const clubName = getSnapshotString(snapshot, 'clubName')
  const city = getSnapshotString(snapshot, 'city')

  return [clubName, city].filter(Boolean).join(' · ')
}

export function OrganizerRegistrationsPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [event, setEvent] = useState<EventResponse | null>(null)
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [statusFilter, setStatusFilter] =
    useState<RegistrationFilter>('SUBMITTED')
  const [disciplineFilter, setDisciplineFilter] = useState('')

  const [isLoadingEvent, setIsLoadingEvent] = useState(true)
  const [isLoadingRegistrations, setIsLoadingRegistrations] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const isLoading = isLoadingEvent || isLoadingRegistrations

  const disciplineNameById = useMemo(() => {
    if (!event) {
      return new Map<string, string>()
    }

    return new Map(
      (event.disciplines ?? []).map((discipline) => [
        discipline.id,
        discipline.name,
      ]),
    )
  }, [event])

  useEffect(() => {
    if (!eventId || !accessToken) {
      setIsLoadingEvent(false)
      setIsLoadingRegistrations(false)
      return
    }

    const currentEventId = eventId
    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadEvent() {
      setIsLoadingEvent(true)
      setErrorMessage(null)

      try {
        const loadedEvent = await getEventById(
          currentEventId,
          authenticatedAccessToken,
        )

        if (isMounted) {
          setEvent(loadedEvent)
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(error, 'Не удалось загрузить событие.'),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoadingEvent(false)
        }
      }
    }

    void loadEvent()

    return () => {
      isMounted = false
    }
  }, [accessToken, eventId])

  useEffect(() => {
    if (!eventId || !accessToken) {
      setIsLoadingRegistrations(false)
      return
    }

    const currentEventId = eventId
    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadRegistrations() {
      setIsLoadingRegistrations(true)
      setErrorMessage(null)

      const options: GetRegistrationsOptions = {
        eventId: currentEventId,
      }

      if (statusFilter !== 'ALL') {
        options.status = statusFilter
      }

      try {
        const loadedRegistrations = await getRegistrations(
          authenticatedAccessToken,
          options,
        )

        if (isMounted) {
          setRegistrations(loadedRegistrations)
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(error, 'Не удалось загрузить заявки.'),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoadingRegistrations(false)
        }
      }
    }

    void loadRegistrations()

    return () => {
      isMounted = false
    }
  }, [accessToken, eventId, statusFilter])

  const visibleRegistrations = useMemo(() => {
    if (!disciplineFilter) {
      return registrations
    }

    return registrations.filter(
      (registration) => registration.eventDisciplineId === disciplineFilter,
    )
  }, [disciplineFilter, registrations])

  if (!eventId || !accessToken) {
    return null
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/organizer/events">
        ← К моим событиям
      </Link>

      <p className={styles.eyebrow}>Панель организатора</p>

      <h1 className={styles.title}>
        {event ? `Заявки: ${event.title}` : 'Заявки события'}
      </h1>

      <p className={styles.description}>
        Просматривайте заявки участников, фильтруйте их по дисциплинам и
        принимайте решение по каждой заявке.
      </p>

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {isLoading ? (
        <div className={styles.stateCard}>Загружаем событие и заявки…</div>
      ) : null}

      {!isLoading && !errorMessage && event ? (
        <>
          <div className={styles.filtersCard}>
            <label className={styles.field}>
              <span>Дисциплина</span>

              <select
                onChange={(event) => setDisciplineFilter(event.target.value)}
                value={disciplineFilter}
              >
                <option value="">Все дисциплины</option>

                {(event.disciplines ?? []).map((discipline) => (
                  <option key={discipline.id} value={discipline.id}>
                    {discipline.name}
                  </option>
                ))}
              </select>
            </label>

            <label className={styles.field}>
              <span>Статус заявки</span>

              <select
                onChange={(event) =>
                  setStatusFilter(event.target.value as RegistrationFilter)
                }
                value={statusFilter}
              >
                {FILTER_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <div className={styles.resultsCount}>
              Найдено заявок: <strong>{visibleRegistrations.length}</strong>
            </div>
          </div>

          {visibleRegistrations.length === 0 ? (
            <div className={styles.stateCard}>
              <h2>Заявок не найдено</h2>

              <p>
                Попробуйте изменить фильтры или дождитесь новых заявок от
                участников.
              </p>
            </div>
          ) : (
            <div className={styles.registrationsList}>
              {visibleRegistrations.map((registration) => {
                const participantName = getParticipantName(registration)
                const secondaryInfo =
                  getParticipantSecondaryInfo(registration)

                const disciplineName =
                  disciplineNameById.get(registration.eventDisciplineId) ??
                  'Дисциплина не найдена'

                return (
                  <article
                    className={styles.registrationCard}
                    key={registration.id}
                  >
                    <div className={styles.cardHeader}>
                      <div>
                        <p className={styles.cardLabel}>{disciplineName}</p>

                        <h2 className={styles.participantName}>
                          {participantName}
                        </h2>

                        {secondaryInfo ? (
                          <p className={styles.secondaryInfo}>
                            {secondaryInfo}
                          </p>
                        ) : null}
                      </div>

                      <span
                        className={registrationStatusClassName(
                          registration.status,
                          styles,
                        )}
                      >
                        {registrationStatusLabel(registration.status)}
                      </span>
                    </div>

                    <div className={styles.cardFooter}>
                      <span>Подана: {formatDate(registration.submittedAt)}</span>

                      <Link
                        className={styles.reviewLink}
                        to={`/operator/registrations/${registration.id}`}
                      >
                        Рассмотреть заявку
                      </Link>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </>
      ) : null}
    </section>
  )
}