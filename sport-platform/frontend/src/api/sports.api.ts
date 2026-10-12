import { apiFetch } from './http'

export type SportResponse = {
  id: string
  code: string
  name: string
  isActive: boolean
}

export type Sport = SportResponse

export async function getSports(accessToken: string): Promise<SportResponse[]> {
  return apiFetch<SportResponse[]>('/api/v1/sports', { accessToken })
}