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
  const [grouped, setGrouped] = useState(false)
  const load = useCallback(
    (signal: AbortSignal) => publicApi.results(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load, RESULTS_POLL_MS)
  return (
    <>
      <PrintHeader event={event} title="Итоговый протокол" />
      <div className={styles.titleRow}>
        <h1>Результаты</h1>
        <PrintButton ready={!!state.data} />
        <PdfDownloadButton
          path={`/api/v1/public/events/${encodeURIComponent(event.publicSlug)}/results.pdf`}
          filename="Итоговый-протокол.pdf"
          disabled={!state.data?.disciplines.length}
        />
      </div>
      <div
        className={`${styles.segment} ${styles.noPrint}`}
        role="group"
        aria-label="Вид результатов"
      >
        <button aria-pressed={!grouped} onClick={() => setGrouped(false)}>
          Абсолют
        </button>
        <button aria-pressed={grouped} onClick={() => setGrouped(true)}>
          По группам
        </button>
      </div>
      <DataState {...state} />
      {state.data &&
        (!state.data.disciplines.length ? (
          <p>Результаты ещё не опубликованы.</p>
        ) : (
          state.data.disciplines.map((discipline) => (
            <section key={discipline.id} className={styles.results}>
              <h2>
                Вид № {discipline.number}. {discipline.name}
              </h2>
              {grouped ? (
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
