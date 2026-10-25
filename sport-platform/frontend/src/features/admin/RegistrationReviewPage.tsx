import { getOrganizationRequests, approveOrganizationRequest, rejectOrganizationRequest, type OrganizationRegistrationResponse } from '../../api/organizations.api'
import { AdminDialog } from './AdminDialog'
import { adminError } from './admin-utils'
import adminStyles from './Admin.module.css'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../api/http'
import {
  type RegistrationReviewDecision,
  reviewRegistration,
  getRegistrations,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { PageHeader } from '../../components/ui/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { OverflowMenu } from '../../components/ui/OverflowMenu'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { money } from '../../api/registration-payments.api'
import { participantName, eventLabel } from '../registrations/registration-display'
import type { Registration } from '../registrations/registration.types'
import reviewStyles from './RegistrationReviewPage.module.css'

type DecisionOption = {
  value: RegistrationReviewDecision
  label: string
  hint: string
}

const DECISIONS: DecisionOption[] = [
  {
    value: 'CONFIRMED',
    label: 'Подтвердить',
    hint: 'Участник может перейти к оплате участия.',
  },
  {
    value: 'NEEDS_CORRECTION',
    label: 'Запросить уточнение',
    hint: 'Участник увидит комментарий и сможет исправить данные.',
  },
  {
    value: 'REJECTED',
    label: 'Отклонить',
    hint: 'Используйте, если заявка не соответствует условиям соревнования.',
  },
]

function getErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return error instanceof TypeError ? 'Нет связи. Проверьте подключение и повторите попытку.' : 'Не удалось обработать заявку. Попробуйте ещё раз.'
  }

  if (error.status === 403) {
    return 'У вашей учётной записи нет прав на рассмотрение заявок.'
  }

  if (error.status === 404) {
    return 'Заявка не найдена. Обновите список.'
  }

  return error.message
}

