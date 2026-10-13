import { useCallback } from 'react'
import { publicApi } from '../../api/public.api'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import { HeatProtocol, PrintButton, PrintHeader } from './Protocol'
import styles from './Public.module.css'

export function StartListPage() {
  const event = usePublicEvent()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.startList(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load)
  return (
    <>
      <PrintHeader event={event} title="Стартовый протокол" />
      <div className={styles.titleRow}>
        <h1>Стартовый протокол</h1>
        <PrintButton ready={!!state.data} />
      </div>
      <DataState {...state} />
      {state.data &&
        (state.data.sessions.length ? (
          state.data.sessions.map((session) => (
            <section key={session.id}>
              <h2>{session.name}</h2>
              {session.disciplines.map((discipline) => (
                <div key={discipline.id}>
                  <h3>
                    Вид № {discipline.number}. {discipline.name}
                  </h3>
                  {discipline.heats.map((heat) => (
                    <HeatProtocol
                      key={heat.id}
                      heat={heat}
                      lanes={event.venue?.lanes ?? 8}
                      slug={event.publicSlug}
                      timeZone={event.venue?.timezone}
                    />
                  ))}
                </div>
              ))}
            </section>
          ))
        ) : (
          <p className="fl-empty">Стартовый протокол ещё не опубликован.</p>
        ))}
    </>
  )
}
