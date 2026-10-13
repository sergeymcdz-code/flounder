import { apiFetch } from './http'

export type Venue = {
  id: string
  name: string
  countryCode: string
  city: string
  address: string
  timezone: string
  venueMeta: Record<string, unknown>
}

export type VenueResponse = Venue

export type CreateVenueRequest = {
  name: string
  countryCode?: string
  city?: string
  address?: string
  timezone: string
  venueMeta?: Record<string, unknown>
}

export async function getVenues(
  accessToken: string,
): Promise<VenueResponse[]> {
  return apiFetch<VenueResponse[]>('/api/v1/venues', {
    accessToken,
  })
}