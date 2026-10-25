import { PageHeader } from '../../components/ui/PageHeader'
import { Button } from '../../components/ui/Button'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { participantName, eventLabel } from '../registrations/registration-display'
import { money } from '../../api/registration-payments.api'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  getRegistration,
  reviewRegistration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import type {
  Registration,
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
  const [reload, setReload] = useState(0)
  useEffect(() => { document.title = 'Заявка участника · SportGate' }, [])

  const canReviewRegistration =
    hasRole('platform_admin') || hasRole('organizer')

  const canConfirmPayment =
    hasRole('platform_admin') || hasRole('operator')

  const canManuallyConfirmPayment =
    canConfirmPayment && !isLoading && !errorMessage && payment?.registrationId === registrationId &&
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
  }, [accessToken, registrationId, reload])

  async function handleReview(decision: RegistrationReviewDecision) {
    if (!registration || registration.id !== registrationId || !accessToken || isLoading || isSaving) {
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
    if (!payment || payment.registrationId !== registrationId || !accessToken || isLoading || isSaving) {
      return
    }

    setIsSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updated = await confirmPayment(payment.id, accessToken)

      setPayment(updated)
      setRegistration((current) => current?.status === 'CONFIRMED' ? { ...current, status: 'PAID' } : current)
      setSuccessMessage('Ручная оплата подтверждена.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось подтвердить платёж.'),
      )
    } finally {
      setIsSaving(false)
    }
  }

  const paymentCode = payment?.status === 'SUCCEEDED' ? 'PAID' : payment?.status === 'REFUNDED' ? 'REFUNDED' : ['CREATED', 'PENDING'].includes(payment?.status ?? '') ? 'PENDING' : paymentStatusLabel(payment?.status ?? '')
  return <section className={styles.page}>
    <Button as={Link} variant="ghost" size="sm" className="fl-back-link" to="/operator/payments">← К списку заявок</Button>
    <p className="fl-eyebrow">Проверка заявки</p>
    <PageHeader title="Заявка участника" badge={registration && registration.id === registrationId && <StatusBadge kind="registration" code={registration.status} />} meta={registration && registration.id === registrationId ? `${participantName(registration)} · ${eventLabel(registration)}` : 'Данные участника и оплата'} />
    {isLoading && <LoadingState message="Загружаем заявку и данные платежа…" />}
    {errorMessage && <ErrorState message={errorMessage} onRetry={() => setReload(value => value + 1)} />}
    {!isLoading && !errorMessage && !registration && <EmptyState title="Заявка не найдена" action={<Button as={Link} to="/operator/payments">К списку заявок</Button>}><p>Откройте другую заявку из списка.</p></EmptyState>}
    {registration && registration.id === registrationId && <div className={styles.content}>
      <article className={styles.card}>
        <h2>Данные заявки</h2>
        <dl className={styles.details}>
          <div><dt>Участник</dt><dd>{participantName(registration)}</dd></div>
          <div><dt>Соревнование</dt><dd>{eventLabel(registration)}</dd></div>
          <div><dt>Вид программы</dt><dd>{registration.disciplineName || 'Вид программы не указан'}</dd></div>
          <div><dt>Клуб</dt><dd>{registration.participantClubName || registration.participantSnapshot.clubName || 'Не указан'}</dd></div>
          {registration.reviewNote && <div><dt>Комментарий организатора</dt><dd>{registration.reviewNote}</dd></div>}
        </dl>
      </article>
      <article className={styles.card}>
        <div className={styles.cardHeader}><h2>Оплата</h2>{payment && payment.registrationId === registrationId && <StatusBadge kind="payment" code={paymentCode} />}</div>
        {payment && payment.registrationId === registrationId ? <>
          <dl className={styles.details}>
            <div><dt>Сумма</dt><dd className={styles.amount}>{money(payment.amount, payment.currency)}</dd></div>
            <div><dt>Способ оплаты</dt><dd>{payment.provider === 'MANUAL' ? 'Ручная оплата' : payment.provider === 'YOOKASSA' ? 'ЮKassa' : 'Другой способ'}</dd></div>
          </dl>
          {canManuallyConfirmPayment && <Button variant="primary" loading={isSaving} loadingLabel="Подтверждаем оплату…" title={isSaving ? 'Дождитесь сохранения' : undefined} onClick={() => void handleConfirmPayment()}>Подтвердить ручную оплату</Button>}
          {payment.provider === 'YOOKASSA' && <Notice>Оплата через ЮKassa подтверждается автоматически после уведомления от платёжного сервиса.</Notice>}
        </> : <Notice>{isLoading ? 'Загружаем данные платежа…' : errorMessage ? 'Данные платежа недоступны. Повторите загрузку.' : 'Платёж для этой заявки ещё не создан.'}</Notice>}
      </article>
      {canReviewRegistration ? <article className={styles.card}>
        <h2>Решение по заявке</h2>
        <label className="fl-field">Комментарий для участника<textarea disabled={isSaving || isLoading} title={isSaving || isLoading ? 'Дождитесь сохранения' : undefined} onChange={event => setReviewNote(event.target.value)} rows={4} value={reviewNote} /></label>
        <div className={styles.actions}>
          <Button variant="primary" disabled={isSaving || isLoading} title={isSaving || isLoading ? 'Дождитесь сохранения' : undefined} onClick={() => void handleReview('CONFIRMED')}>Подтвердить заявку</Button>
          <Button disabled={isSaving || isLoading} title={isSaving || isLoading ? 'Дождитесь сохранения' : undefined} onClick={() => void handleReview('NEEDS_CORRECTION')}>Запросить уточнение</Button>
          <Button variant="danger" disabled={isSaving || isLoading} title={isSaving || isLoading ? 'Дождитесь сохранения' : undefined} onClick={() => void handleReview('REJECTED')}>Отклонить заявку</Button>
        </div>
      </article> : <Notice>Оператор может просматривать заявку и подтверждать ручную оплату. Решение по заявке принимает организатор или администратор.</Notice>}
      {isSaving && <p role="status">Дождитесь сохранения.</p>}
      {successMessage && <Notice tone="info"><span role="status">{successMessage}</span></Notice>}
    </div>}
  </section>
}
