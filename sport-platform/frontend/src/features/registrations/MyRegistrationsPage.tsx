import { RegistrationPaymentBlock } from '../payments/RegistrationPaymentBlock'
import { paymentInstructions, type PaymentSettings } from '../../api/registration-payments.api'
import { apiFetch } from '../../api/http'
import { formatDateTime } from '../../utils/formatDateTime'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useEffect, useRef, useState } from 'react'
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
import { PageHeader } from '../../components/ui/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { registrationAction } from '../../api/organizer.api'
import { money } from '../../api/registration-payments.api'

function getParticipantName(registration: Registration) {
  const { firstName, lastName, middleName } = registration.participantSnapshot
  return [lastName, firstName, middleName].filter(Boolean).join(' ')
}
function getStatusHint(registration: Registration, email: string) {
  if (['SUBMITTED', 'UNDER_REVIEW'].includes(registration.status)) return `Организатор проверит заявку, письмо придёт на ${email || 'ваш email'}`
  if (registration.status === 'WAITLISTED') return 'Место освободится — сообщим письмом'
  if (['PAID', 'ADMITTED'].includes(registration.status) || registration.status === 'CONFIRMED' && ['PAID', 'WAIVED', 'NOT_REQUIRED'].includes(registration.paymentStatus ?? '')) return 'Ждите стартовый протокол'
  if (registration.status === 'CONFIRMED') return 'Оплатите участие'
  if (['REJECTED', 'NEEDS_CORRECTION'].includes(registration.status)) return registration.reviewNote ?? 'Свяжитесь с организатором'
  return 'Участие отменено'
}
export function MyRegistrationsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [searchParams] = useSearchParams()

  const [instructions, setInstructions] = useState<Record<string, PaymentSettings>>({})
  const [email, setEmail] = useState('')
  useEffect(() => { if (!accessToken) return; let active = true; apiFetch<{ email: string }>('/api/v1/auth/me', { accessToken }).then(user => { if (active) setEmail(user.email) }).catch(() => {}); return () => { active = false } }, [accessToken])
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [withdrawing, setWithdrawing] = useState<Registration | null>(null)
  const [busy, setBusy] = useState(false)
  const [withdrawError, setWithdrawError] = useState<string>()
  const withdrawSummary = useRef<HTMLElement | null>(null)
  useEffect(() => { document.title = 'Мои заявки · SportGate' }, [])

  async function withdraw() {
    if (!withdrawing || !accessToken || busy) return
    setBusy(true)
    setWithdrawError(undefined)
    try {
      await registrationAction(withdrawing.id, 'withdraw', accessToken)
      setRegistrations(rows => rows.map(row => row.id === withdrawing.id ? { ...row, status: 'WITHDRAWN' } : row))
      setWithdrawing(null)
      requestAnimationFrame(() => withdrawSummary.current?.focus())
    } catch (error) {
      setWithdrawError(error instanceof ApiError ? error.message : 'Нет связи. Не удалось отозвать заявку. Повторите попытку.')
    } finally { setBusy(false) }
  }

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
      const events = [...new Set(response.map(r => r.eventId))]
      const settings = await Promise.all(events.map(async id => [id, await paymentInstructions(id, accessToken).catch(() => undefined)] as const))
      setInstructions(Object.fromEntries(settings.filter((entry): entry is readonly [string, PaymentSettings] => entry[1] !== undefined)))
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'Сессия истекла. Войдите снова.'
          : error instanceof ApiError
            ? error.message
            : error instanceof TypeError ? 'Нет связи. Проверьте подключение и повторите попытку.' : 'Не удалось загрузить ваши заявки.'

      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadRegistrations()
  }, [accessToken])

  const createdRegistrationId = searchParams.get('created')

  return <section className={styles.page}>
    <p className="fl-eyebrow">Личный кабинет</p>
    <PageHeader title="Мои заявки" meta="Актуальные статусы участия и оплаты соревнований."
      actions={<Button as={Link} to="/">Подать ещё заявку</Button>} />
    {createdRegistrationId && <Notice tone="info"><span role="status">Заявка успешно отправлена и добавлена в список.</span></Notice>}
    {isLoading && <LoadingState message="Загружаем ваши заявки…" />}
    {errorMessage && <ErrorState message={errorMessage} onRetry={() => void loadRegistrations()} />}
    {!isLoading && !errorMessage && !registrations.length && <EmptyState title="Заявок пока нет"
      action={<Button as={Link} to="/" variant="primary">Найти соревнование</Button>}>
      <p>Выберите соревнование и подайте первую заявку.</p>
    </EmptyState>}
    {registrations.length > 0 && <>
      <div className={styles.cards}>
        <DataTable cards minWidth="calc(var(--space-16) * 12)" caption="Ваши заявки на соревнования" rows={registrations} rowKey={row => row.id} columns={[
          { key: 'participant', header: 'Участник', rowHeader: true, render: row => <div><strong>{getParticipantName(row)}</strong><span className={styles.meta}>{[row.participantSnapshot.clubName, row.participantSnapshot.city].filter(Boolean).join(' · ')}</span></div> },
          { key: 'status', header: 'Статус', render: row => <StatusBadge kind="registration" code={row.status} /> },
          { key: 'event', header: 'Соревнование', render: row => <div><strong>{row.eventTitle || 'Соревнование'}</strong><span className={styles.meta}>{row.disciplineName || 'Вид программы'} · {formatDateTime(row.eventStartAt, row.venueTimezone)}</span></div> },
          { key: 'payment', header: 'Оплата', render: row => <StatusBadge kind="payment" code={row.paymentStatus === 'WAIVED' ? 'PAID' : row.paymentStatus ?? (row.status === 'PAID' ? 'PAID' : 'NOT_REQUIRED')} /> },
          { key: 'fee', header: 'Взнос', numeric: true, render: row => money(row.feeAmount, row.feeCurrency ?? 'RUB') },
          { key: 'actions', header: 'Что дальше', render: row => <div className={styles.next}>
            <p>Что дальше: {getStatusHint(row, email)}</p>
            <div className={styles.actions}>
              {['CONFIRMED', 'ADMITTED'].includes(row.status) && instructions[row.eventId]?.enabled !== false && (!row.paymentStatus || row.paymentStatus === 'PENDING') && <Button as={Link} variant="primary" to={`/my/registrations/${row.id}/payment`}>К оплате</Button>}
              <details className={styles.details}><summary className="fl-btn fl-btn-ghost">Подробнее</summary>
                <p>Отправлена: {formatDateTime(row.submittedAt, row.venueTimezone)}</p>
                <RegistrationPaymentBlock registration={row} settings={instructions[row.eventId]} />
                {row.reviewNote && <Notice><strong>Комментарий организатора</strong><p>{row.reviewNote}</p></Notice>}
                {!['WITHDRAWN', 'CANCELLED', 'REJECTED'].includes(row.status) && <Button variant="danger" onClick={event => { withdrawSummary.current = event.currentTarget.closest('details')?.querySelector('summary') ?? null; setWithdrawing(row); setWithdrawError(undefined) }}>Отозвать заявку</Button>}
              </details>
            </div>
          </div> },
        ]} />
      </div>
      <details className={styles.documents}><summary className="fl-btn fl-btn-ghost">Документы спортсмена</summary>
        {accessToken && [...new Map(registrations.map(row => [row.participantProfileId, row])).values()].map(row => <div key={row.participantProfileId}>
          <h2>{getParticipantName(row)}</h2><DocumentsPanel profileId={row.participantProfileId} accessToken={accessToken} />
        </div>)}
      </details>
    </>}
    {withdrawing && <ConfirmDialog title="Отозвать заявку?" description={`${getParticipantName(withdrawing)} · ${withdrawing.eventTitle || 'Соревнование'}. Участие будет отменено. Возврат оплаты согласуйте с организатором.`}
      confirmLabel="Отозвать заявку" tone="danger" busy={busy} busyLabel="Отзываем…" error={withdrawError} onClose={() => setWithdrawing(null)} onConfirm={() => void withdraw()} />}
  </section>
}
