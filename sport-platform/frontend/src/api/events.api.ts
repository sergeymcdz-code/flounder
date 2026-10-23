import { getVenues } from './venues.api'
import { publicApi } from './public.api'
import { apiFetch } from './http'

export type EventStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'REGISTRATIONOPEN'
  | 'REGISTRATIONCLOSED'
  | 'LIVE'
  | 'COMPLETED'
  | 'ARCHIVED'
  | string

export type EventDiscipline = {
  id: string
  code?: string | null
  name: string
  competitionFormat: 'INDIVIDUAL' | 'TEAM' | string
  unitType?: string | null
  resultType?: string | null
  rankingStrategy?: string | null
  participantLimit: number | null
  entryFeeAmount: number | null
  entryFeeCurrency: string | null
  settingsJson: string | null
}

export type Event = {
  payment?: import('./registration-payments.api').PaymentSettings
  venue?: { timezone?: string; name?: string }
  id: string
  organizationId?: string | null
  venueId?: string | null
  sportId: string
  regulationVersionId?: string | null
  maxParticipants?: number | null
  participantsCount?: number
  title: string
  description: string | null
  publicSlug: string
  status: EventStatus
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  settingsJson?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  disciplines?: EventDiscipline[]
}

export type EventResponse = Event
export type OrganizerEvent = Event

export type PublicEvent = {
  venue?: { timezone?: string; name?: string }
  id: string
  sportId: string
  maxParticipants?: number | null
  participantsCount?: number
  title: string
  description: string | null
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  status: EventStatus
  publicSlug: string
}

export type PublicEventDetails = PublicEvent & {
  disciplines: EventDiscipline[]
}

export type CreateEventRequest = {
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
  maxParticipants?: number | null
  participantsCount?: number
  title: string
  description?: string | null
  publicSlug?: string | null
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
}

export async function getPublicEvents(): Promise<PublicEvent[]> {
  const events = await apiFetch<PublicEvent[]>('/api/v1/public/events')
  return Promise.all(events.map(async event => { if (event.venue?.timezone) return event; const details = await publicApi.event(event.publicSlug).catch(() => null); return details?.venue ? { ...event, venue: details.venue } : event }))
}

export async function getPublicEventDetails(
  publicSlug: string,
): Promise<PublicEventDetails> {
  return apiFetch<PublicEventDetails>(
    `/api/v1/public/events/${encodeURIComponent(publicSlug)}`,
  )
}

export async function getMyEvents(
  accessToken: string,
  includePrivateVenues = true,
  organizationId?: string,
): Promise<OrganizerEvent[]> {
  const [events, venues] = await Promise.all([apiFetch<OrganizerEvent[]>('/api/v1/organizer/events' + (organizationId ? `?organizationId=${encodeURIComponent(organizationId)}` : ''), { accessToken }), includePrivateVenues ? getVenues(accessToken).catch(() => []) : Promise.resolve([])])
  return Promise.all(events.map(async event => {
    if (event.venue?.timezone) return event
    const venue = venues.find(venue => venue.id === event.venueId)
    if (venue) return { ...event, venue }
    const details = event.status !== 'DRAFT' && event.publicSlug ? await publicApi.event(event.publicSlug).catch(() => null) : null
    return details?.venue ? { ...event, venue: details.venue } : event
  }))
}

export async function getEvent(
  id: string,
  accessToken?: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/v1/events/${encodeURIComponent(id)}`, {
    accessToken,
  })
}

export async function getEventById(
  id: string,
  accessToken?: string,
): Promise<EventResponse> {
  return getEvent(id, accessToken)
}

export async function createEvent(
  request: CreateEventRequest,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>('/api/v1/events', {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export async function publishEvent(
  id: string,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(
    `/api/v1/events/${encodeURIComponent(id)}/publish`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export async function openEventRegistration(
  id: string,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(
    `/api/v1/events/${encodeURIComponent(id)}/open-registration`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export async function closeEventRegistration(
  id: string,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(
    `/api/v1/events/${encodeURIComponent(id)}/close-registration`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

export async function completeEvent(
  id: string,
  accessToken: string,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(
    `/api/v1/events/${encodeURIComponent(id)}/complete`,
    {
      method: 'POST',
      accessToken,
    },
  )
}
export const archiveEvent = (id: string, accessToken: string) =>
  apiFetch<EventResponse>(`/api/v1/events/${encodeURIComponent(id)}/archive`, { method: 'POST', accessToken })
export const patchEvent = (id: string, input: { maxParticipants?: number | null; registrationCloseAt?: string | null }, accessToken: string) =>
  apiFetch<EventResponse>(`/api/v1/events/${encodeURIComponent(id)}`, { method: 'PATCH', accessToken, body: JSON.stringify(input) })
