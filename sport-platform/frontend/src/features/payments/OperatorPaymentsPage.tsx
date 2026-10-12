import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { ApiError } from '../../api/http'
import { getRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type { Registration } from '../../features/registrations/registration.types'
import styles from './OperatorPaymentsPage.module.css'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этого раздела.'
    }

    return error.message
  }

  return 'Не удалось загрузить список заявок.'
}

function registrationStatusLabel(status: Registration['status']) {
  const labels: Record<Registration['status'], string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    NEEDS_CORRECTION: 'Требует уточнения',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function registrationStatusClassName(status: Registration['status']) {
  const classes: Record<Registration['status'], string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    REJECTED: styles.statusRejected,
    NEEDS_CORRECTION: styles.statusCorrection,
    CANCELLED: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status]}`
}

function shortId(value: string) {
  return value.slice(0, 8)
}

export function OperatorPaymentsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadRegistrations() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getRegistrations(authenticatedAccessToken)

        if (isMounted) {
          setRegistrations(data)
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

    void loadRegistrations()

    return () => {
      isMounted = false
    }
  }, [accessToken])

  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>Панель оператора</p>

      <h1 className={styles.title}>Заявки и платежи</h1>

      <p className={styles.description}>
        Откройте заявку, чтобы проверить данные участника и статус платежа.
      </p>

      {isLoading ? (
        <div className={styles.stateCard}>
          Загружаем список заявок…
        </div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        registrations.length === 0 ? (
          <div className={styles.stateCard}>Заявок пока нет.</div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Заявка</th>
                  <th scope="col">Событие</th>
                  <th scope="col">Статус</th>
                  <th scope="col">
                    <span className={styles.visuallyHidden}>Действия</span>
                  </th>
                </tr>
              </thead>

              <tbody>
                {registrations.map((registration) => (
                  <tr key={registration.id}>
                    <td>
                      <strong className={styles.registrationTitle}>
                        Заявка #{shortId(registration.id)}
                      </strong>

                      <span className={styles.registrationId}>
                        {registration.id}
                      </span>
                    </td>

                    <td>
                      <strong className={styles.eventLabel}>Событие</strong>

                      <span className={styles.registrationId}>
                        {registration.eventId}
                      </span>
                    </td>

                    <td>
                      <span
                        className={registrationStatusClassName(
                          registration.status,
                        )}
                      >
                        {registrationStatusLabel(registration.status)}
                      </span>
                    </td>

                    <td>
                      <NavLink
                        className={styles.openLink}
                        to={`/operator/registrations/${registration.id}`}
                      >
                        Открыть
                      </NavLink>
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