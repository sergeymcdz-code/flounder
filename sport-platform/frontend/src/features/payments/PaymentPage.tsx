import { useEffect, useState } from 'react'
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router'
import { ApiError } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type {
  Registration,
  RegistrationStatus,
} from '../../features/registrations/registration.types'
import {
  createPaymentForRegistration,
  getPaymentForRegistration,
  type Payment,
} from '../../api/payments.api'
import styles from './PaymentPage.module.css'

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите снова.'
    }

    if (error.status === 403) {
      return 'У вас нет доступа к оплате этой заявки.'
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
    CREATED: 'Платёж создан',
    PENDING: 'Ожидает оплаты',
    WAITING_FOR_CAPTURE: 'Ожидает подтверждения',
    SUCCEEDED: 'Оплата подтверждена',
    CANCELED: 'Платёж отменён',
    FAILED: 'Ошибка оплаты',
    REFUNDED: 'Средства возвращены',
  }

  return labels[status] ?? status
}

function registrationStatusLabel(status: RegistrationStatus) {
  const labels: Record<RegistrationStatus, string> = {
    SUBMITTED: 'На рассмотрении',
    CONFIRMED: 'Подтверждена',
    REJECTED: 'Отклонена',
    NEEDS_CORRECTION: 'Требует уточнения',
    CANCELLED: 'Отменена',
  }

  return labels[status]
}

function paymentStatusClassName(status: string) {
  const statusClasses: Record<string, string> = {
    CREATED: styles.statusPending,
    PENDING: styles.statusPending,
    WAITING_FOR_CAPTURE: styles.statusWaiting,
    SUCCEEDED: styles.statusSucceeded,
    CANCELED: styles.statusCanceled,
    FAILED: styles.statusFailed,
    REFUNDED: styles.statusNeutral,
  }

  return `${styles.statusBadge} ${
    statusClasses[status] ?? styles.statusNeutral
  }`
}

function getPaymentUnavailableCopy(registration: Registration) {
  if (registration.status === 'SUBMITTED') {
    return {
      title: 'Заявка ещё рассматривается',
      message:
        'Оплата станет доступна после подтверждения заявки организатором.',
    }
  }

  if (registration.status === 'NEEDS_CORRECTION') {
    return {
      title: 'Требуется уточнение данных',
      message:
        'Оплата будет доступна после того, как организатор подтвердит заявку.',
    }
  }

  if (registration.status === 'REJECTED') {
    return {
      title: 'Заявка отклонена',
      message: 'Оплата для отклонённой заявки недоступна.',
    }
  }

  if (registration.status === 'CANCELLED') {
    return {
      title: 'Заявка отменена',
      message: 'Оплата для отменённой заявки недоступна.',
    }
  }

  return {
    title: 'Оплата недоступна',
    message: 'Для этой заявки сейчас нельзя создать платёж.',
  }
}

function paymentProviderLabel(provider: string) {
  const labels: Record<string, string> = {
    MANUAL: 'Ручная оплата',
    YOOKASSA: 'ЮKassa',
  }

  return labels[provider] ?? provider
}

