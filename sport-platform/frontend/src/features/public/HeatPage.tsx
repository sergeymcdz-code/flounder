import { useCallback } from 'react'
import { Link, useParams } from 'react-router'
import { publicApi } from '../../api/public.api'
import { findHeat } from './format'
import { HeatProtocol } from './Protocol'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'

export function HeatPage() {
  const event = usePublicEvent()
  const { unitId = '' } = useParams()
  const load = useCallback(
    (signal: AbortSignal) => publicApi.startList(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load)
  const found = state.data && findHeat(state.data, unitId)
  return (
    <>
      <Link to={`/e/${encodeURIComponent(event.publicSlug)}/start-list`}>
        ← Стартовый протокол
      </Link>
      <h1>Заплыв {found?.heat.number ?? ''}</h1>
      <DataState {...state} />
      {found && (
        <>
          <h2>
            Вид № {found.discipline.number}. {found.discipline.name}
          </h2>
          <p>{found.session.name}</p>
          <HeatProtocol heat={found.heat} lanes={event.venue.lanes} />
        </>
      )}
      {state.data && !found && <p>Заплыв не найден или ещё не опубликован.</p>}
    </>
  )
}
