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
import './AdminPages.css'
import { RegistrationDirectory } from '../registrations/RegistrationDirectory'
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
    return 'Не удалось обработать заявку. Попробуйте ещё раз.'
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

  return (
    <section className={reviewStyles.page}>
      <p className="eyebrow">Панель администратора</p>

      <h1>Рассмотрение заявки</h1>

      <p className="page-description">
        Найдите участника, выберите заявку и оставьте понятный комментарий.
      </p>

      <div className="admin-warning">
        Не меняйте подтверждённые заявки на рабочем стенде без необходимости.
        Для тестов используйте отдельную заявку в статусе «На рассмотрении».
      </div>

      {isLoading && <p role="status">Загружаем заявки…</p>}
      {loadError && <p role="alert">{loadError} <button className="fl-btn" onClick={() => setReload(value => value + 1)}>Повторить</button></p>}
      {!isLoading && !loadError && <RegistrationDirectory registrations={registrations} selectedId={selected?.id} disabled={isSubmitting} onSelect={row => {
        setSelected(row); setReviewNote(''); setDecision('CONFIRMED'); setErrorMessage(null); setSuccessMessage(null)
      }} />}
      {successMessage && <p className="form-success" role="status">{successMessage}</p>}
      {selected && <form className={`fl-form ${reviewStyles.form}`} onSubmit={handleSubmit}>
        <h2 ref={headingRef} tabIndex={-1}>Решение по заявке</h2>
        <p>{participantName(selected)} · {eventLabel(selected)}</p>
        <fieldset className="fl-fieldset" disabled={isSubmitting}>
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

        <button title={isSubmitting ? 'Дождитесь сохранения' : undefined}
          className="fl-btn fl-btn-primary"
          disabled={isSubmitting}
          type="submit"
        >
          {isSubmitting ? 'Сохраняем…' : 'Сохранить решение'}
        </button>
        <button className="fl-btn fl-btn-secondary" type="button" disabled={isSubmitting} onClick={() => setSelected(null)}>Отменить выбор</button>
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
  return <section className={adminStyles.page}><h1>Заявки организаций</h1>
    {loading ? <p role="status">Загружаем заявки…</p> : error ? <p className={adminStyles.error} role="alert">{error} <button className="fl-btn fl-btn-secondary" onClick={() => setReload(value => value + 1)}>Повторить</button></p>
      : !requests.length ? <p className="fl-empty">Заявок организаций на рассмотрении нет.</p> : <div className={adminStyles.queue}>
        {requests.map(request => <article key={request.id} className={adminStyles.card}>
          <h2>{request.organizationName}</h2><p>{request.legalName || request.organizationType} · ИНН: {request.inn || '—'}</p>
          <p>{request.contactEmail} {request.contactPhone}</p><p>Подана: {new Date(request.createdAt).toLocaleDateString('ru-RU')}</p>
          <div className={adminStyles.actions}>{[true, false].map(approve => <button className="fl-btn fl-btn-secondary" key={String(approve)} onClick={() => {
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
