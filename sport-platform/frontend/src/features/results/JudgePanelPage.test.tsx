import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../app/providers/AuthProvider'
import * as api from '../../api/results.api'
import type { CompetitionUnitDetailsResponse } from '../../api/results.api'
import { JudgePanelPage } from './JudgePanelPage'

vi.mock('../../api/results.api', () => ({
  getCompetitionUnitDetails: vi.fn(), getCompetitionUnitsByDiscipline: vi.fn(),
  changeCompetitionUnitPublication: vi.fn(), changeHeatState: vi.fn(),
  saveHeatResults: vi.fn(), setLiveHeat: vi.fn(),
}))
const unit: CompetitionUnitDetailsResponse = {
  id: 'heat', eventDisciplineId: 'discipline', label: 'Заплыв 1', status: 'IN_PROGRESS',
  entries: [{ entryId: 'entry', registrationId: 'reg', participantName: 'Иванов Иван',
    laneOrPosition: 4, rawValue: '00:46.20', display: '00:46.20', resultType: 'TIME', resultStatus: 'VALID', finalPlace: 1 }],
}
function mount(status: string, role = 'operator') {
  sessionStorage.setItem('flounder.auth-session', JSON.stringify({
    accessToken: `demo.${btoa(JSON.stringify({ roles: [role] }))}.demo`, expiresAt: Date.now() + 60_000,
  }))
  vi.mocked(api.getCompetitionUnitDetails).mockResolvedValue({ ...unit, status })
  render(<AuthProvider><MemoryRouter initialEntries={['/operator/competition-units/heat']}>
    <Routes><Route path="/operator/competition-units/:unitId" element={<JudgePanelPage />} /></Routes>
  </MemoryRouter></AuthProvider>)
}
beforeEach(() => {
  vi.resetAllMocks()
  Element.prototype.scrollIntoView = vi.fn()
  sessionStorage.clear()
  vi.mocked(api.getCompetitionUnitsByDiscipline).mockResolvedValue([])
})

describe('Judge results publication', () => {
  it('publishes complete results and offers corrections after FINISHED', async () => {
    mount('IN_PROGRESS')
    const publish = await screen.findByRole('button', { name: 'Опубликовать результаты' })
    expect(publish).toBeEnabled()
    vi.mocked(api.changeCompetitionUnitPublication).mockResolvedValue({ ...unit, status: 'FINISHED' })
    await userEvent.click(publish)
    expect(api.changeCompetitionUnitPublication).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Опубликовать' }))
    expect(api.changeCompetitionUnitPublication).toHaveBeenCalledWith('heat', true, expect.any(String))
    expect(await screen.findByText('Результаты опубликованы.')).toBeInTheDocument()
    expect(screen.getByText('Завершён')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сохранить исправление' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Старт' })).toBeDisabled()
  })

  it('locks published fields for judges and hides unpublication', async () => {
    mount('FINISHED', 'judge')
    expect(await screen.findByRole('textbox')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Сохранить исправление' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Снять результаты с публикации' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Финиш' })).toBeEnabled()
  })

  it('allows operators to unpublish while preserving the start list', async () => {
    mount('FINISHED')
    expect(await screen.findByRole('textbox')).toBeEnabled()
    vi.mocked(api.changeCompetitionUnitPublication).mockResolvedValue({ ...unit, status: 'PUBLISHED' })
    await userEvent.click(screen.getByRole('button', { name: 'Снять результаты с публикации' }))
    await userEvent.click(screen.getByRole('button', { name: 'Снять' }))
    expect(api.changeCompetitionUnitPublication).toHaveBeenCalledWith('heat', false, expect.any(String))
    expect(await screen.findByText('Публикация результатов снята.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сохранить и пересчитать' })).toBeEnabled()
  })

  it('finishes an already published heat without restarting it', async () => {
    mount('FINISHED', 'judge')
    await userEvent.click(await screen.findByRole('button', { name: 'Финиш' }))
    await waitFor(() => expect(api.changeHeatState).toHaveBeenCalledWith('heat', 'finish', expect.any(String)))
    expect(await screen.findByText('Заплыв завершён.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('allows judges to edit saved times while only the start list is published', async () => {
    mount('PUBLISHED', 'judge')
    expect(await screen.findByRole('textbox')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Сохранить и пересчитать' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Финиш' })).toBeDisabled()
  })
})
it.each([['PUBLISHED', 'Старт'], ['IN_PROGRESS', 'Сохранить и пересчитать'], ['FINISHED', 'Следующий заплыв →']])('has one primary action for %s', async (status, label) => {
  mount(status)
  await screen.findByRole('textbox')
  expect(screen.getByRole('button', { name: label })).toHaveClass('fl-btn fl-btn-primary')
  expect(document.querySelectorAll('.fl-btn.fl-btn-primary')).toHaveLength(1)
})
it('shows a notice instead of action buttons without judge rights', async () => {
  mount('IN_PROGRESS', 'participant')
  expect(await screen.findByText('Нет прав для ввода результатов.')).toBeInTheDocument()
  for (const name of ['Старт', 'Финиш', 'Сохранить и пересчитать', 'Опубликовать результаты']) expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
})
