import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, expect, it, vi } from 'vitest'
import { server } from '../test/setup'
import { PdfDownloadButton } from './PdfDownloadButton'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())
afterEach(() => vi.unstubAllGlobals())

it.each([undefined, 'test-organizer-token'])('downloads a PDF with appropriate authentication (%s)', async (token) => {
  let authorization: string | null | undefined
  server.use(http.get('*/api/v1/documents/sample.pdf', ({ request }) => {
    authorization = request.headers.get('Authorization')
    return new HttpResponse('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf' } })
  }))
  const create = vi.fn(() => 'blob:test-pdf')
  const revoke = vi.fn()
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = create
    static revokeObjectURL = revoke
  })
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  render(<PdfDownloadButton path="/api/v1/documents/sample.pdf" filename="Протокол.pdf" accessToken={token} />)
  fireEvent.click(screen.getByRole('button', { name: 'Скачать PDF' }))
  await waitFor(() => expect(click).toHaveBeenCalledOnce())
  expect(authorization).toBe(token ? `Bearer ${token}` : null)
  expect(create).toHaveBeenCalledOnce()
  expect(document.querySelector('a[download]')).toBeNull()
  await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:test-pdf'), { timeout: 2000 })
})

it('shows the Russian server error and permits retry without downloading it', async () => {
  server.use(http.get('*/api/v1/documents/sample.pdf', () => HttpResponse.json({
    code: 'events.access_denied', message: 'Нет доступа к управлению этим событием',
  }, { status: 403 })))
  render(<PdfDownloadButton path="/api/v1/documents/sample.pdf" filename="Протокол.pdf" />)
  fireEvent.click(screen.getByRole('button', { name: 'Скачать PDF' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Нет доступа')
  expect(screen.getByRole('button', { name: 'Скачать PDF' })).toBeEnabled()
})

it('rejects an HTML app-shell fallback instead of saving it as PDF', async () => {
  server.use(http.get('*/api/v1/documents/sample.pdf', () => new HttpResponse('<html/>', {
    headers: { 'Content-Type': 'text/html' },
  })))
  render(<PdfDownloadButton path="/api/v1/documents/sample.pdf" filename="Протокол.pdf" />)
  fireEvent.click(screen.getByRole('button', { name: 'Скачать PDF' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('неверном формате')
})