export function RegistrationReviewPage() {
  const { session } = useAuth()
  useEffect(() => { document.title = 'Рассмотрение заявки · SportGate' }, [])

  const [query, setQuery] = useState('')
  const [eventId, setEventId] = useState('')
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [selected, setSelected] = useState<Registration | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const accessToken = session?.accessToken
  useEffect(() => {
    let active = true
    setIsLoading(true)
    setLoadError(null)
    if (!accessToken) { setIsLoading(false); return }
    getRegistrations(accessToken).then(rows => { if (active) setRegistrations(rows) })
      .catch(error => { if (active) setLoadError(getErrorMessage(error)) })
      .finally(() => { if (active) setIsLoading(false) })
    return () => { active = false }
  }, [accessToken, reload])
  useEffect(() => {
    if (selected) headingRef.current?.focus()
  }, [selected])
  const [decision, setDecision] =
    useState<RegistrationReviewDecision>('CONFIRMED')
  const [reviewNote, setReviewNote] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!session) {
      return
    }

    const normalizedRegistrationId = selected?.id
    const normalizedReviewNote = reviewNote.trim()

    if (!normalizedRegistrationId) {
      setErrorMessage('Выберите заявку из списка.')
      return
    }

    if (!normalizedReviewNote) {
      setErrorMessage('Добавьте комментарий для участника.')
      return
    }

    setErrorMessage(null)
    setSuccessMessage(null)
    setIsSubmitting(true)

    try {
      const reviewedRegistration = await reviewRegistration(
        normalizedRegistrationId,
        {
          decision,
          reviewNote: normalizedReviewNote,
        },
        session.accessToken,
      )

      const label =
        DECISIONS.find((item) => item.value === reviewedRegistration.status)
          ?.label ??
        DECISIONS.find((item) => item.value === decision)?.label ??
        'Обработана'

      setSuccessMessage(`Заявка успешно обработана: ${label}.`)
      setRegistrations(rows => rows.map(row => row.id === reviewedRegistration.id ? {
        ...row,
        ...reviewedRegistration,
        eventTitle: reviewedRegistration.eventTitle ?? row.eventTitle,
        eventStartAt: reviewedRegistration.eventStartAt ?? row.eventStartAt,
        disciplineName: reviewedRegistration.disciplineName ?? row.disciplineName,
        venueTimezone: reviewedRegistration.venueTimezone ?? row.venueTimezone,
        publicSlug: reviewedRegistration.publicSlug ?? row.publicSlug,
      } : row))
      setSelected(null)
      setReviewNote('')
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  const events = [...new Map(registrations.map(row => [row.eventId, row])).values()]
  const visible = registrations.filter(row => (!eventId || row.eventId === eventId) && `${participantName(row)} ${row.eventTitle || ''}`.toLocaleLowerCase('ru-RU').includes(query.trim().toLocaleLowerCase('ru-RU')))
  function select(row: Registration, nextDecision: RegistrationReviewDecision = 'CONFIRMED') {
    setSelected(row); setReviewNote(''); setDecision(nextDecision); setErrorMessage(null); setSuccessMessage(null)
  }
  return (
    <section className={reviewStyles.page}>
      <p className="fl-eyebrow">Панель администратора</p>
      <PageHeader title="Рассмотрение заявки" meta="Найдите участника, выберите заявку и оставьте понятный комментарий." />
      {isLoading && <LoadingState message="Загружаем заявки…" />}
      {loadError && <ErrorState message={loadError} onRetry={() => setReload(value => value + 1)} />}
      {(!isLoading || registrations.length > 0) && <>
        <form className={reviewStyles.filters} onSubmit={event => event.preventDefault()} role="search">
          <label className="fl-field">Поиск по ФИО или соревнованию<input type="search" value={query} onChange={event => setQuery(event.target.value)} /></label>
          <label className="fl-field">Соревнование<select value={eventId} onChange={event => setEventId(event.target.value)}><option value="">Все соревнования</option>{events.map(row => <option key={row.eventId} value={row.eventId}>{eventLabel(row)}</option>)}</select></label>
        </form>
        <p role="status">Найдено заявок: {visible.length}</p>
        {!visible.length && !loadError ? <EmptyState title={registrations.length ? 'Ничего не найдено' : 'Заявок пока нет'} action={<Button onClick={() => { setQuery(''); setEventId(''); setReload(value => value + 1) }}>{registrations.length ? 'Сбросить фильтры' : 'Обновить список'}</Button>}><p>{registrations.length ? 'Измените поиск или соревнование.' : 'Новые заявки появятся здесь после отправки участниками.'}</p></EmptyState> : visible.length > 0 && <div className={reviewStyles.tableWrap}>
          <DataTable cards minWidth="calc(var(--space-16) * 12)" caption="Заявки участников" rows={visible} rowKey={row => row.id} columns={[
            { key: 'name', header: 'Участник', rowHeader: true, render: row => <div><strong>{participantName(row)}</strong><span className={reviewStyles.meta}>{[row.participantBirthYear ?? row.participantSnapshot.birthDate?.slice(0, 4), row.participantClubName || row.participantSnapshot.clubName].filter(Boolean).join(' · ')}</span></div> },
            { key: 'status', header: 'Статус', render: row => <StatusBadge kind="registration" code={row.status} /> },
            { key: 'event', header: 'Соревнование', render: row => <div>{eventLabel(row)}<span className={reviewStyles.meta}>{row.disciplineName || 'Вид программы'}</span></div> },
            { key: 'payment', header: 'Оплата', render: row => <div><StatusBadge kind="payment" code={row.paymentStatus || 'NOT_REQUIRED'} /><span className={`${reviewStyles.meta} fl-numeric`}>{money(row.feeAmount, row.feeCurrency || 'RUB')}</span></div> },
            { key: 'actions', header: 'Действия', render: row => <div className={reviewStyles.actions}>
              <Button disabled={isSubmitting} title={isSubmitting ? 'Дождитесь сохранения решения' : undefined} onClick={() => select(row)} aria-label={`Выбрать заявку: ${participantName(row)}, ${eventLabel(row)}`}>{selected?.id === row.id ? 'Выбрана' : 'Выбрать'}</Button>
              <OverflowMenu label={`Действия заявки: ${participantName(row)}`} items={DECISIONS.map(option => ({ label: option.label, onSelect: () => select(row, option.value), disabled: isSubmitting, disabledReason: isSubmitting ? 'Дождитесь сохранения решения' : undefined }))} />
            </div> },
          ]} />
        </div>}
      </>}
      {successMessage && <Notice tone="info"><span role="status">{successMessage}</span></Notice>}
      {isSubmitting && <p role="status">Дождитесь сохранения решения.</p>}
      {selected && <form className={reviewStyles.form} onSubmit={handleSubmit}>
        <h2 ref={headingRef} tabIndex={-1}>Решение по заявке</h2>
        <p>{participantName(selected)} · {eventLabel(selected)}</p>
        <fieldset className="fl-fieldset" disabled={isSubmitting} title={isSubmitting ? 'Дождитесь сохранения решения' : undefined}>
          <legend>Решение</legend>

          <div className={reviewStyles.decisions}>
            {DECISIONS.map((option) => (
              <label
                className={reviewStyles.option}
                key={option.value}
              >
                <input
                  checked={decision === option.value}
                  name="registration-review-decision"
                  onChange={() => setDecision(option.value)}
                  type="radio"
                  value={option.value}
                />

                <span>
                  <strong>{option.label}</strong>
                  <small>{option.hint}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="fl-field">
          <span>Комментарий для участника</span>
          <textarea
            disabled={isSubmitting}
            title={isSubmitting ? 'Дождитесь сохранения решения' : undefined}
            maxLength={2000}
            onChange={(event) => setReviewNote(event.target.value)}
            placeholder="Например: Заявка подтверждена. Ожидайте информацию об оплате."
            required
            rows={5}
            value={reviewNote}
          />
        </label>

        {errorMessage ? (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className={reviewStyles.actions}>
          <Button type="submit" variant="primary" loading={isSubmitting} loadingLabel="Сохраняем…" title={isSubmitting ? 'Дождитесь сохранения решения' : undefined}>Сохранить решение</Button>
          <Button disabled={isSubmitting} title={isSubmitting ? 'Дождитесь сохранения решения' : undefined} onClick={() => setSelected(null)}>Отменить выбор</Button>
        </div>
      </form>}
    </section>
  )
}
// Organization moderation uses its own API; athlete moderation above remains available.
export function OrganizationRegistrationReviewPage() {
  const { session } = useAuth()
  const token = session?.accessToken ?? ''
  const [requests, setRequests] = useState<OrganizationRegistrationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [selected, setSelected] = useState<{ id: string; name: string; approve: boolean } | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    getOrganizationRequests(token).then(value => { if (active) setRequests(value) })
      .catch(err => { if (active) setError(adminError(err)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, reload])
  async function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected || busy) return
    if (!selected.approve && !reason.trim()) { setFormError('Укажите причину отклонения.'); return }
    setBusy(true); setFormError('')
    try {
      if (selected.approve) await approveOrganizationRequest(token, selected.id)
      else await rejectOrganizationRequest(token, selected.id, reason.trim())
      setSelected(null); setReload(value => value + 1)
    } catch (err) { setFormError(adminError(err)) } finally { setBusy(false) }
  }
  return <section className={reviewStyles.page}><p className="fl-eyebrow">Панель администратора</p><PageHeader title="Заявки организаций" meta="Проверьте сведения об организации перед одобрением." />
    {loading && <LoadingState message="Загружаем заявки…" />}{error && <ErrorState message={error} onRetry={() => setReload(value => value + 1)} />}
      {!loading && !error && !requests.length && <EmptyState title="Заявок организаций на рассмотрении нет" action={<Button onClick={() => setReload(value => value + 1)}>Обновить список</Button>}><p>Новые обращения появятся здесь.</p></EmptyState>}
      {requests.length > 0 && <div className={reviewStyles.queue}>
        {requests.map(request => <article key={request.id} className={reviewStyles.card}>
          <h2>{request.organizationName}</h2><p>{request.legalName || request.organizationType} · ИНН: {request.inn || '—'}</p>
          <p>{request.contactEmail} {request.contactPhone}</p><p>Подана: {new Date(request.createdAt).toLocaleDateString('ru-RU')}</p>
          <div className={reviewStyles.actions}>{[true, false].map(approve => <button className="fl-btn fl-btn-secondary" key={String(approve)} onClick={() => {
            setSelected({ id: request.id, name: request.organizationName, approve }); setReason(''); setFormError('')
          }}>{approve ? 'Одобрить' : 'Отклонить'}</button>)}</div>
        </article>)}
      </div>}
    {selected && <AdminDialog title={selected.approve ? 'Одобрить заявку' : 'Отклонить заявку'} busy={busy} onClose={() => setSelected(null)}>
      <form className={adminStyles.form} onSubmit={decide}><p>{selected.name}</p>
        {selected.approve ? <p>Создать организацию и назначить заявителя владельцем?</p> : <label>Причина отклонения<textarea required maxLength={2000} disabled={busy} value={reason} onChange={event => setReason(event.target.value)} /></label>}
        {formError && <p className={adminStyles.error} role="alert">{formError}</p>}
        <button title={busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-secondary" type="submit" disabled={busy}>{busy ? 'Сохраняем…' : 'Подтвердить'}</button>
      </form>
    </AdminDialog>}
  </section>
}
