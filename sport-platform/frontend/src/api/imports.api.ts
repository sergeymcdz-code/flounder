import { apiFetch, apiDownload } from './http'
import type { ImportReport } from './organizer.types'
export const downloadImportTemplate = (id: string, accessToken: string) =>
  apiDownload(
    `/api/v1/events/${encodeURIComponent(id)}/registrations/import-template`,
    accessToken,
  )
export const importRegistrations = (
  id: string,
  file: File,
  accessToken: string,
) => {
  const body = new FormData()
  body.set('file', file)
  return apiFetch<ImportReport>(
    `/api/v1/events/${encodeURIComponent(id)}/registrations/import`,
    { accessToken, method: 'POST', body },
  )
}
