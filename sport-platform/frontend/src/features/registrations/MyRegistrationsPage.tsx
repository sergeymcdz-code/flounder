import { apiFetch } from '../../api/http'
import { formatDateTime } from '../../utils/formatDateTime'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { DocumentsPanel } from '../documents/DocumentsPanel'
import { ApiError } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import type {
  Registration,
} from './registration.types'
import styles from './MyRegistrationsPage.module.css'

function getParticipantName(registration: Registration) {
  const { firstName, lastName, middleName } = registration.participantSnapshot
  return [lastName, firstName, middleName].filter(Boolean).join(' ')
}
function getStatusHint(registration: Registration, email: string) {
  if (['SUBMITTED', 'UNDER_REVIEW'].includes(registration.status)) return `Организатор проверит заявку, письмо придёт на ${email || 'ваш email'}`
  if (registration.status === 'WAITLISTED') return 'Место освободится — сообщим письмом'
  if (['PAID', 'ADMITTED'].includes(registration.status) || registration.status === 'CONFIRMED' && ['PAID', 'NOT_REQUIRED'].includes(registration.paymentStatus ?? '')) return 'Ждите стартовый протокол'
  if (registration.status === 'CONFIRMED') return 'Оплатите участие'
  if (['REJECTED', 'NEEDS_CORRECTION'].includes(registration.status)) return registration.reviewNote ?? 'Свяжитесь с организатором'
  return 'Участие отменено'
}
export function MyRegistrationsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [searchParams] = useSearchParams()

  const [email, setEmail] = useState('')
  useEffect(() => { if (!accessToken) return; let active = true; apiFetch<{ email: string }>('/api/v1/auth/me', { accessToken }).then(user => { if (active) setEmail(user.email) }).catch(() => {}); return () => { active = false } }, [accessToken])
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

      <h1 className={styles.title}>Мои заявки</h1><Link to="/">Подать ещё заявку</Link>

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
            const statusHint = getStatusHint(registration, email)

            return (
              <article className={styles.card} key={registration.id}>
                <div className={styles.cardHeader}>
                  <div>
                    <h2>{registration.eventTitle || 'Соревнование'}</h2><p>{registration.disciplineName || 'Вид программы'} · {formatDateTime(registration.eventStartAt, registration.venueTimezone)}</p>

                    <h3 className={styles.participantName}>
                      {getParticipantName(registration)}
                    </h3>
                  </div>

                  <StatusBadge kind="registration" code={registration.status} /><StatusBadge kind="payment" code={registration.paymentStatus ?? (registration.status === 'PAID' ? 'PAID' : 'PENDING')} />
                </div>

                <dl className={styles.facts}>
                  <div>
                    <dt>Отправлена</dt>
                    <dd>{formatDateTime(registration.submittedAt, registration.venueTimezone)}</dd>
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
                    className={styles.statusHint}
                  >
                    Что дальше: {statusHint}
                  </p>
                ) : null}

                {registration.reviewNote ? (
                  <div className={styles.reviewNote}>
                    <strong>Комментарий организатора</strong>
                    <p>{registration.reviewNote}</p>
                  </div>
                ) : null}

                {registration.status === 'CONFIRMED' && !['PAID', 'NOT_REQUIRED'].includes(registration.paymentStatus ?? '') ? (
                  <div className={styles.actions}>
                    <Link
                      className={styles.primaryButton}
                      to={`/my/registrations/${registration.id}/payment`}
                    >
                      К оплате
                    </Link>
                  </div>
                ) : null}


              </article>
            )
          })}
          <details><summary>Документы спортсмена</summary>
          {accessToken && [...new Map(registrations.map((r) => [r.participantProfileId, r])).values()].map((r) => (
            <div key={r.participantProfileId}>
              <h2>{getParticipantName(r)}</h2>
              <DocumentsPanel profileId={r.participantProfileId} accessToken={accessToken} />
            </div>
          ))}
          </details>
        </div>
      ) : null}
    </section>
  )
}