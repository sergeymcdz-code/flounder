import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import {
  publishEvent,
  openEventRegistration,
  closeEventRegistration,
  completeEvent,
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
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')
  const url = `${window.location.origin}/e/${encodeURIComponent(event.publicSlug)}`
  const transitions = [
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
        <div className={styles.actions}>
          {transitions
            .filter((t) => t.states.includes(event.status))
            .map((t) => (
              <button
                className={styles.primary}
                key={t.label}
                disabled={busy}
                onClick={() => {
                  void run(() => t.action(event.id, accessToken))
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
