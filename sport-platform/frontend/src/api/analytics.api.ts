import { apiDownload, apiFetch } from './http'
export type Count = { key: string; count: number }
export type Breakdown = { key: string; name: string; registrations: number; participants: number }
export type EventAnalytics = {
  eventId: string; title: string; timezone: string; generatedAt: string
  registrations: { total: number; participants: number; byStatus: Count[]; byDay: { date: string; count: number }[]
    funnel: { submitted: number; confirmed: number; paid: number; admitted: number } }
  byDiscipline: { id: string; name: string; registrations: number; heats: number; participants: number }[]
  byAgeGroup: Breakdown[]; byClub: Breakdown[]; byGender: Breakdown[]
  sessions: { id: string; name: string; heats: number; estimatedDurationMin: number; estimatedEndsAt: string | null; delaySec: number | null }[]
  results: { publishedHeats: number; medianPublicationSec: number | null; p95PublicationSec: number | null; latencySamples: number; latencySource: string; corrected: number }
  validator: { runId: string | null; executedAt: string | null; errors: number; warnings: number }
  importStats: { available: boolean; reason: string; recent: { executedAt: string; rows: number; created: number; updated: number; errors: number; durationMs: number }[] }
}
export type OrganizationAnalytics = {
  organizationId: string; name: string; generatedAt: string; events: number; byStatus: Count[]
  participants: number; uniqueParticipants: number; repeatParticipants: number; averageEventSize: number
  revenue: { currency: string; amount: number }[]
}
export const getEventAnalytics = (id: string, token: string) =>
  apiFetch<EventAnalytics>(`/api/v1/events/${id}/analytics`, { accessToken: token })
export const getOrganizationAnalytics = (id: string, token: string) =>
  apiFetch<OrganizationAnalytics>(`/api/v1/organizations/${id}/analytics`, { accessToken: token })
export async function downloadAnalytics(scope: 'events' | 'organizations', id: string, token: string) {
  const blob = await apiDownload(`/api/v1/${scope}/${id}/analytics/export.xlsx`, token)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = `${scope}-${id}-analytics.xlsx`
  document.body.append(link); link.click(); link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
