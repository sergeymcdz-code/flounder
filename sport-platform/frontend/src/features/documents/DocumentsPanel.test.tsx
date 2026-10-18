import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { DocumentsPanel } from './DocumentsPanel'
import * as api from '../../api/documents.api'
vi.mock('../../api/documents.api', async (original) => ({
  ...await original<typeof api>(), getDocuments: vi.fn(), uploadDocument: vi.fn(), deleteDocument: vi.fn(), downloadDocument: vi.fn(), reviewDocument: vi.fn(),
}))
const pending: api.ParticipantDocument = {
  id: 'document', profileId: 'child', eventId: null, type: 'MEDICAL_CERT', originalName: 'medical.pdf', contentType: 'application/pdf',
  sizeBytes: 9, issuedAt: '2026-10-13', validUntil: '2027-10-13', status: 'PENDING', rejectReason: null, uploadedAt: '2026-10-13T10:00:00Z',
}
beforeEach(() => {
  vi.mocked(api.getDocuments).mockResolvedValue([])
  vi.mocked(api.uploadDocument).mockResolvedValue(pending)
  vi.mocked(api.deleteDocument).mockResolvedValue(undefined)
})
it('uploads multipart for the child with dates and updates the visible status', async () => {
  const changed = vi.fn()
  const { container } = render(<DocumentsPanel profileId="child" accessToken="token" onChanged={changed} />)
  await screen.findByText('Документы пока не загружены.')
  expect(screen.getByLabelText('Файл документа')).not.toHaveAttribute('capture')
  fireEvent.change(screen.getByLabelText('Файл документа'), { target: { files: [new File(['%PDF-1.7'], 'medical.pdf', { type: 'application/pdf' })] } })
  fireEvent.change(screen.getByLabelText('Действует до'), { target: { value: '2027-10-13' } })
  // jsdom's FormData does not read file input's synthetic FileList; use the real multipart shape.
  const data = new FormData(); data.set('file', new File(['%PDF-1.7'], 'medical.pdf', { type: 'application/pdf' })); data.set('type', 'MEDICAL_CERT'); data.set('validUntil', '2027-10-13')
  const NativeFormData = globalThis.FormData
  vi.stubGlobal('FormData', class extends NativeFormData { constructor() { super(); for (const [key, value] of data) this.set(key, value) } })
  vi.mocked(api.getDocuments).mockResolvedValue([pending])
  fireEvent.submit(container.querySelector('form')!)
  await screen.findByText('На проверке')
  expect(screen.getByText(/medical.pdf/)).toBeInTheDocument()
  expect(api.uploadDocument).toHaveBeenCalledWith('child', expect.any(NativeFormData), 'token')
  expect(changed).toHaveBeenCalledOnce()
  vi.unstubAllGlobals()
})
it('shows reviewed status and reason; managers cannot delete participant files', async () => {
  vi.mocked(api.getDocuments).mockResolvedValue([{ ...pending, status: 'REJECTED', rejectReason: 'Нечёткое фото' }])
  render(<DocumentsPanel profileId="child" accessToken="token" eventId="event" manager />)
  expect(await screen.findByText('Нечёткое фото')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument()
  expect(api.getDocuments).toHaveBeenCalledWith('child', 'token', 'event')
})
it('deletes a pending document and reports server errors in Russian', async () => {
  vi.mocked(api.getDocuments).mockResolvedValue([pending])
  render(<DocumentsPanel profileId="child" accessToken="token" />)
  const button = await screen.findByRole('button', { name: 'Удалить' })
  vi.mocked(api.deleteDocument).mockRejectedValueOnce(new Error('Нет доступа к документам профиля'))
  fireEvent.click(button)
  expect(await screen.findByRole('alert')).toHaveTextContent('Нет доступа к документам профиля')
  vi.mocked(api.getDocuments).mockResolvedValue([])
  fireEvent.click(button)
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument())
  expect(api.deleteDocument).toHaveBeenCalledWith('child', 'document', 'token')
})
it('lets a manager reject a pending document only with a reason, then refreshes the list', async () => {
  const changed = vi.fn()
  vi.mocked(api.getDocuments).mockResolvedValue([pending])
  vi.mocked(api.reviewDocument).mockResolvedValue({ ...pending, status: 'REJECTED', rejectReason: 'Истёк срок' })
  render(<DocumentsPanel profileId="child" accessToken="token" eventId="event" manager onChanged={changed} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Отклонить…' }))
  expect(screen.getByRole('button', { name: 'Отклонить' })).toBeDisabled()
  fireEvent.change(screen.getByLabelText('Причина'), { target: { value: 'Истёк срок' } })
  vi.mocked(api.getDocuments).mockResolvedValue([{ ...pending, status: 'REJECTED', rejectReason: 'Истёк срок' }])
  fireEvent.click(screen.getByRole('button', { name: 'Отклонить' }))
  await waitFor(() => expect(changed).toHaveBeenCalledOnce())
  expect(api.reviewDocument).toHaveBeenCalledWith('child', 'document', { status: 'REJECTED', rejectReason: 'Истёк срок' }, 'token', 'event')
  expect(screen.getByText('Истёк срок')).toBeVisible()
})
