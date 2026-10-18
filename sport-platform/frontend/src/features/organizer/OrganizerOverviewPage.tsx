import { KeyValue } from '../../components/ui/KeyValue'
import { useEffect, useState } from 'react'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { useAuth } from '../../app/providers/AuthProvider'
import { Link, useSearchParams } from 'react-router'
import { EventChecklist } from './EventChecklist'
import { QRCodeSVG } from 'qrcode.react'
import {
  publishEvent,
  openEventRegistration,
  closeEventRegistration,
  completeEvent,
  archiveEvent,
  patchEvent,
} from '../../api/events.api'
import {
  formatDate,
  isHeatPublished,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerOverviewPage() {
  const context = useOrganizerEvent()
  const { event, registrations, heats, accessToken, busy, run, error, clearError } = context
  const toast = useToast()
  const { isOrganizer, isPlatformAdmin } = useAuth()
  const [params, setParams] = useSearchParams()
  const [confirmation, setConfirmation] = useState<{ label: string; title: string; description: string; action: () => Promise<unknown> } | null>(null)
  useEffect(() => {
    if (params.get('archive') !== '1') return
    const next = new URLSearchParams(params)
    next.delete('archive')
    setParams(next, { replace: true })
    if (event.status === 'COMPLETED' && (isOrganizer || isPlatformAdmin)) {
      clearError()
      setConfirmation({ label: 'В архив', title: 'Отправить соревнование в архив?', description: 'Соревнование будет доступно только для просмотра.', action: () => archiveEvent(event.id, accessToken) })
    }
  }, [params, setParams, event.id, event.status, accessToken, isOrganizer, isPlatformAdmin, clearError])
  const [limit, setLimit] = useState<string>(String(event.maxParticipants ?? ''))
  const [closeAt, setCloseAt] = useState('')
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')
  const url = `${window.location.origin}/e/${encodeURIComponent(event.publicSlug)}`
  const transitions = [
    { states: ['COMPLETED'], label: 'В архив', action: archiveEvent },
    { states: ['REGISTRATION_CLOSED', 'REGISTRATIONCLOSED'], label: 'Продлить', action: openEventRegistration },
    { states: ['DRAFT'], label: 'Опубликовать', action: publishEvent },
    {
      states: ['PUBLISHED'],
      label: 'Открыть регистрацию',
      action: openEventRegistration,
    },
    {
      states: ['REGISTRATIONOPEN', 'REGISTRATION_OPEN'],
      label: 'Закрыть регистрацию',
      action: closeEventRegistration,
    },
    {
      states: ['REGISTRATIONCLOSED', 'REGISTRATION_CLOSED', 'LIVE'],
      label: 'Завершить соревнование',
      action: completeEvent,
    },
  ]
  function requestTransition(t: typeof transitions[number]) {
    const descriptions: Record<string, [string, string]> = {
      'Опубликовать': ['Опубликовать соревнование?', 'Страница появится в каталоге /. Регистрация пока закрыта — откроете отдельно.'],
      'Открыть регистрацию': ['Открыть регистрацию?', `Участники смогут подавать заявки на /e/${event.publicSlug}/register до ${formatDate(event.registrationCloseAt, event.venue?.timezone)}.`],
      'Закрыть регистрацию': ['Закрыть регистрацию?', 'Новые заявки приниматься не будут. Регистрацию можно продлить.'],
      'Продлить': ['Продлить регистрацию?', `Участники снова смогут подавать заявки на /e/${event.publicSlug}/register до новой даты.`],
      'Завершить соревнование': ['Завершить соревнование?', 'Ввод результатов закроется, страница перейдёт в «Завершено».'],
      'В архив': ['Отправить соревнование в архив?', 'Соревнование будет доступно только для просмотра.'],
    }
    const [title, description] = descriptions[t.label]
    clearError()
    setConfirmation({ label: t.label, title, description, action: async () => {
      if (t.label === 'Продлить') {
        if (!closeAt) throw new Error('Укажите новую дату закрытия регистрации.')
        const next = new Date(closeAt)
        if (next.getTime() <= Date.now()) throw new Error('Новая дата закрытия должна быть в будущем.')
        await patchEvent(event.id, { registrationCloseAt: next.toISOString() }, accessToken)
      }
      return t.action(event.id, accessToken)
    } })
  }
  return (
    <div className={styles.stack}>
      {confirmation && <ConfirmDialog title={confirmation.title} description={confirmation.description} confirmLabel={confirmation.label} busy={busy} error={error}
        tone={confirmation.label === 'В архив' ? 'danger' : 'primary'}
        onClose={() => setConfirmation(null)} onConfirm={() => { void run(confirmation.action).then(ok => { if (ok) { setConfirmation(null); toast({ text: confirmation.label === 'Опубликовать' ? 'Опубликовано' : 'Статус соревнования изменён', href: `/e/${event.publicSlug}` }) } }) }} />}
      <EventChecklist context={context} onTransition={step => { const t = transitions.find(t => t.states.includes(event.status) && (step === 6 ? t.action === completeEvent : t.action === publishEvent || t.action === openEventRegistration)); if (t) requestTransition(t) }} />

      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Регистрация</h2>
          <StatusBadge kind="event" code={event.status} />
        </div>
        <dl><KeyValue label="Регистрация">{formatDate(event.registrationOpenAt, event.venue?.timezone)} — {formatDate(event.registrationCloseAt, event.venue?.timezone)}</KeyValue></dl>
        <p>
          {event.venue?.name ?? 'Площадка не указана'}
          {event.venue &&
            ` · ${event.venue.city} · ${event.venue.lanes ?? '—'} дорожек · ${event.venue.courseLength ?? '—'} м`}
        </p>
        <p className={styles.muted}>{event.description}</p>
        {event.maxParticipants != null && <p className={styles.badge}>Лимит: {new Set(registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).map(r => r.participantProfileId)).size}/{event.maxParticipants}</p>}
        {event.status !== 'ARCHIVED' && <form className={`${styles.form} fl-form`} onSubmit={(e) => { e.preventDefault(); void run(() => patchEvent(event.id, { maxParticipants: limit ? Number(limit) : null }, accessToken)) }}>
          <label className="fl-field">Лимит участников (необязательно)
            <input type="number" min="1" step="1" value={limit} placeholder="Без лимита" onChange={e => setLimit(e.target.value)} />
          </label>
          <button className="fl-btn fl-btn-secondary" disabled={busy}>Сохранить</button>
        </form>}
        {['REGISTRATION_CLOSED', 'REGISTRATIONCLOSED'].includes(event.status) && <label className="fl-field">Новая дата закрытия регистрации
          <input type="datetime-local" value={closeAt} onChange={e => setCloseAt(e.target.value)} />
        </label>}
        <div className={styles.actions}>
          {transitions
            .filter((t) => t.states.includes(event.status) && (t.action !== archiveEvent || isOrganizer || isPlatformAdmin))
            .map((t) => (
              <button
                className="fl-btn fl-btn-secondary"
                key={t.label}
                disabled={busy}
                onClick={() => requestTransition(t)}
              >
                {t.label}
              </button>
            ))}
        </div>
      <div className={styles.publicCard}>
        <div>
          <h2>Публичная страница</h2>
          <a
            href={url}
            target="_blank"
            rel="noreferrer"
            className={styles.link}
          >
            {url}
          </a>
          <p className={styles.muted}>Ссылка для участников и зрителей</p>
          <button
            className="fl-btn fl-btn-secondary" onClick={() => {
              void navigator.clipboard
                .writeText(url)
                .then(() => {
                  setCopied(true)
                  setCopyError('')
                })
                .catch(() =>
                  setCopyError(
                    'Не удалось скопировать. Выделите ссылку и скопируйте вручную.',
                  ),
                )
            }}
          >
            Копировать
          </button>
          <p role="status">{copied ? 'Ссылка скопирована' : copyError}</p>
        </div>
        <Link className="fl-btn fl-btn-ghost" to={`/e/${event.publicSlug}/poster`}>QR-плакат A4</Link><QRCodeSVG value={url} size={140} title="QR-код публичной страницы" />
      </div>
      </article>
      <article className={styles.card}><h2>Сводка</h2><div className={styles.metrics}>
        {[
          ['Заявок всего', registrations.length, '/entries'],
          [
            'Подтверждено',
            registrations.filter((r) =>
              ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status),
            ).length, '/entries?status=CONFIRMED',
          ],
          [
            'Оплачено',
            registrations.filter((r) => r.paymentStatus === 'PAID').length, '/entries?payment=PAID',
          ],
          ['Заплывов сформировано', heats.length, '/heats'],
          [
            'Заплывов опубликовано',
            heats.filter((h) => isHeatPublished(h.status)).length, '/heats',
          ],
        ].map(([label, value, path]) => (
          <div className="fl-summary-cell" key={label}>
            <p className={styles.muted}>{label}</p>
            <Link to={`/organizer/events/${event.id}${path}`} className={styles.metric}>{value}</Link>
          </div>
        ))}
      </div></article>

    </div>
  )
}
