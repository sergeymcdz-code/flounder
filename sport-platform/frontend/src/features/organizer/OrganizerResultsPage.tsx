import { useState } from 'react'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { CurrentHeatButton } from '../results/CurrentHeatButton'
import { Link } from 'react-router'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publishDisciplineResults } from '../../api/scheduling.api'
import {
  formatDate,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerResultsPage() {
  const { event, heats, accessToken, busy, run, error } = useOrganizerEvent()
  const toast = useToast()
  const [publishId, setPublishId] = useState<string | null>(null)
  const publishDiscipline = event.disciplines.find(d => d.id === publishId)
  const hasResults = (id: string) =>
    heats
      .find((h) => h.id === id)
      ?.entries.some(
        (e) =>
          e.rawValue != null ||
          (e.resultStatus != null && e.resultStatus !== 'PENDING'),
      ) ?? false
  return (
    <div className={styles.stack}>
      <CurrentHeatButton eventId={event.id} slug={event.publicSlug} />
      {publishDiscipline && <ConfirmDialog title={`Опубликовать результаты «${publishDiscipline.name}»?`} description={`Места и времена появятся на /e/${event.publicSlug}/results как неофициальные.`} confirmLabel="Опубликовать" busy={busy} error={error} onClose={() => setPublishId(null)} onConfirm={() => { void run(() => publishDisciplineResults(heats.filter(h => h.eventDisciplineId === publishId), accessToken)).then(ok => { if (ok) { setPublishId(null); toast({ text: 'Опубликовано', href: `/e/${event.publicSlug}/results` }) } }) }} />}
      <div className={styles.toolbar}>
        <h2>Результаты соревнования</h2>
        <a
          className={styles.button}
          href={`/e/${encodeURIComponent(event.publicSlug)}/results?print=1`}
          target="_blank"
          rel="noreferrer"
        >
          Печать итогового протокола
        </a>
        <PdfDownloadButton
          path={`/api/v1/events/${event.id}/documents/results.pdf`}
          filename="Итоговый-протокол.pdf"
          accessToken={accessToken}
          disabled={busy || !heats.some((heat) => hasResults(heat.id))}
        />
      </div>
      {event.disciplines.map((d, index) => {
        const units = heats.filter((h) => h.eventDisciplineId === d.id)
        return (
          <article className={styles.card} key={d.id}>
            <div className={styles.toolbar}>
              <h2>
                Вид № {d.number ?? index + 1}. {d.name}
              </h2>
              <button
                disabled={
                  busy ||
                  !units.some(
                    (h) =>
                      ['PUBLISHED', 'IN_PROGRESS'].includes(h.status) &&
                      h.entries.length > 0 &&
                      h.entries.every(
                        (e) =>
                          e.rawValue != null ||
                          (e.resultStatus != null &&
                            e.resultStatus !== 'PENDING'),
                      ),
                  )
                }
                onClick={() => {
                  setPublishId(d.id)
                }}
              >
                Опубликовать результаты вида
              </button>
            </div>
            <div className={styles.table}>
              <table>
                <thead>
                  <tr>
                    <th>Заплыв</th>
                    <th>Время</th>
                    <th>Статус</th>
                    <th>Результаты</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {units.map((h) => (
                    <tr key={h.id}>
                      <td>{h.sequenceNumber ?? h.label}</td>
                      <td>{formatDate(h.scheduledAt, event.venue?.timezone)}</td>
                      <td>
                        <StatusBadge kind="heat" code={h.status} />
                      </td>
                      <td>
                        {['FINISHED', 'COMPLETED'].includes(h.status)
                          ? 'Опубликованы'
                          : hasResults(h.id) ? 'Внесены' : 'Нет'}
                      </td>
                      <td>
                        <Link
                          className={styles.link}
                          to={`/operator/competition-units/${h.id}?eventId=${event.id}`}
                        >
                          Ввести
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {units.length === 0 && (
              <p className={styles.muted}>Сначала сформируйте заплывы.</p>
            )}
          </article>
        )
      })}
    </div>
  )
}
