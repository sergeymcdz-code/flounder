import { formatDateTime } from '../../utils/formatDateTime'
import { statusLabel, statusLabelsFor } from '../../components/ui/status-dictionary'
import { useOutletContext } from 'react-router'
import type {
  OrganizerEventDetails,
  OrganizerRegistration,
  Session,
  Heat,
  GenerationResult,
  GenerationSummary,
  EventGenerationResult,
} from '../../api/organizer.types'

export type RefreshTarget =
  'event' | 'registrations' | 'none' | { heatId: string }

export type EventContext = {
  event: OrganizerEventDetails
  registrations: OrganizerRegistration[]
  sessions: Session[]
  heats: Heat[]
  accessToken: string
  clearError: () => void
  error: string
  busy: boolean
  generationResults: Record<string, GenerationResult>
  lastGeneration: GenerationSummary | null
  saveGeneration: (
    results: { eventDisciplineId: string; result: GenerationResult }[],
    durationMs: number,
    violations?: GenerationSummary['violations'],
  ) => void
  registerDialog: () => () => void
  refresh: () => Promise<void>
  run: (
    action: () => Promise<unknown>,
    target?: RefreshTarget,
  ) => Promise<boolean>
}
export const useOrganizerEvent = () => useOutletContext<EventContext>()
export const formatDate = formatDateTime
export function formatSeed(value: number | null | undefined) {
  if (value == null) return '—'
  return `${Math.floor(value / 60000)
    .toString()
    .padStart(2, '0')}:${((value % 60000) / 1000).toFixed(2).padStart(5, '0')}`
}
// Compatibility alias for consumers with mixed entities. New UI uses an explicit kind.
export const statusLabels: Record<string, string> = {
  ...statusLabelsFor('heat'), ...statusLabelsFor('registration'), ...statusLabelsFor('payment'), ...statusLabelsFor('event'),
  REGISTRATIONOPEN: statusLabel('event', 'REGISTRATION_OPEN'), REGISTRATIONCLOSED: statusLabel('event', 'REGISTRATION_CLOSED'), IMPORT: 'Импорт', FORM: 'Форма',
}

export const isHeatPublished = (status: string) =>
  ['PUBLISHED', 'IN_PROGRESS', 'FINISHED', 'COMPLETED'].includes(status)

export function normalizeGeneration(result: EventGenerationResult) {
  return result.disciplines.map((d) => ({
    eventDisciplineId: d.eventDisciplineId,
    result: 'result' in d ? d.result : d,
  }))
}
export function sessionSummary(
  session: Session,
  disciplines: OrganizerEventDetails['disciplines'],
  heats: Heat[],
) {
  const sessionHeats = heats.filter((h) =>
    disciplines.some(
      (d) => d.id === h.eventDisciplineId && d.sessionId === session.id,
    ),
  )
  const ends = sessionHeats
    .flatMap((h) => {
      if (!h.scheduledAt) return []
      const duration =
        disciplines.find((d) => d.id === h.eventDisciplineId)
          ?.heatDurationSec ?? 180
      return [new Date(h.scheduledAt).getTime() + duration * 1000]
    })
    .filter(Number.isFinite)
  return {
    heatsCount: session.heatsCount ?? sessionHeats.length,
    estimatedEndsAt:
      session.estimatedEndsAt ??
      (ends.length ? new Date(Math.max(...ends)).toISOString() : null),
  }
}
