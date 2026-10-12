import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  getRegistration,
  reviewRegistration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type {
  Registration,
  RegistrationStatus,
} from '../../features/registrations/registration.types'
import {
  confirmPayment,
  getPaymentForReview,
  type Payment,
} from '../../api/payments.api'
import styles from './OperatorRegistrationPage.module.css'

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для этого действия.'
    }

    if (error.status === 404) {
      return 'Заявка или платёж не найдены.'
    }

    return error.message
  }

  return fallback
}

function paymentStatusLabel(status: string) {
  const labels: Record<string, string> = {
    CREATED: 'Создан',
    PENDING: 'Ожидает оплаты',
    WAITING_FOR_CAPTURE: 'Ожидает подтверждения',
    SUCCEEDED: 'Оплачен',
    CANCELED: 'Отменён',
    FAILED: 'Ошибка оплаты',
    REFUNDED: 'Возвращён',
  }

  return labels[status] ?? status
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

function registrationStatusClassName(status: RegistrationStatus) {
  const classes: Record<RegistrationStatus, string> = {
    SUBMITTED: styles.statusSubmitted,
    CONFIRMED: styles.statusConfirmed,
    NEEDS_CORRECTION: styles.statusCorrection,
    REJECTED: styles.statusRejected,
    CANCELLED: styles.statusCancelled,
  }

  return `${styles.statusBadge} ${classes[status]}`
}

function paymentStatusClassName(status: string) {
  const classes: Record<string, string> = {
    CREATED: styles.paymentPending,
    PENDING: styles.paymentPending,
    WAITING_FOR_CAPTURE: styles.paymentWaiting,
    SUCCEEDED: styles.paymentSucceeded,
    CANCELED: styles.paymentCancelled,
    FAILED: styles.paymentFailed,
    REFUNDED: styles.statusNeutral,
  }

  return `${styles.statusBadge} ${
    classes[status] ?? styles.statusNeutral
  }`
}

export function OperatorRegistrationPage() {
  const { registrationId } = useParams<{ registrationId: string }>()
  const { session, hasRole } = useAuth()
  const accessToken = session?.accessToken

  const [registration, setRegistration] =
    useState<Registration | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(
    null,
  )
  const [reviewNote, setReviewNote] = useState('')

  const canReviewRegistration =
    hasRole('platform_admin') || hasRole('organizer')

  const canConfirmPayment =
    hasRole('platform_admin') || hasRole('operator')

  const canManuallyConfirmPayment =
    canConfirmPayment &&
    payment?.provider === 'MANUAL' &&
    (payment.status === 'CREATED' || payment.status === 'PENDING')

  useEffect(() => {
    if (!registrationId || !accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    const resolvedRegistrationId = registrationId
    let isMounted = true

    async function loadData() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const loadedRegistration = await getRegistration(
          resolvedRegistrationId,
          authenticatedAccessToken,
        )

        if (!isMounted) {
          return
        }

        setRegistration(loadedRegistration)
        setReviewNote(loadedRegistration.reviewNote ?? '')

        try {
          const loadedPayment = await getPaymentForReview(
            resolvedRegistrationId,
            authenticatedAccessToken,
          )

          if (isMounted) {
            setPayment(loadedPayment)
          }
        } catch (error) {
          if (error instanceof ApiError && error.status === 404) {
            if (isMounted) {
              setPayment(null)
            }

            return
          }

          throw error
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(
              error,
              'Не удалось загрузить заявку и данные платежа.',
            ),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadData()

    return () => {
      isMounted = false
    }
  }, [accessToken, registrationId])

  async function handleReview(decision: RegistrationReviewDecision) {
    if (!registration || !accessToken) {
      return
    }

    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await reviewRegistration(
        registration.id,
        {
          decision,
          reviewNote: reviewNote.trim(),
        },
        accessToken,
      )

      setRegistration(updated)
      setReviewNote(updated.reviewNote ?? '')
      setSuccessMessage('Решение по заявке сохранено.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось сохранить решение по заявке.'),
      )
    } finally {
      setIsSaving(false)
    }
  }

  async function handleConfirmPayment() {
    if (!payment || !accessToken) {
      return
    }

    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await confirmPayment(payment.id, accessToken)

      setPayment(updated)
      setSuccessMessage('Ручная оплата подтверждена.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось подтвердить платёж.'),
      )
    } finally {
      setIsSaving(false)
    }
  }

  if (!registrationId || !accessToken) {
    return null
  }

  if (isLoading) {
    return (
      <section className={styles.page}>
        <div className={styles.stateCard}>
          Загружаем заявку и данные платежа…
        </div>
      </section>
    )
  }

  if (errorMessage && !registration) {
    return (
      <section className={styles.page}>
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>

        <NavLink className={styles.backLink} to="/operator/payments">
          ← К списку заявок
        </NavLink>
      </section>
    )
  }

  if (!registration) {
    return null
  }

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/operator/payments">
        ← К списку заявок
      </NavLink>

      <p className={styles.eyebrow}>Проверка заявки</p>

      <div className={styles.titleRow}>
        <h1 className={styles.title}>Заявка участника</h1>

        <span
          className={registrationStatusClassName(registration.status)}
        >
          {registrationStatusLabel(registration.status)}
        </span>
      </div>

      <div className={styles.content}>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Данные заявки</h2>

          <dl className={styles.detailList}>
            <div>
              <dt>ID заявки</dt>
              <dd className={styles.technicalValue}>{registration.id}</dd>
            </div>

            <div>
              <dt>ID события</dt>
              <dd className={styles.technicalValue}>
                {registration.eventId}
              </dd>
            </div>

            <div>
              <dt>ID дисциплины</dt>
              <dd className={styles.technicalValue}>
                {registration.eventDisciplineId}
              </dd>
            </div>

            <div>
              <dt>Статус</dt>
              <dd>{registrationStatusLabel(registration.status)}</dd>
            </div>

            {registration.reviewNote ? (
              <div className={styles.reviewNote}>
                <dt>Комментарий организатора</dt>
                <dd>{registration.reviewNote}</dd>
              </div>
            ) : null}
          </dl>
        </article>

        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <h2 className={styles.cardTitle}>Оплата</h2>

            {payment ? (
              <span className={paymentStatusClassName(payment.status)}>
                {paymentStatusLabel(payment.status)}
              </span>
            ) : null}
          </div>

          {payment ? (
            <>
              <dl className={styles.detailList}>
                <div>
                  <dt>ID платежа</dt>
                  <dd className={styles.technicalValue}>{payment.id}</dd>
                </div>

                <div>
                  <dt>Сумма</dt>
                  <dd className={styles.amount}>
                    {payment.amount} {payment.currency}
                  </dd>
                </div>

                <div>
                  <dt>Способ оплаты</dt>
                  <dd>{payment.provider}</dd>
                </div>

                <div>
                  <dt>Статус платежа</dt>
                  <dd>{paymentStatusLabel(payment.status)}</dd>
                </div>
              </dl>

              {canManuallyConfirmPayment ? (
                <button
                  className={styles.primaryButton}
                  disabled={isSaving}
                  onClick={handleConfirmPayment}
                  type="button"
                >
                  {isSaving
                    ? 'Подтверждаем оплату…'
                    : 'Подтвердить ручную оплату'}
                </button>
              ) : null}

              {payment.provider === 'YOOKASSA' ? (
                <p className={styles.emptyText}>
                  Оплата через YooKassa подтверждается автоматически
                  через webhook платёжного провайдера.
                </p>
              ) : null}
            </>
          ) : (
            <p className={styles.emptyText}>
              Платёж для этой заявки ещё не создан.
            </p>
          )}
        </article>

        {canReviewRegistration ? (
          <article className={styles.card}>
            <h2 className={styles.cardTitle}>Решение по заявке</h2>

            <label className={styles.noteField}>
              <span>Комментарий для участника</span>

              <textarea
                disabled={isSaving}
                onChange={(event) => setReviewNote(event.target.value)}
                rows={4}
                value={reviewNote}
              />
            </label>

            <div className={styles.actionRow}>
              <button
                className={styles.primaryButton}
                disabled={isSaving}
                onClick={() => void handleReview('CONFIRMED')}
                type="button"
              >
                Подтвердить заявку
              </button>

              <button
                className={styles.secondaryButton}
                disabled={isSaving}
                onClick={() => void handleReview('NEEDS_CORRECTION')}
                type="button"
              >
                Запросить уточнение
              </button>

              <button
                className={styles.dangerButton}
                disabled={isSaving}
                onClick={() => void handleReview('REJECTED')}
                type="button"
              >
                Отклонить заявку
              </button>
            </div>
          </article>
        ) : (
          <div className={styles.readOnlyNotice}>
            Оператор может просматривать заявку и подтверждать ручную
            оплату. Решение по заявке принимает организатор или
            администратор.
          </div>
        )}

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}

        {successMessage ? (
          <p className={styles.successMessage} role="status">
            {successMessage}
          </p>
        ) : null}
      </div>
    </section>
  )
}