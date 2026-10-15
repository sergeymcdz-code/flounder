import { Notice } from '../../components/ui/Notice'
import { Link } from 'react-router'
import { useCallback, useState } from 'react'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publicApi } from '../../api/public.api'
import { PrintButton, PrintHeader, ResultsTable } from './Protocol'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { RESULTS_POLL_MS, usePublicData } from './usePublicData'
import styles from './Public.module.css'

export function ResultsPage() {
  const event = usePublicEvent()
  const [grouped, setGrouped] = useState<boolean | null>(null)
  const load = useCallback(
    (signal: AbortSignal) => publicApi.results(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load, RESULTS_POLL_MS)
  const showGroups = grouped ?? Boolean(state.data?.disciplines[0]?.byAgeGroup?.length)
  return (
    <>
      <PrintHeader event={event} title="Итоговый протокол" />
      <Notice tone="warning" className="fl-unofficial">Неофициальные результаты — публикуются сразу после заплыва и могут быть исправлены до утверждения судейской коллегией</Notice>
      <div className={`${styles.titleRow} fl-results-page`}>
        <h1>Результаты</h1>
        <div className="fl-actionbar fl-print-actions"><PrintButton ready={!!state.data} />
        <PdfDownloadButton
          path={`/api/v1/public/events/${encodeURIComponent(event.publicSlug)}/results.pdf`}
          filename="Итоговый-протокол.pdf"
          disabled={!state.data?.disciplines.length}
        /></div>
      </div>
      <div
        className={`${styles.segment} ${styles.noPrint}`}
        role="group"
        aria-label="Вид результатов"
      >
        <button className="fl-btn fl-btn-ghost fl-btn-sm" aria-pressed={!showGroups} onClick={() => setGrouped(false)}>
          Абсолют
        </button>
        <button className="fl-btn fl-btn-ghost fl-btn-sm" aria-pressed={showGroups} onClick={() => setGrouped(true)}>
          По группам
        </button>
      </div>
      <DataState {...state} />
      {state.data &&
        (!state.data.disciplines.length ? (
          <p className="fl-empty">Результаты ещё не опубликованы. <Link to={`/e/${event.publicSlug}/live`}>Открыть табло</Link> · <Link to={`/e/${event.publicSlug}/live#find-me`}>Найти себя</Link></p>
        ) : (
          state.data.disciplines.map((discipline) => (
            <section key={discipline.id} className={styles.results}>
              <h2>
                Вид № {discipline.number}. {discipline.name}
              </h2>
              {showGroups && discipline.byAgeGroup.length ? (
                discipline.byAgeGroup.map((group) => (
                  <div key={group.code}>
                    <h3>{group.name}</h3>
                    <ResultsTable rows={group.rows} grouped />
                  </div>
                ))
              ) : (
                <ResultsTable rows={discipline.overall} />
              )}
            </section>
          ))
        ))}
    </>
  )
}
