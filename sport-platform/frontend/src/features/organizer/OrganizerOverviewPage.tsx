import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { QRCodeSVG } from 'qrcode.react'
import { KeyValue } from '../../components/ui/KeyValue'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { useToast } from '../../components/ui/Toast'
import { normalizeStatus } from '../../components/ui/status-dictionary'
import { useAuth } from '../../app/providers/AuthProvider'
import { EventChecklist, type ChecklistAction } from './EventChecklist'
import {
  publishEvent, openEventRegistration, closeEventRegistration,
  completeEvent, archiveEvent, patchEvent,
} from '../../api/events.api'
import { formatDate, isHeatPublished, useOrganizerEvent } from './organizer-event-context'
import styles from './OverviewPage.module.css'

type Confirmation = { label: string; title: string; description: string; action: () => Promise<unknown> }

// The shared ConfirmDialog has no field slot. Keep the extension form local until it does.
function ExtensionDialog({ busy, error, onClose, onConfirm }: {
  busy: boolean; error: string; onClose: () => void; onConfirm: (closeAt: string) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const [closeAt, setCloseAt] = useState('')
  const [validation, setValidation] = useState('')
  const reason = busy ? 'Дождитесь завершения текущего действия' : undefined
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const node = dialog.current
    node?.showModal()
    input.current?.focus()
    return () => { node?.close(); previous?.focus() }
  }, [])
  return <dialog ref={dialog} className={`fl-confirm ${styles.extension}`} aria-labelledby="extend-title" aria-describedby="extend-description"
    onCancel={e => { e.preventDefault(); if (!busy) onClose() }}>
    <h2 id="extend-title">Продлить регистрацию?</h2>
    <p id="extend-description">Участники снова смогут подавать заявки до новой даты.</p>
    <form className="fl-form" onSubmit={e => {
      e.preventDefault()
      if (busy) return
      if (!closeAt || !Number.isFinite(new Date(closeAt).getTime()) || new Date(closeAt).getTime() <= Date.now()) {
        setValidation('Новая дата закрытия должна быть в будущем.')
        return
      }
      setValidation('')
      onConfirm(closeAt)
    }}>
      <label className="fl-field">Новая дата закрытия регистрации
        <input ref={input} type="datetime-local" required value={closeAt} onChange={e => setCloseAt(e.target.value)} />
      </label>
      {(validation || error) && <Notice tone="danger">{validation || error}</Notice>}
      {reason && <small>{reason}</small>}
      <ActionBar><Button disabled={busy} title={reason} onClick={onClose}>Отмена</Button>
        <Button type="submit" variant="primary" loading={busy} title={reason}>Продлить</Button></ActionBar>
    </form>
  </dialog>
}

