import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { OrganizerDiplomaPage } from './OrganizerDiplomaPage'
import { AwardsPrintControls } from './PrintFormsControls'
import { saveDiploma, uploadBackground } from '../../api/print-forms.api'
import { downloadProtocol } from '../../api/protocols.api'

const context = {
  event: { id: 'cup', status: 'COMPLETED', settingsJson: '{"diploma":{"enabled":true,"title":"Кубок"}}',
    disciplines: [{ id: 'f50', name: '50 м', ageGroups: [{ code: 'young', name: '2014–2015' }] }] },
  accessToken: 'token', busy: false,
  run: async (action: () => Promise<unknown>) => { await action(); return true },
}
vi.mock('./organizer-event-context', () => ({ useOrganizerEvent: () => context }))
vi.mock('../../api/print-forms.api', async original => ({
  ...await original<typeof import('../../api/print-forms.api')>(), saveDiploma: vi.fn(), uploadBackground: vi.fn(),
}))
vi.mock('../../api/protocols.api', () => ({ downloadProtocol: vi.fn() }))
beforeEach(() => { vi.clearAllMocks(); context.event.status = 'COMPLETED' })

it('saves the template, retains an uploaded background and downloads a fresh sample', async () => {
  vi.mocked(uploadBackground).mockResolvedValue({ backgroundFileKey: 'uploaded-background' })
  render(<OrganizerDiplomaPage />)
  expect(screen.getByLabelText('Заголовок')).toHaveValue('Кубок')
  await userEvent.clear(screen.getByLabelText('Текст грамоты'))
  await userEvent.type(screen.getByLabelText('Текст грамоты'), 'Победитель')
  await userEvent.upload(screen.getByLabelText('Фон грамоты (PNG/JPEG, до 5 МБ)'), new File(['png'], 'background.png', { type: 'image/png' }))
  await screen.findByText('Фон загружен')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить шаблон' }))
  await waitFor(() => expect(saveDiploma).toHaveBeenCalledWith('cup', expect.objectContaining({ enabled: true, bodyTemplate: 'Победитель', backgroundFileKey: 'uploaded-background' }), 'token'))
  await userEvent.click(screen.getByRole('button', { name: 'Пробная грамота (PDF)' }))
  await waitFor(() => expect(downloadProtocol).toHaveBeenCalledWith('/api/v1/events/cup/documents/diplomas.pdf?sample=true', 'Пробная-грамота.pdf', 'token'))
})

it('keeps archived printing available while disabling template and background changes', () => {
  context.event.status = 'ARCHIVED'
  render(<OrganizerDiplomaPage />)
  expect(screen.getByRole('button', { name: 'Сохранить шаблон' })).toBeDisabled()
  expect(screen.getByLabelText('Текст грамоты')).toBeDisabled()
  expect(screen.getByLabelText('Фон грамоты (PNG/JPEG, до 5 МБ)')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Пробная грамота (PDF)' })).toBeEnabled()
})

it('passes award and diploma filters to authenticated PDF downloads', async () => {
  render(<AwardsPrintControls />)
  await userEvent.selectOptions(screen.getByLabelText('Вид награждения'), 'f50')
  await userEvent.selectOptions(screen.getByLabelText('Группа для грамот'), 'young')
  await userEvent.click(screen.getByRole('button', { name: 'Грамоты (PDF)' }))
  await waitFor(() => expect(downloadProtocol).toHaveBeenCalledWith('/api/v1/events/cup/documents/diplomas.pdf?scope=AGE_GROUP&places=3&disciplineId=f50&ageGroupCode=young', 'Грамоты.pdf', 'token'))
  await userEvent.selectOptions(screen.getByLabelText('Область награждения'), 'ABSOLUTE')
  await userEvent.click(screen.getByRole('button', { name: 'Лист награждения (PDF)' }))
  await waitFor(() => expect(downloadProtocol).toHaveBeenCalledWith('/api/v1/events/cup/documents/awards.pdf?scope=ABSOLUTE&places=3&disciplineId=f50', 'Лист-награждения.pdf', 'token'))
})
