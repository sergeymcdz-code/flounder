import { useCallback } from 'react'
import { Link } from 'react-router'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { publicApi } from '../../api/public.api'
import { time } from './format'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import { HeatProtocol, PrintButton, PrintHeader, ProtocolState } from './Protocol'
import printStyles from './Public.module.css'
import styles from './StartList.module.css'

export function StartListPage() {
  const event = usePublicEvent()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.startList(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load)
  const hasHeats = state.data?.sessions.some(session =>
    session.disciplines.some(discipline => discipline.heats.length > 0),
  ) ?? false
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  return (
    <div className={styles.page}>
      <PrintHeader event={event} title="Стартовый протокол" />
      {/* Keep the existing A4 heading separate from the screen-only PageHeader. */}
      <div className={`${printStyles.titleRow} ${styles.printOnly}`} aria-hidden="true">
        <h1>Стартовый протокол</h1>
      </div>
      <div className={`${styles.pageHeader} ${printStyles.noPrint}`}>
        <div className={styles.headerRow}>
          <PageHeader title="Стартовый протокол" />
          <ActionBar className={`fl-print-actions ${styles.printActions}`}>
            <PrintButton ready={hasHeats} />
            <span className={styles.pdf}>
              <PdfDownloadButton
                path={`/api/v1/public/events/${encodeURIComponent(event.publicSlug)}/start-list.pdf`}
                filename="Стартовый-протокол.pdf"
                disabled={!hasHeats}
              />
            </span>
          </ActionBar>
        </div>
        {!hasHeats && <small className={styles.muted}>Печать и PDF доступны после публикации заплывов.</small>}
      </div>
      <ProtocolState {...state} />
      {state.data && (hasHeats ? (
        <>
          <nav className={`${styles.anchors} ${printStyles.noPrint}`} aria-label="Виды программы"
            onScroll={event => { event.currentTarget.dataset.scrolled = String(event.currentTarget.scrollLeft > 0) }}>
            {state.data.sessions.flatMap(session => session.disciplines.map(discipline => {
              const id = `discipline-${session.id}-${discipline.id}`
              return <Button key={id} as="a" href={`#${id}`} variant="ghost" size="sm"
                aria-label={`Вид ${discipline.number} · ${discipline.name}`}
                onClick={event => {
                  event.preventDefault()
                  const heading = document.getElementById(id)
                  heading?.scrollIntoView({ block: 'start' })
                  heading?.focus({ preventScroll: true })
                }}>Вид {discipline.number}</Button>
            }))}
          </nav>
          {state.data.sessions.map(session => {
            const startsAt = event.sessions.find(item => item.id === session.id)?.startsAt
            return <section key={session.id} className={styles.session}>
              <h2 className={styles.sessionHeading}>
                {session.name}
                {startsAt && <span className={`${styles.sessionTime} ${printStyles.noPrint}`}>{time(startsAt, event.venue?.timezone)}</span>}
              </h2>
              {session.disciplines.map(discipline => (
                <div key={discipline.id} className={styles.discipline}>
                  <h3 className={styles.disciplineHeading} id={`discipline-${session.id}-${discipline.id}`} tabIndex={-1}>
                    Вид № {discipline.number}. {discipline.name}
                  </h3>
                  {discipline.heats.map(heat => (
                    <HeatProtocol key={heat.id} heat={heat} lanes={event.venue?.lanes ?? 8}
                      slug={event.publicSlug} timeZone={event.venue?.timezone} />
                  ))}
                </div>
              ))}
            </section>
          })}
        </>
      ) : (
        <EmptyState title="Стартовый протокол ещё не опубликован" action={
          <Button as={Link} to={`${base}/schedule`} variant="secondary">Открыть расписание</Button>
        }><p>Посмотрите расписание соревнования. Заплывы появятся после публикации.</p></EmptyState>
      ))}
    </div>
  )
}
