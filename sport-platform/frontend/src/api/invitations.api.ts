import { apiFetch } from './http'
export type OrganizationInvitation = { id: string; email: string; memberRole: string; status: string; expiresAt: string }
export type Invitation = Omit<OrganizationInvitation, 'id'> & { organizationName: string; userExists: boolean }
export const memberRoleNames: Record<string, string> = { owner: 'Владелец', admin: 'Администратор', organizer: 'Организатор', operator: 'Секретарь', judge: 'Судья', member: 'Наблюдатель' }
export const invitationStatusNames: Record<string, string> = { PENDING: 'Ожидает', ACCEPTED: 'Принято', REVOKED: 'Отозвано', EXPIRED: 'Истекло' }
export const getInvitations = (token: string, id: string) => apiFetch<OrganizationInvitation[]>(`/api/v1/organizations/${id}/invitations`, { accessToken: token })
export const createInvitation = (token: string, id: string, email: string, memberRole: string) => apiFetch<OrganizationInvitation>(`/api/v1/organizations/${id}/invitations`, { accessToken: token, method: 'POST', body: JSON.stringify({ email, memberRole }) })
export const revokeInvitation = (token: string, id: string, invitationId: string) => apiFetch(`/api/v1/organizations/${id}/invitations/${invitationId}`, { accessToken: token, method: 'DELETE' })
export const getInvitation = (token: string) => apiFetch<Invitation>(`/api/v1/invitations/${encodeURIComponent(token)}`)
export const acceptInvitation = (accessToken: string, token: string) => apiFetch<{ organizationId: string; memberRole: string }>(`/api/v1/invitations/${encodeURIComponent(token)}/accept`, { accessToken, method: 'POST' })
