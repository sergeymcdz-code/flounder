import { apiFetch } from './http'

export type OrganizationResponse = {
  id: string
  type: string
  name: string
  city?: string | null
  owner?: string | null
  memberCount?: number
  legalName: string | null
  inn: string | null
  contactEmail: string | null
  contactPhone: string | null
  createdAt: string
  updatedAt: string
}

export type OrganizationRegistrationStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | string

export type OrganizationRegistrationResponse = {
  id: string
  applicantUserId: string
  organizationType: string
  organizationName: string
  legalName: string | null
  inn: string | null
  contactEmail: string | null
  contactPhone: string | null
  status: OrganizationRegistrationStatus
  rejectionReason: string | null
  reviewedByUserId: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export type CreateOrganizationRegistrationRequest = {
  organizationType: string
  organizationName: string
  legalName?: string | null
  inn?: string | null
  contactEmail?: string | null
  contactPhone?: string | null
}

export async function getOrganizations(
  accessToken: string,
): Promise<OrganizationResponse[]> {
  return apiFetch<OrganizationResponse[]>('/api/v1/organizations', {
    accessToken,
  })
}

export async function createOrganizationRegistrationRequest(
  request: CreateOrganizationRegistrationRequest,
  accessToken: string,
): Promise<OrganizationRegistrationResponse> {
  return apiFetch<OrganizationRegistrationResponse>(
    '/api/v1/organization-registration-requests',
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

export async function getMyPendingOrganizationRegistration(
  accessToken: string,
): Promise<OrganizationRegistrationResponse> {
  return apiFetch<OrganizationRegistrationResponse>(
    '/api/v1/organization-registration-requests/me/pending',
    {
      accessToken,
    },
  )
}
export type OrganizationMember = {
  id: string; organizationId: string; userId: string; email: string
  firstName: string | null; lastName: string | null; memberRole: string; createdAt: string
}
export type CreateOrganization = {
  type: string; name: string; city?: string; legalName?: string; inn?: string; contactEmail?: string; contactPhone?: string
}
export const getOrganization = (token: string, id: string) =>
  apiFetch<OrganizationResponse>(`/api/v1/organizations/${id}`, { accessToken: token })
export const createOrganization = (token: string, body: CreateOrganization) =>
  apiFetch<OrganizationResponse>('/api/v1/organizations', { accessToken: token, method: 'POST', body: JSON.stringify(body) })
export const getMembers = (token: string, id: string) =>
  apiFetch<OrganizationMember[]>(`/api/v1/organizations/${id}/members`, { accessToken: token })
export const addMember = (token: string, id: string, email: string, memberRole: string) =>
  apiFetch<OrganizationMember>(`/api/v1/organizations/${id}/members`, { accessToken: token, method: 'POST', body: JSON.stringify({ email, memberRole }) })
export const updateMember = (token: string, id: string, userId: string, memberRole: string) =>
  apiFetch<OrganizationMember>(`/api/v1/organizations/${id}/members/${userId}`, { accessToken: token, method: 'PATCH', body: JSON.stringify({ memberRole }) })
export const deleteMember = (token: string, id: string, userId: string) =>
  apiFetch(`/api/v1/organizations/${id}/members/${userId}`, { accessToken: token, method: 'DELETE' })
export const getOrganizationRequests = (token: string) =>
  apiFetch<OrganizationRegistrationResponse[]>('/api/v1/organization-registration-requests?status=PENDING', { accessToken: token })
export const approveOrganizationRequest = (token: string, id: string) =>
  apiFetch(`/api/v1/organization-registration-requests/${id}/approve`, { accessToken: token, method: 'POST', body: '{}' })
export const rejectOrganizationRequest = (token: string, id: string, comment: string) =>
  apiFetch(`/api/v1/organization-registration-requests/${id}/reject`, { accessToken: token, method: 'POST', body: JSON.stringify({ comment }) })
