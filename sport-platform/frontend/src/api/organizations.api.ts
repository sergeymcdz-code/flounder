import { apiFetch } from './http'

export type OrganizationResponse = {
  id: string
  type: string
  name: string
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