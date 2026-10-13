import { getVenues } from './venues.api'
import { apiFetch } from './http'
import type {
  OrganizerEventDetails,
  OrganizerRegistration,
  DisciplineInput,
  SwimDiscipline,
  DisciplineTemplate,
} from './organizer.types'
export async function getOrganizerEvent(
  id: string,
  accessToken: string,
): Promise<OrganizerEventDetails> {
  const [event, disciplines, venues] = await Promise.all([
    apiFetch<OrganizerEventDetails>(
      `/api/v1/events/${encodeURIComponent(id)}`,
      { accessToken },
    ),
    apiFetch<SwimDiscipline[]>(
      `/api/v1/events/${encodeURIComponent(id)}/disciplines`,
      { accessToken },
    ),
    getVenues(accessToken),
  ])
  const venue = venues.find((v) => v.id === event.venueId)
  return {
    ...event,
    disciplines,
    venue:
      event.venue ??
      (venue
        ? {
            name: venue.name,
            city: venue.city,
            lanes: venue.lanes ?? null,
            courseLength: venue.courseLength ?? null,
          }
        : undefined),
  }
}
export const getOrganizerRegistrations = (
  eventId: string,
  accessToken: string,
) =>
  apiFetch<OrganizerRegistration[]>(
    `/api/v1/registrations?eventId=${encodeURIComponent(eventId)}`,
    { accessToken },
  )
export const saveDiscipline = (
  eventId: string,
  id: string | undefined,
  input: DisciplineInput,
  accessToken: string,
) =>
  apiFetch<SwimDiscipline>(
    id
      ? `/api/v1/event-disciplines/${encodeURIComponent(id)}`
      : `/api/v1/events/${encodeURIComponent(eventId)}/disciplines`,
    {
      accessToken,
      method: id ? 'PATCH' : 'POST',
      body: JSON.stringify({
        code: input.code?.trim() || undefined,
        name: input.name?.trim() || undefined,
        distanceM: input.distanceM,
        stroke: input.stroke,
        gender: input.gender,
        seedingMode: input.seedingMode,
        sessionId: input.sessionId,
        orderInSession: input.orderInSession,
        heatDurationSec: input.heatDurationSec,
        ageGroups: input.ageGroups.map((g) => ({
          code: g.code,
          name: g.name,
          gender: g.gender,
          birthYearFrom: g.birthYearFrom,
          birthYearTo: g.birthYearTo,
        })),
        disciplineTemplateId: input.disciplineTemplateId,
      }),
    },
  )
export const deleteDiscipline = (id: string, accessToken: string) =>
  apiFetch<void>(`/api/v1/event-disciplines/${encodeURIComponent(id)}`, {
    accessToken,
    method: 'DELETE',
  })
export const publishDiscipline = (
  eventId: string,
  id: string,
  accessToken: string,
) =>
  apiFetch<SwimDiscipline>(
    `/api/v1/events/${encodeURIComponent(eventId)}/disciplines/${encodeURIComponent(id)}/publish`,
    { accessToken, method: 'POST' },
  )
export const registrationAction = (
  id: string,
  action: 'withdraw' | 'mark-paid',
  accessToken: string,
) =>
  apiFetch<OrganizerRegistration>(
    `/api/v1/registrations/${encodeURIComponent(id)}/${action}`,
    { accessToken, method: 'POST' },
  )
export const updateSeedTime = (
  id: string,
  seedTimeMs: number | null,
  accessToken: string,
) =>
  apiFetch<OrganizerRegistration>(
    `/api/v1/registrations/${encodeURIComponent(id)}`,
    { accessToken, method: 'PATCH', body: JSON.stringify({ seedTimeMs }) },
  )

export const getDisciplineTemplates = (accessToken: string) =>
  apiFetch<DisciplineTemplate[]>('/api/v1/discipline-templates', {
    accessToken,
  })
