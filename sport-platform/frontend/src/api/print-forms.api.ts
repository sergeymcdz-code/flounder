import { apiFetch } from './http'
export type DiplomaSettings = {
  enabled: boolean; title: string; bodyTemplate: string; footer: string
  showLogo: boolean; backgroundFileKey: string | null
}
export const diplomaDefaults: DiplomaSettings = {
  enabled: false, title: 'ГРАМОТА',
  bodyTemplate: 'награждается {lastName} {firstName} ({birthYear}, {club}), занявший(ая) {place} место на дистанции {discipline} с результатом {result}',
  footer: 'Главный судья ______ Главный секретарь ______', showLogo: false, backgroundFileKey: null,
}
export const placeholders = ['lastName', 'firstName', 'birthYear', 'club', 'place', 'placeText', 'discipline', 'ageGroup', 'result', 'eventTitle', 'eventDates', 'venue', 'city']
export function assignBibs(eventId: string, input: { mode: string; startFrom: number; reassign: boolean }, accessToken: string) {
  return apiFetch<{ assigned: number; kept: number; total: number }>(`/api/v1/events/${eventId}/bib-numbers/assign`, { method: 'POST', body: JSON.stringify(input), accessToken })
}
export function saveDiploma(eventId: string, diploma: DiplomaSettings, accessToken: string) {
  return apiFetch(`/api/v1/events/${eventId}`, { method: 'PATCH', body: JSON.stringify({ diploma }), accessToken })
}
export function uploadBackground(eventId: string, file: File, accessToken: string) {
  const body = new FormData(); body.append('file', file)
  return apiFetch<{ backgroundFileKey: string }>(`/api/v1/events/${eventId}/documents/diploma-background`, { method: 'POST', body, accessToken })
}
