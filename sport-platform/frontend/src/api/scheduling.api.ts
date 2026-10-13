import { apiFetch } from './http'
import {
  getCompetitionUnitsByDiscipline,
  getCompetitionUnitDetails,
  changeCompetitionUnitPublication,
} from './results.api'
import type {
  Heat,
  GenerationResult,
  EventGenerationResult,
  Violation,
} from './organizer.types'
export async function getHeats(
  disciplineId: string,
  accessToken: string,
): Promise<Heat[]> {
  const units = await getCompetitionUnitsByDiscipline(disciplineId, accessToken)
  return Promise.all(
    units.map(async (unit) => ({
      ...unit,
      ...(await getCompetitionUnitDetails(unit.id, accessToken)),
    })),
  )
}
export function generateHeats(
  id: string,
  scope: 'events',
  force: boolean,
  accessToken: string,
): Promise<EventGenerationResult>
export function generateHeats(
  id: string,
  scope: 'event-disciplines',
  force: boolean,
  accessToken: string,
): Promise<GenerationResult>
export function generateHeats(
  id: string,
  scope: 'events' | 'event-disciplines',
  force: boolean,
  accessToken: string,
) {
  return apiFetch<GenerationResult | EventGenerationResult>(
    `/api/v1/${scope}/${encodeURIComponent(id)}/generate-competition-units`,
    {
      accessToken,
      method: 'POST',
      body: JSON.stringify({ force, circleSeedTopHeats: false }),
    },
  )
}
export const recalculateSchedule = (id: string, accessToken: string) =>
  apiFetch<{
    units: {
      id: string
      number: number
      scheduledAt: string
      sequenceInSession: number
    }[]
  }>(`/api/v1/events/${encodeURIComponent(id)}/schedule/recalculate`, {
    accessToken,
    method: 'POST',
  })
export const validateHeats = (id: string, accessToken: string) =>
  apiFetch<{ violations: Violation[] }>(
    `/api/v1/event-disciplines/${encodeURIComponent(id)}/competition-units/validate`,
    { accessToken },
  )
export const changeLane = (
  id: string,
  laneOrPosition: number,
  accessToken: string,
) =>
  apiFetch<void>(`/api/v1/competition-unit-entries/${encodeURIComponent(id)}`, {
    accessToken,
    method: 'PATCH',
    body: JSON.stringify({ laneOrPosition }),
  })
export const publishStartList = (id: string, accessToken: string) =>
  apiFetch<void>(
    `/api/v1/events/${encodeURIComponent(id)}/competition-units/publish`,
    { accessToken, method: 'POST' },
  )
export async function publishDisciplineResults(
  heats: Heat[],
  accessToken: string,
) {
  for (const heat of heats) {
    if (['FINISHED', 'COMPLETED'].includes(heat.status)) continue
    try {
      await changeCompetitionUnitPublication(heat.id, true, accessToken)
    } catch (error) {
      throw new Error(`Заплыв ${heat.sequenceNumber ?? heat.label} не опубликован. ${error instanceof Error ? error.message : 'Повторите попытку.'}`)
    }
  }
}

export type ScheduleShift = {
  units: { id: string; previousScheduledAt: string; scheduledAt: string }[]
  shiftedAt: string | null
}
export const shiftSchedule = (
  id: string,
  fromUnitId: string,
  deltaSec: number,
  accessToken: string,
  preview = false,
  signal?: AbortSignal,
) => apiFetch<ScheduleShift>(
  `/api/v1/events/${encodeURIComponent(id)}/schedule/shift${preview ? '/preview' : ''}`,
  { accessToken, method: 'POST', body: JSON.stringify({ fromUnitId, deltaSec }), signal },
)