export function OrganizerOverviewPage() {
  const context = useOrganizerEvent()
  const { event, registrations, heats, accessToken, busy, run, error, clearError, registerDialog } = context
  const toast = useToast()
  const { isOrganizer, isPlatformAdmin } = useAuth()
  const canManage = isOrganizer || isPlatformAdmin
  const status = normalizeStatus(event.status)
  const [params, setParams] = useSearchParams()
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [extending, setExtending] = useState(false)
  const [limit, setLimit] = useState<string>(String(event.maxParticipants ?? ''))
  const [copyError, setCopyError] = useState('')
  const reason = busy ? 'Дождитесь завершения текущего действия' : undefined
  const url = `${window.location.origin}/e/${encodeURIComponent(event.publicSlug)}`
  const base = `/organizer/events/${event.id}`
  const participants = new Set(registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).map(r => r.participantProfileId)).size
  const pending = registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW'].includes(r.status)).length
  const unpaid = registrations.filter(r => ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status) && !['PAID', 'WAIVED', 'REFUNDED'].includes(r.paymentStatus)).length
  const unpublished = heats.filter(h => !isHeatPublished(h.status)).length
  const transitions = [
    { states: ['COMPLETED'], label: 'В архив', action: archiveEvent, step: 6 },
    { states: ['DRAFT'], label: 'Опубликовать', action: publishEvent, step: 1 },
    { states: ['PUBLISHED'], label: 'Открыть регистрацию', action: openEventRegistration, step: 1 },
    { states: ['REGISTRATION_OPEN'], label: 'Закрыть регистрацию', action: closeEventRegistration, step: 1 },
    { states: ['REGISTRATION_CLOSED', 'LIVE'], label: 'Завершить соревнование', action: completeEvent, step: 6 },
  ]
  const transition = canManage ? transitions.find(t => t.states.includes(status)) : undefined
  const action: ChecklistAction | undefined = transition && {
    label: transition.label, step: transition.step,
  }
  useEffect(() => {
    if (params.get('archive') !== '1') return
    const next = new URLSearchParams(params)
    next.delete('archive')
    setParams(next, { replace: true })
    if (status === 'COMPLETED' && canManage) {
      clearError()
      setConfirmation({ label: 'В архив', title: 'Отправить соревнование в архив?', description: 'Соревнование будет доступно только для просмотра.', action: () => archiveEvent(event.id, accessToken) })
    }
  }, [params, setParams, event.id, status, accessToken, canManage, clearError])
  useEffect(() => {
    if (confirmation || extending) return registerDialog?.()
  }, [confirmation, extending, registerDialog])

  function requestTransition() {
    if (!transition) return
    const descriptions: Record<string, [string, string]> = {
      'Опубликовать': ['Опубликовать соревнование?', 'Страница появится в каталоге. Регистрацию можно будет открыть отдельно.'],
      'Открыть регистрацию': ['Открыть регистрацию?', `Участники смогут подавать заявки до ${formatDate(event.registrationCloseAt, event.venue?.timezone)}.`],
      'Закрыть регистрацию': ['Закрыть регистрацию?', 'Новые заявки приниматься не будут. Регистрацию можно продлить.'],
      'Завершить соревнование': ['Завершить соревнование?', 'Ввод результатов закроется, соревнование перейдёт в «Завершено».'],
      'В архив': ['Отправить соревнование в архив?', 'Соревнование будет доступно только для просмотра.'],
    }
    const [title, description] = descriptions[transition.label]
    clearError()
    setConfirmation({ label: transition.label, title, description, action: () => transition.action(event.id, accessToken) })
  }
  return <div className={styles.page}>
    {confirmation && <ConfirmDialog {...confirmation} confirmLabel={confirmation.label} busy={busy} error={error}
      tone={confirmation.label === 'В архив' ? 'danger' : 'primary'}
      onClose={() => setConfirmation(null)} onConfirm={() => { void run(confirmation.action).then(ok => {
        if (ok) { setConfirmation(null); toast({ text: confirmation.label === 'Опубликовать' ? 'Опубликовано' : 'Статус соревнования изменён', href: `/e/${event.publicSlug}` }) }
      }) }} />}
    {extending && <ExtensionDialog busy={busy} error={error} onClose={() => setExtending(false)} onConfirm={closeAt => {
      void run(async () => {
        await patchEvent(event.id, { registrationCloseAt: new Date(closeAt).toISOString() }, accessToken)
        return openEventRegistration(event.id, accessToken)
      }).then(ok => { if (ok) { setExtending(false); toast({ text: 'Регистрация продлена' }) } })
    }} />}
    {error && !confirmation && !extending && <Notice tone="danger">{error} <Button onClick={() => { void run(context.refresh) }} disabled={busy} title={reason}>Повторить</Button></Notice>}
    <section className={`fl-card ${styles.attention}`} aria-label="Требует внимания">
      <h2>Требует внимания</h2>
      {pending || unpublished || unpaid ? <div className={styles.attentionList}>
        {pending > 0 && <Button as={Link} to={`${base}/entries?status=SUBMITTED`}>Заявки на проверке: {pending}</Button>}
        {unpublished > 0 && <Button as={Link} to={`${base}/heats`}>Неопубликованный стартовый протокол: {unpublished}</Button>}
        {unpaid > 0 && <Button as={Link} to={`${base}/payments`}>Неоплаченные взносы: {unpaid}</Button>}
      </div> : <p className={styles.muted}>Срочных задач нет. Следующий шаг — в чек-листе ниже.</p>}
    </section>
    <div className={styles.grid}>
      <div className={styles.checklist}>
        <EventChecklist context={context} action={action} busy={busy} onTransition={requestTransition} />
      </div>
      <section className={`fl-card ${styles.summary}`} aria-label="Сводка">
        <h2>Сводка</h2>
        <div className={styles.metrics}>{[
          ['Заявок', registrations.length, '/entries'],
          ['Подтверждено', registrations.filter(r => ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).length, '/entries?status=CONFIRMED'],
          ['Оплачено', registrations.filter(r => r.paymentStatus === 'PAID').length, '/entries?payment=PAID'],
          ['Заплывов', heats.length, '/heats'],
        ].map(([label, value, path]) => <Link key={label} aria-label={`${label} ${value}`} to={`${base}${path}`} className={`fl-summary-cell ${styles.metric}`}>
          <span>{label}</span><strong>{value}</strong>
        </Link>)}</div>
        {registrations.length === 0 && <EmptyState title="Заявок пока нет" action={<Button as={Link} variant="ghost" to={`${base}/entries`}>Открыть заявки</Button>}>
          <p>Поделитесь публичной страницей с участниками.</p>
        </EmptyState>}
      </section>
      <section className={`fl-card ${styles.registration}`} aria-label="Регистрация и площадка">
        <h2 aria-label="Регистрация">Регистрация и площадка</h2>
        <dl className={styles.details}>
          <KeyValue label="Регистрация">{formatDate(event.registrationOpenAt, event.venue?.timezone)} — {formatDate(event.registrationCloseAt, event.venue?.timezone)}</KeyValue>
          <KeyValue label="Площадка">{event.venue ? `${event.venue.name} · ${event.venue.city} · ${event.venue.lanes ?? '—'} дорожек · ${event.venue.courseLength ?? '—'} м` : 'Площадка не указана'}</KeyValue>
          <KeyValue label="Лимит"><span className="fl-numeric">{participants}/{event.maxParticipants ?? 'без лимита'}</span></KeyValue>
        </dl>
        {event.description && <p className={styles.muted}>{event.description}</p>}
        {status !== 'ARCHIVED' && (canManage ? <>
          <form className={`fl-form ${styles.limitForm}`} onSubmit={e => {
            e.preventDefault()
            void run(() => patchEvent(event.id, { maxParticipants: limit ? Number(limit) : null }, accessToken)).then(ok => { if (ok) toast({ text: 'Лимит участников сохранён' }) })
          }}>
            <label className="fl-field">Лимит участников (необязательно)
              <input type="number" min="1" step="1" value={limit} placeholder="Без лимита" onChange={e => setLimit(e.target.value)} />
            </label>
            <ActionBar className={styles.save}><Button type="submit" loading={busy} title={reason}>Сохранить</Button></ActionBar>
          </form>
          {reason && <small className={styles.muted}>{reason}</small>}
          {status === 'REGISTRATION_CLOSED' && <ActionBar><Button disabled={busy} title={reason} onClick={() => { clearError(); setExtending(true) }}>Продлить</Button></ActionBar>}
        </> : <Notice>Изменять настройки соревнования может организатор.</Notice>)}
      </section>
      <section className={`fl-card ${styles.public}`} aria-label="Публичная страница">
        <h2>Публичная страница</h2>
        <div className={styles.publicCard}>
          <QRCodeSVG value={url} size={140} title="QR-код публичной страницы" />
          <div className={styles.publicLinks}>
            <a href={url} target="_blank" rel="noreferrer" className={styles.url}>{url}</a>
            <p className={styles.muted}>Ссылка для участников и зрителей</p>
            <ActionBar className={styles.publicActions}>
              <Button onClick={() => {
                void Promise.resolve().then(() => navigator.clipboard.writeText(url)).then(() => {
                  setCopyError(''); toast({ text: 'Ссылка скопирована' })
                }).catch(() => setCopyError('Не удалось скопировать. Выделите ссылку и скопируйте вручную.'))
              }}>Копировать</Button>
              <Button as={Link} variant="ghost" to={`/e/${event.publicSlug}/poster`}>QR-плакат A4</Button>
              <Button as="a" variant="ghost" href={url} target="_blank" rel="noreferrer">Открыть ↗</Button>
            </ActionBar>
          </div>
        </div>
        {copyError && <Notice tone="danger">{copyError}</Notice>}
      </section>
    </div>
  </div>
}
