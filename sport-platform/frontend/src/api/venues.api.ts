import { apiFetch } from './http'

export type VenueResponse = {
  id: string
  name: string
  countryCode: string
  city: string
  address: string
  timezone: string
}

export async function getVenues(
  accessToken: string,
): Promise<VenueResponse[]> {
  return apiFetch<VenueResponse[]>('/api/v1/venues', {
    accessToken,
  })
}