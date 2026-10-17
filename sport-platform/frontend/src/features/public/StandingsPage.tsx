import { useCallback } from 'react'
import { useParams } from 'react-router'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publicApi } from '../../api/public.api'
import { usePublicData } from './usePublicData'
import { DataState } from './PublicLayout'
import { PrintButton, PrintHeader } from './Protocol'
import { usePublicEvent } from './publicContext'
import { StandingsTables } from '../standings/StandingsTables'
import styles from '../standings/Standings.module.css'

export function StandingsPage() {
  const { slug = '' } = useParams()
  const event = usePublicEvent()
  const load = useCallback((signal: AbortSignal) => publicApi.standings(slug, signal), [slug])
  const state = usePublicData(load, 30_000)
  if (state.errorStatus === 404) return <h1>Зачёт не опубликован</h1>
  return <div className={styles.page}>
    <PrintHeader event={event} title={state.data?.title || 'Командный и медальный зачёт'} />
    <h1>{state.data?.title || 'Командный и медальный зачёт'}</h1>
    <DataState {...state} />
    {state.data && <>
      <div className={styles.actions}>
        <PdfDownloadButton
          path={`/api/v1/public/events/${encodeURIComponent(slug)}/standings.pdf`}
          filename="Зачёт.pdf"
        />
        <PrintButton ready />
      </div>
      <StandingsTables data={state.data} />
    </>}
  </div>
}
