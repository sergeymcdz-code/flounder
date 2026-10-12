import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import type {
  Registration,
  RegistrationStatus,
} from './registration.types'
import styles from './MyRegistrationsPage.module.css'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

function getStatusLabel(status: RegistrationStatus) {
  const labels: Record<RegistrationStatus, string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    NEEDS_CORRECTION: 'Требует уточнения',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function getStatusClassName(status: RegistrationStatus) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    REJECTED: styles.statusRejected,
    NEEDS_CORRECTION: styles.statusCorrection,
    CANCELLED: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status]}`
}

function getParticipantName(registration: Registration) {
  const { firstName, lastName, middleName } =
    registration.participantSnapshot

  return [lastName, firstName, middleName].filter(Boolean).join(' ')
}

function getStatusHint(status: RegistrationStatus) {
  const hints: Record<RegistrationStatus, string | null> = {
    SUBMITTED:
      'Организатор проверяет данные заявки. Оплата станет доступна после подтверждения.',
    CONFIRMED:
      'Заявка подтверждена. Перейдите к оплате участия.',
    NEEDS_CORRECTION:
      'Организатор запросил уточнение данных. Ознакомьтесь с комментарием и свяжитесь с организатором при необходимости.',
    REJECTED:
      'Заявка отклонена. Оплата для неё недоступна.',
    CANCELLED:
      'Заявка отменена. Оплата для неё недоступна.',
  }

  return hints[status]
}

function getStatusHintClassName(status: RegistrationStatus) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.hintSubmitted,
    CONFIRMED: styles.hintConfirmed,
    NEEDS_CORRECTION: styles.hintCorrection,
    REJECTED: styles.hintRejected,
    CANCELLED: styles.hintCancelled,
  }

  return `${styles.statusHint} ${classes[status]}`
}

export function MyRegistrationsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [searchParams] = useSearchParams()

  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function loadRegistrations() {
    if (!accessToken) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const response = await getMyRegistrations(accessToken)
      setRegistrations(response)
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'Сессия истекла. Войдите снова.'
          : error instanceof ApiError
            ? error.message
            : 'Не удалось загрузить ваши заявки.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadRegistrations()
  }, [accessToken])

  const createdRegistrationId = searchParams.get('created')

  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>Личный кабинет</p>

      <h1 className={styles.title}>Мои заявки</h1>

      <p className={styles.description}>
        Здесь отображается актуальный статус ваших заявок на соревнования.
      </p>

      {createdRegistrationId ? (
        <div className={styles.successBanner} role="status">
          Заявка успешно отправлена и добавлена в список.
        </div>
      ) : null}

      {isLoading ? (
        <LoadingState message="Загружаем ваши заявки…" />
      ) : null}

      {!isLoading && errorMessage ? (
        <ErrorState
          message={errorMessage}
          onRetry={() => void loadRegistrations()}
        />
      ) : null}

      {!isLoading && !errorMessage && registrations.length === 0 ? (
        <div className={styles.emptyState}>
          <h2>Заявок пока нет</h2>

          <p>Выберите соревнование и подайте первую заявку.</p>

          <Link className={styles.primaryButton} to="/">
            Найти соревнование
          </Link>
        </div>
      ) : null}

      {!isLoading && !errorMessage && registrations.length > 0 ? (
        <div className={styles.list}>
          {registrations.map((registration) => {
            const statusHint = getStatusHint(registration.status)

            return (
              <article className={styles.card} key={registration.id}>
                <div className={styles.cardHeader}>
                  <div>
                    <p className={styles.cardLabel}>Заявка</p>

                    <h2 className={styles.participantName}>
                      {getParticipantName(registration)}
                    </h2>
                  </div>

                  <span
                    className={getStatusClassName(registration.status)}
                  >
                    {getStatusLabel(registration.status)}
                  </span>
                </div>

                <dl className={styles.facts}>
                  <div>
                    <dt>Отправлена</dt>
                    <dd>{formatDate(registration.submittedAt)}</dd>
                  </div>

                  {registration.participantSnapshot.clubName ? (
                    <div>
                      <dt>Клуб</dt>
                      <dd>{registration.participantSnapshot.clubName}</dd>
                    </div>
                  ) : null}

                  {registration.participantSnapshot.city ? (
                    <div>
                      <dt>Город</dt>
                      <dd>{registration.participantSnapshot.city}</dd>
                    </div>
                  ) : null}
                </dl>

                {statusHint ? (
                  <p
                    className={getStatusHintClassName(
                      registration.status,
                    )}
                  >
                    {statusHint}
                  </p>
                ) : null}

                {registration.reviewNote ? (
                  <div className={styles.reviewNote}>
                    <strong>Комментарий организатора</strong>
                    <p>{registration.reviewNote}</p>
                  </div>
                ) : null}

                {registration.status === 'CONFIRMED' ? (
                  <div className={styles.actions}>
                    <Link
                      className={styles.primaryButton}
                      to={`/my/registrations/${registration.id}/payment`}
                    >
                      Перейти к оплате
                    </Link>
                  </div>
                ) : null}

                <p className={styles.technicalId}>
                  ID дисциплины: {registration.eventDisciplineId}
                </p>
              </article>
            )
          })}
        </div>
      ) : null}
    </section>
  )
}