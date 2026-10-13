import { apiDownload } from './http'

export async function downloadProtocol(
  path: string,
  filename: string,
  accessToken?: string,
): Promise<void> {
  const blob = await apiDownload(path, accessToken)
  if (!blob.type.includes('application/pdf')) {
    throw new Error('Сервер вернул файл в неверном формате.')
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  // Allow the browser to begin the download before releasing the object URL.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
