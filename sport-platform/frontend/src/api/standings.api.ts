import { apiFetch } from './http'

export type StandingsConfig = {
  enabled: boolean; scope: 'AGE_GROUP' | 'ABSOLUTE'; pointsByPlace: number[]
  maxScoredPerClubPerDiscipline: number | null; disciplineIds: string[] | null
  published: boolean; title: string | null
}
export type ClubMedals = { club: string; gold: number; silver: number; bronze: number }
export type Standings = {
  title: string | null; scope: 'AGE_GROUP' | 'ABSOLUTE'; medals: ClubMedals[]
  team: (ClubMedals & { points: number; scoredResults: number; breakdown?: {
    disciplineId: string; ageGroupCode: string | null; place: number; points: number; athlete: string; counted: boolean
  }[] })[]
  unitsPending: number; disciplinesIncluded: number; calculatedAt: string; preliminary: boolean
}
const path = (id: string) => `/api/v1/events/${encodeURIComponent(id)}/standings`
export const getStandingsConfig = (id: string, accessToken: string) =>
  apiFetch<StandingsConfig>(`${path(id)}/config`, { accessToken })
export const saveStandingsConfig = (id: string, config: StandingsConfig, accessToken: string) =>
  apiFetch<StandingsConfig>(`${path(id)}/config`, { method: 'PUT', body: JSON.stringify(config), accessToken })
export const getStandings = (id: string, accessToken: string) =>
  apiFetch<Standings>(`${path(id)}?details=true`, { accessToken })
