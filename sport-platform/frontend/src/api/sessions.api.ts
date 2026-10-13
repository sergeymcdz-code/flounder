import { apiFetch } from './http'
import type { Session, SessionInput, AgeGroupPreset } from './organizer.types'
export const getSessions = (eventId: string, accessToken: string) =>
  apiFetch<Session[]>(
    `/api/v1/events/${encodeURIComponent(eventId)}/sessions`,
    { accessToken },
  )
export const saveSession = (
  eventId: string,
  id: string | undefined,
  input: SessionInput,
  accessToken: string,
) =>
  apiFetch<Session>(
    id
      ? `/api/v1/event-sessions/${encodeURIComponent(id)}`
      : `/api/v1/events/${encodeURIComponent(eventId)}/sessions`,
    {
      accessToken,
      method: id ? 'PATCH' : 'POST',
      body: JSON.stringify({
        name: input.name,
        orderNo: input.orderNo,
        startsAt: input.startsAt,
        breakBetweenEventsSec: input.breakBetweenEventsSec,
      }),
    },
  )
export const deleteSession = (id: string, accessToken: string) =>
  apiFetch<void>(`/api/v1/event-sessions/${encodeURIComponent(id)}`, {
    accessToken,
    method: 'DELETE',
  })
export const getAgeGroupPresets = (accessToken: string) =>
  apiFetch<AgeGroupPreset[]>('/api/v1/age-group-presets', { accessToken })
