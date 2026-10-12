import { apiFetch } from './http'

export type RegulationRules = {
  title?: string
  participation?: {
    minAge?: number | null
    maxAge?: number | null
    requirements?: string[]
  }
  disciplines?: Array<{
    name: string
    description?: string
  }>
  scoring?: {
    description?: string
  }
  additionalConditions?: string[]
}

export type RegulationTemplateResponse = {
  id: string
  sportId: string
  code: string
  name: string
  description: string | null
  isActive: boolean
  createdAt: string
}

export type RegulationVersionResponse = {
  id: string
  regulationTemplateId: string
  versionNo: number
  status: string
  effectiveFrom: string | null
  rulesJson: RegulationRules
  notes: string | null
  createdBy: string | null
  createdAt: string
}

export type CreateRegulationTemplateRequest = {
  sportId: string
  code: string
  name: string
  description: string | null
}

export type CreateRegulationVersionRequest = {
  versionNo: number
  rulesJson: RegulationRules
  notes: string | null
  effectiveFrom: string | null
}

export async function getRegulationTemplates(
  accessToken: string,
): Promise<RegulationTemplateResponse[]> {
  return apiFetch<RegulationTemplateResponse[]>(
    '/api/v1/regulation-templates',
    {
      accessToken,
    },
  )
}

export async function getRegulationVersions(
  accessToken: string,
): Promise<RegulationVersionResponse[]> {
  return apiFetch<RegulationVersionResponse[]>(
    '/api/v1/regulation-versions',
    {
      accessToken,
    },
  )
}

export async function createRegulationTemplate(
  payload: CreateRegulationTemplateRequest,
  accessToken: string,
): Promise<RegulationTemplateResponse> {
  return apiFetch<RegulationTemplateResponse>(
    '/api/v1/regulation-templates',
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify(payload),
    },
  )
}

export async function createRegulationVersion(
  templateId: string,
  payload: CreateRegulationVersionRequest,
  accessToken: string,
): Promise<RegulationVersionResponse> {
  return apiFetch<RegulationVersionResponse>(
    `/api/v1/regulation-templates/${encodeURIComponent(templateId)}/versions`,
    {
      method: 'POST',
      accessToken,
      body: JSON.stringify(payload),
    },
  )
}

export async function publishRegulationVersion(
  versionId: string,
  accessToken: string,
): Promise<RegulationVersionResponse> {
  return apiFetch<RegulationVersionResponse>(
    `/api/v1/regulation-versions/${encodeURIComponent(versionId)}/publish`,
    {
      method: 'POST',
      accessToken,
    },
  )
}