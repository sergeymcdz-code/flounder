import { useAuth } from '../../app/providers/AuthProvider'
import { Link } from 'react-router'
import { JudgingHeatList } from '../results/JudgingHeatList'
import { useState } from 'react'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { useToast } from '../../components/ui/Toast'
import { CurrentHeatButton } from '../results/CurrentHeatButton'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publishDisciplineResults } from '../../api/scheduling.api'
import {
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerResultsPage() {
  const { event, heats, sessions, accessToken, busy, run, error, clearError } = useOrganizerEvent()
  const toast = useToast()
  const { isOrganizer, isPlatformAdmin, isOperator } = useAuth()
  const canManage = isOrganizer || isPlatformAdmin || isOperator
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
        {canManage && <>
        <a
          className="fl-btn fl-btn-secondary"
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
        </>}
      </div>
      <JudgingHeatList eventId={event.id} sessions={sessions} disciplines={event.disciplines} units={heats} timeZone={event.venue?.timezone} />
      {canManage && event.disciplines.map((d, index) => {
        const units = heats.filter((h) => h.eventDisciplineId === d.id)
        return (
          <article className={styles.card} key={d.id}>
            <div className={styles.toolbar}>
              <h2>
                Вид № {d.number ?? index + 1}. {d.name}
              </h2>
              <button
                className="fl-btn fl-btn-secondary" disabled={
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
                  clearError()
                  setPublishId(d.id)
                }}
              >
                Опубликовать результаты вида
              </button>
            </div>
            {units.length === 0 && (
              <p className="fl-empty">Сначала сформируйте заплывы. <Link className="fl-btn fl-btn-ghost" to={`/organizer/events/${event.id}/heats`}>Сформировать заплывы</Link></p>
            )}
          </article>
        )
      })}
    </div>
  )
}
