import { useState } from 'react'
import { OrganizerConfirm } from './OrganizerDialog'
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
  statusLabels,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerOverviewPage() {
  const { event, registrations, heats, accessToken, busy, run } =
    useOrganizerEvent()
  const [confirmation, setConfirmation] = useState<{ label: string; action: () => Promise<unknown> } | null>(null)
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
  return (
    <div className={styles.stack}>
      {confirmation && <OrganizerConfirm title={confirmation.label} message="Подтвердите изменение статуса события." busy={busy}
        onClose={() => setConfirmation(null)} onConfirm={() => { void run(confirmation.action).then(ok => { if (ok) setConfirmation(null) }) }} />}

      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Информация о событии</h2>
          <span className={styles.badge}>
            {statusLabels[event.status] ?? event.status}
          </span>
        </div>
        <p>
          {formatDate(event.eventStartAt)} — {formatDate(event.eventEndAt)}
        </p>
        <p>
          {event.venue?.name ?? 'Площадка не указана'}
          {event.venue &&
            ` · ${event.venue.city} · ${event.venue.lanes ?? '—'} дорожек · ${event.venue.courseLength ?? '—'} м`}
        </p>
        <p className={styles.muted}>{event.description}</p>
        {event.maxParticipants != null && <p className={styles.badge}>Лимит: {new Set(registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).map(r => r.participantProfileId)).size}/{event.maxParticipants}</p>}
        {event.status !== 'ARCHIVED' && <form className={styles.form} onSubmit={(e) => { e.preventDefault(); void run(() => patchEvent(event.id, { maxParticipants: limit ? Number(limit) : null }, accessToken)) }}>
          <label className={styles.field}>Лимит участников события
            <input type="number" min="1" step="1" value={limit} placeholder="Без лимита" onChange={e => setLimit(e.target.value)} />
          </label>
          <button disabled={busy}>Сохранить лимит</button>
        </form>}
        {['REGISTRATION_CLOSED', 'REGISTRATIONCLOSED'].includes(event.status) && <label className={styles.field}>Новая дата закрытия регистрации
          <input type="datetime-local" value={closeAt} onChange={e => setCloseAt(e.target.value)} />
        </label>}
        <div className={styles.actions}>
          {transitions
            .filter((t) => t.states.includes(event.status))
            .map((t) => (
              <button
                className={styles.primary}
                key={t.label}
                disabled={busy}
                onClick={() => {
                  setConfirmation({ label: t.label, action: async () => {
                    if (t.label === 'Продлить') {
                      if (!closeAt) throw new Error('Укажите новую дату закрытия регистрации.')
                      const next = new Date(closeAt)
                      if (next.getTime() <= Date.now()) throw new Error('Новая дата закрытия должна быть в будущем.')
                      await patchEvent(event.id, { registrationCloseAt: next.toISOString() }, accessToken)
                    }
                    return t.action(event.id, accessToken)
                  } })
                }}
              >
                {t.label}
              </button>
            ))}
        </div>
      </article>
      <div className={styles.metrics}>
        {[
          ['Заявок всего', registrations.length],
          [
            'Подтверждено',
            registrations.filter((r) =>
              ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status),
            ).length,
          ],
          [
            'Оплачено',
            registrations.filter((r) => r.paymentStatus === 'PAID').length,
          ],
          ['Заплывов сформировано', heats.length],
          [
            'Заплывов опубликовано',
            heats.filter((h) => isHeatPublished(h.status)).length,
          ],
        ].map(([label, value]) => (
          <article className={styles.card} key={label}>
            <p className={styles.muted}>{label}</p>
            <strong className={styles.metric}>{value}</strong>
          </article>
        ))}
      </div>
      <article className={`${styles.card} ${styles.publicCard}`}>
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
            onClick={() => {
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
        <QRCodeSVG value={url} size={140} title="QR-код публичной страницы" />
      </article>
    </div>
  )
}
