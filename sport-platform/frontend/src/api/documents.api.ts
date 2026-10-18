import { statusLabelsFor } from '../components/ui/status-dictionary'
import { apiDownload, apiFetch } from './http'
export const documentTypes = {
  MEDICAL_CERT: 'Медицинская справка',
  INSURANCE: 'Страховка',
  PARENT_CONSENT: 'Согласие родителя',
  PDN_CONSENT: 'Согласие на обработку ПДн',
} as const
export type DocumentType = keyof typeof documentTypes
export type DocumentStatus = 'NOT_UPLOADED' | 'PENDING' | 'ACCEPTED' | 'REJECTED'
export const documentStatusLabels = statusLabelsFor('document') as Record<DocumentStatus, string>
export type ParticipantDocument = {
  id: string; profileId: string; eventId: string | null; type: DocumentType
  originalName: string; contentType: string; sizeBytes: number; issuedAt: string | null
  validUntil: string | null; status: DocumentStatus; rejectReason: string | null; uploadedAt: string
}
const scope = (eventId?: string) => eventId ? `?eventId=${encodeURIComponent(eventId)}` : ''
export const getDocuments = (profileId: string, accessToken: string, eventId?: string) =>
  apiFetch<ParticipantDocument[]>(`/api/v1/profiles/${encodeURIComponent(profileId)}/documents${scope(eventId)}`, { accessToken })
export const uploadDocument = (profileId: string, body: FormData, accessToken: string) =>
  apiFetch<ParticipantDocument>(`/api/v1/profiles/${encodeURIComponent(profileId)}/documents`, { method: 'POST', body, accessToken })
export const deleteDocument = (profileId: string, id: string, accessToken: string) =>
  apiFetch<void>(`/api/v1/profiles/${encodeURIComponent(profileId)}/documents/${encodeURIComponent(id)}`, { method: 'DELETE', accessToken })
export async function downloadDocument(id: string, accessToken: string, eventId?: string) {
  const link = await apiFetch<{ url: string; expiresAt: string }>(`/api/v1/documents/${encodeURIComponent(id)}/download${scope(eventId)}`, { accessToken })
  return apiDownload(link.url, accessToken)
}

export const reviewDocument = (profileId: string, id: string, body: { status: 'ACCEPTED' | 'REJECTED'; rejectReason?: string | null }, accessToken: string, eventId: string) =>
  apiFetch<ParticipantDocument>(`/api/v1/profiles/${encodeURIComponent(profileId)}/documents/${encodeURIComponent(id)}/review${scope(eventId)}`, { method: 'POST', accessToken, body: JSON.stringify(body) })