export function PaymentPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { registrationId: registrationIdFromPath } = useParams<{
    registrationId: string
  }>()
  const [searchParams] = useSearchParams()

  const registrationId =
    registrationIdFromPath ?? searchParams.get('registrationId')

  const accessToken = session?.accessToken
  const paymentStatusFromReturn = searchParams.get('status')

  const [registration, setRegistration] =
    useState<Registration | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreatingPayment, setIsCreatingPayment] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', {
        replace: true,
        state: {
          from: registrationId
            ? `/my/registrations/${registrationId}/payment`
            : '/my/registrations',
        },
      })

      return
    }

    if (!registrationId) {
      setErrorMessage('Идентификатор заявки не указан.')
      setIsLoading(false)
      return
    }

    const resolvedAccessToken = accessToken
    const resolvedRegistrationId = registrationId
    let isMounted = true

    async function loadPage() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const registrations = await getMyRegistrations(resolvedAccessToken)

        const ownRegistration = registrations.find(
          (item) => item.id === resolvedRegistrationId,
        )

        if (!ownRegistration) {
          if (isMounted) {
            setRegistration(null)
            setPayment(null)
            setErrorMessage('Заявка не найдена или недоступна.')
          }

          return
        }

        if (!isMounted) {
          return
        }

        setRegistration(ownRegistration)

        if (ownRegistration.status !== 'CONFIRMED') {
          setPayment(null)
          return
        }

        try {
          const loadedPayment = await getPaymentForRegistration(
            resolvedRegistrationId,
            resolvedAccessToken,
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
            getErrorMessage(error, 'Не удалось загрузить страницу оплаты.'),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadPage()

    return () => {
      isMounted = false
    }
  }, [accessToken, navigate, registrationId])

  async function handleCreatePayment() {
    if (!registrationId || !accessToken || !registration) {
      return
    }

    if (registration.status !== 'CONFIRMED') {
      return
    }

    setIsCreatingPayment(true)
    setErrorMessage(null)

    try {
      const createdPayment = await createPaymentForRegistration(
        registrationId,
        accessToken,
      )

      setPayment(createdPayment)

      if (createdPayment.provider === 'YOOKASSA') {
        if (!createdPayment.confirmationUrl) {
          setErrorMessage(
            'Платёж создан, но ссылка на оплату не получена. Попробуйте позже.',
          )

          return
        }

        window.location.assign(createdPayment.confirmationUrl)
      }
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось создать платёж.'),
      )
    } finally {
      setIsCreatingPayment(false)
    }
  }

  if (isLoading) {
    return (
      <section className={styles.page}>
        <div className={styles.stateCard}>
          <h2>Загружаем оплату</h2>
          <p>Проверяем статус заявки и данные платежа.</p>
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

        <Link className={styles.backLink} to="/my/registrations">
          ← К моим заявкам
        </Link>
      </section>
    )
  }

  if (!registration) {
    return null
  }

  const isConfirmed = registration.status === 'CONFIRMED'
  const unavailableCopy = getPaymentUnavailableCopy(registration)

  const isManualAwaitingConfirmation =
    payment?.provider === 'MANUAL' &&
    (payment.status === 'CREATED' || payment.status === 'PENDING')

  const canPayThroughYooKassa =
    payment?.provider === 'YOOKASSA' &&
    Boolean(payment.confirmationUrl) &&
    (payment.status === 'CREATED' || payment.status === 'PENDING')

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/my/registrations">
        ← К моим заявкам
      </Link>

      <p className={styles.eyebrow}>Оплата участия</p>

      <h1 className={styles.title}>Оплата заявки</h1>

      <p className={styles.description}>
        Проверьте статус заявки и выполните оплату участия, если она
        требуется.
      </p>

      <div className={styles.content}>
        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <div>
              <p className={styles.cardLabel}>Заявка</p>
              <h2 className={styles.cardTitle}>Участие в соревновании</h2>
            </div>

            <span className={styles.registrationBadge}>
              {registrationStatusLabel(registration.status)}
            </span>
          </div>

          <dl className={styles.facts}>
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
          </dl>

          {registration.reviewNote ? (
            <div className={styles.reviewNote}>
              <strong>Комментарий организатора</strong>
              <p>{registration.reviewNote}</p>
            </div>
          ) : null}
        </article>

        <article className={styles.card}>
          <div className={styles.cardHeader}>
            <div>
              <p className={styles.cardLabel}>Платёж</p>
              <h2 className={styles.cardTitle}>Оплата участия</h2>
            </div>

            {payment ? (
              <span className={paymentStatusClassName(payment.status)}>
                {paymentStatusLabel(payment.status)}
              </span>
            ) : null}
          </div>

          {!isConfirmed ? (
            <div className={styles.paymentUnavailable}>
              <strong>{unavailableCopy.title}</strong>
              <p>{unavailableCopy.message}</p>
            </div>
          ) : null}

          {isConfirmed && payment ? (
            <>
              <dl className={styles.facts}>
                <div>
                  <dt>Сумма</dt>
                  <dd className={styles.paymentAmount}>
                    {payment.amount} {payment.currency}
                  </dd>
                </div>

                <div>
                  <dt>Статус</dt>
                  <dd>{paymentStatusLabel(payment.status)}</dd>
                </div>

                <div>
                  <dt>Способ оплаты</dt>
                  <dd>{paymentProviderLabel(payment.provider)}</dd>
                </div>

                {payment.paidAt ? (
                  <div>
                    <dt>Подтверждён</dt>
                    <dd>
                      {new Intl.DateTimeFormat('ru-RU', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                        timeZone: 'Europe/Moscow',
                      }).format(new Date(payment.paidAt))}
                    </dd>
                  </div>
                ) : null}
              </dl>

              {canPayThroughYooKassa ? (
                <a
                  className={styles.primaryButton}
                  href={payment.confirmationUrl ?? undefined}
                >
                  Перейти к оплате
                </a>
              ) : null}

              {isManualAwaitingConfirmation ? (
                <div className={styles.paymentDescription}>
                  Платёж создан. После оплаты дождитесь подтверждения
                  оператором.
                </div>
              ) : null}

              {payment.status === 'SUCCEEDED' ? (
                <div className={styles.paymentSuccess}>
                  Оплата подтверждена. Ваша заявка успешно оплачена.
                </div>
              ) : null}

              {payment.status === 'CANCELED' ||
              payment.status === 'FAILED' ? (
                <div className={styles.paymentFailure}>
                  Платёж не был завершён. Свяжитесь с организатором или
                  оператором, чтобы уточнить дальнейшие действия.
                </div>
              ) : null}
            </>
          ) : null}

          {isConfirmed && !payment ? (
            <>
              <p className={styles.paymentDescription}>
                Создайте платёж, чтобы оплатить участие в соревновании.
              </p>

              <button
                className={styles.primaryButton}
                disabled={isCreatingPayment}
                onClick={() => void handleCreatePayment()}
                type="button"
              >
                {isCreatingPayment
                  ? 'Создаём платёж…'
                  : 'Создать платёж'}
              </button>
            </>
          ) : null}
        </article>

        {paymentStatusFromReturn === 'success' ? (
          <div className={styles.successBanner} role="status">
            Возврат из платёжного сервиса выполнен. Проверяем актуальный
            статус оплаты.
          </div>
        ) : null}

        {paymentStatusFromReturn === 'failed' ? (
          <div className={styles.errorBanner} role="alert">
            Платёжный сервис сообщил об ошибке оплаты. Проверьте статус
            платежа выше или повторите попытку.
          </div>
        ) : null}

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}
      </div>
    </section>
  )
}