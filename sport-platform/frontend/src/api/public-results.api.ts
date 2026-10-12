import { apiFetch } from './http'

export type PublicResultEntry = {
  place: number | null
  participantName: string
  clubName: string | null
  rawValue: string | null
  resultType: string | null
  status: string
}

export type PublicCompetitionUnitResults = {
  id: string
  label: string
  entries: PublicResultEntry[]
}

export type PublicDisciplineResultsResponse = {
  eventId: string
  eventDisciplineId: string
  units: PublicCompetitionUnitResults[]
}

export function getPublicDisciplineResults(
  eventId: string,
  disciplineId: string,
): Promise<PublicDisciplineResultsResponse> {
  return apiFetch<PublicDisciplineResultsResponse>(
    `/api/v1/events/${encodeURIComponent(eventId)}/disciplines/${encodeURIComponent(disciplineId)}/results`,
  )
}