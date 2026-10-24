import { useCallback } from 'react'
import { Link, useParams } from 'react-router'
import { publicApi } from '../../api/public.api'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { findHeat, time } from './format'
import { HeatProtocol, ProtocolState } from './Protocol'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import printStyles from './Public.module.css'
import styles from './StartList.module.css'

export function HeatPage() {
  const event = usePublicEvent()
  const { unitId = '' } = useParams()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.startList(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load)
  const found = state.data && findHeat(state.data, unitId)
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  return (
    <div className={styles.page}>
      <div className={`${styles.pageHeader} ${printStyles.noPrint}`}>
        <Button as={Link} to={`${base}/start-list`} variant="ghost" size="sm" className="fl-back-link">
          ← Стартовый протокол
        </Button>
        <PageHeader title={`Заплыв ${found?.heat.number ?? ''}`.trim()} meta={found &&
          `Вид № ${found.discipline.number} · ${found.discipline.name} · ${found.session.name} · ${time(found.heat.scheduledAt, event.venue?.timezone)}`
        } />
      </div>
      <div className={styles.printOnly} aria-hidden="true">
        <Button as={Link} variant="ghost" size="sm" className="fl-back-link" to={`${base}/start-list`}>← Стартовый протокол</Button>
        <h1>Заплыв {found?.heat.number ?? ''}</h1>
        {found && <>
          <h2>Вид № {found.discipline.number}. {found.discipline.name}</h2>
          <p>{found.session.name}</p>
        </>}
      </div>
      <ProtocolState {...state} />
      {found && <>
        <HeatProtocol heat={found.heat} lanes={event.venue?.lanes ?? 8} timeZone={event.venue?.timezone} />
        <ActionBar className={`${styles.heatLinks} ${printStyles.noPrint}`}>
          <Button as={Link} to={`${base}/live`} variant="ghost">Табло</Button>
          <Button as={Link} to={`/events/${encodeURIComponent(event.publicSlug)}/disciplines/${encodeURIComponent(found.discipline.id)}/results`} variant="ghost">Результаты вида</Button>
        </ActionBar>
      </>}
      {state.data && !found && <EmptyState title="Заплыв не найден или ещё не опубликован" action={
        <Button as={Link} to={`${base}/start-list`} variant="secondary">К протоколу</Button>
      }><p>Проверьте номер заплыва в стартовом протоколе.</p></EmptyState>}
    </div>
  )
}
