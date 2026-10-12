import { apiFetch } from './http'
import type {
  CreateRegistrationRequest,
  Registration,
  RegistrationStatus,
} from '../features/registrations/registration.types'

export type RegistrationReviewDecision =
  | 'CONFIRMED'
  | 'NEEDS_CORRECTION'
  | 'REJECTED'

export type ReviewRegistrationRequest = {
  decision: RegistrationReviewDecision
  reviewNote: string
}

export type GetRegistrationsOptions = {
  eventId?: string
  status?: RegistrationStatus
}

export function createRegistration(
  request: CreateRegistrationRequest,
  accessToken: string,
): Promise<Registration> {
  return apiFetch<Registration>('/api/v1/registrations', {
    method: 'POST',
    accessToken,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export function getMyRegistrations(
  accessToken: string,
): Promise<Registration[]> {
  return apiFetch<Registration[]>('/api/v1/registrations/me', {
    accessToken,
  })
}

export function reviewRegistration(
  registrationId: string,
  request: ReviewRegistrationRequest,
  accessToken: string,
): Promise<Registration> {
  return apiFetch<Registration>(
    `/api/v1/registrations/${encodeURIComponent(registrationId)}/review`,
    {
      method: 'POST',
      accessToken,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    },
  )
}

export function getRegistrations(
  accessToken: string,
  options: GetRegistrationsOptions = {},
): Promise<Registration[]> {
  const params = new URLSearchParams()

  if (options.eventId) {
    params.set('eventId', options.eventId)
  }

  if (options.status) {
    params.set('status', options.status)
  }

  const query = params.toString()

  return apiFetch<Registration[]>(
    `/api/v1/registrations${query ? `?${query}` : ''}`,
    {
      accessToken,
    },
  )
}

export function getRegistration(
  id: string,
  accessToken: string,
): Promise<Registration> {
  return apiFetch<Registration>(
    `/api/v1/registrations/${encodeURIComponent(id)}`,
    {
      accessToken,
    },
  )
}