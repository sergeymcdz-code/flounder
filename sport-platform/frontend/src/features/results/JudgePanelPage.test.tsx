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
  it('preserves exact DNS, DNF and DSQ button names and Russian descriptions', async () => {
    mount('IN_PROGRESS', 'judge')
    await screen.findByRole('textbox')
    // Testing Library matches string accessible names exactly by default.
    const dns = screen.getByRole('button', { name: 'DNS' })
    expect(dns).toHaveAccessibleDescription('Не стартовал')
    expect(screen.getByRole('button', { name: 'DNF' })).toHaveAccessibleDescription('Не финишировал')
    expect(screen.getByRole('button', { name: 'DSQ' })).toHaveAccessibleDescription('Дисквалифицирован')
    await userEvent.click(dns)
    expect(dns).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('textbox')).toBeDisabled()
    await userEvent.click(dns)
    expect(dns).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('textbox')).toBeEnabled()
  })

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

it('renders lane cards at 375px and reserves the measured action bar height', async () => {
  const originalMatchMedia = window.matchMedia
  const bounds = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ height: 184, width: 375, left: 0, right: 375, top: 628, bottom: 812, x: 0, y: 628, toJSON: () => ({}) })
  window.matchMedia = vi.fn().mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  try {
    mount('IN_PROGRESS')
    const card = await screen.findByRole('article', { name: 'Дорожка 4' })
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(card).toContainElement(screen.getByRole('textbox', { name: 'Время, дорожка 4' }))
    expect(screen.queryByText(/Заявочное время/)).not.toBeInTheDocument()
    expect(screen.queryByText('Клуб не указан')).not.toBeInTheDocument()
    expect(screen.queryByText(/Место:|В группе:/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'DNS' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('group', { name: 'Действия заплыва' }).closest('section')).toHaveStyle({ '--judge-actions-height': '184px' })
    await userEvent.click(screen.getByRole('button', { name: 'DNS' }))
    expect(screen.getByRole('textbox')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'DNS' }))
    expect(screen.getByRole('textbox')).toBeEnabled()
  } finally {
    window.matchMedia = originalMatchMedia
    bounds.mockRestore()
  }
})

it.each([false, true])('keeps arrow and Enter navigation between editable lanes (mobile: %s)', async mobile => {
  const originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockReturnValue({ matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  try {
    mount('IN_PROGRESS')
    vi.mocked(api.getCompetitionUnitDetails).mockResolvedValue({ ...unit, entries: [unit.entries[0], { ...unit.entries[0], entryId: 'second', laneOrPosition: 5 }] })
    const first = await screen.findByRole('textbox', { name: 'Время, дорожка 4' })
    const second = await screen.findByRole('textbox', { name: 'Время, дорожка 5' })
    first.focus()
    await userEvent.keyboard('{ArrowDown}')
    expect(second).toHaveFocus()
    await userEvent.keyboard('{ArrowUp}')
    expect(first).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(second).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(screen.getByRole('button', { name: 'Сохранить и пересчитать' })).toHaveFocus()
  } finally { window.matchMedia = originalMatchMedia }
})

it.each([false, true])('masks six digits, submits once and reports saving/saved (mobile: %s)', async mobile => {
  const originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockReturnValue({ matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  try {
    mount('IN_PROGRESS', 'judge')
    const input = await screen.findByRole('textbox')
    await userEvent.clear(input)
    await userEvent.type(input, '003215')
    expect(input).toHaveValue('00:32.15')
    let finish!: (data: CompetitionUnitDetailsResponse) => void
    vi.mocked(api.saveHeatResults).mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const save = screen.getByRole('button', { name: 'Сохранить и пересчитать' })
    await userEvent.click(save)
    expect(screen.getByText('Сохраняем…')).toHaveAttribute('role', 'status')
    expect(save).toBeDisabled()
    expect(api.saveHeatResults).toHaveBeenCalledExactlyOnceWith('heat', [{ competitionUnitEntryId: 'entry', status: 'VALID', time: '00:32.15' }], expect.any(String))
    finish({ ...unit, entries: [{ ...unit.entries[0], display: '00:32.15' }] })
    expect(await screen.findByText(/Сохранено \d{2}:\d{2}/)).toBeVisible()
    expect(document.querySelectorAll('.fl-btn-primary')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Опубликовать результаты' })).toHaveClass('fl-btn-primary')
  } finally { window.matchMedia = originalMatchMedia }
})

it('preserves the draft after a failed save and retries the same heat', async () => {
  mount('IN_PROGRESS')
  const input = await screen.findByRole('textbox')
  await userEvent.clear(input)
  await userEvent.type(input, '003215')
  vi.mocked(api.saveHeatResults).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce({ ...unit, entries: [{ ...unit.entries[0], display: '00:32.15' }] })
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить и пересчитать' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось сохранить — повторить')
  expect(input).toHaveValue('00:32.15')
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText(/Сохранено \d{2}:\d{2}/)).toBeInTheDocument()
  expect(api.saveHeatResults).toHaveBeenCalledTimes(2)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it.each([['KeyD', 'DNS'], ['KeyN', 'DNF'], ['KeyQ', 'DSQ']])('keeps the Alt shortcut %s', async (code, status) => {
  mount('IN_PROGRESS', 'judge')
  const input = await screen.findByRole('textbox')
  input.focus()
  await userEvent.keyboard(`{Alt>}[${code}]{/Alt}`)
  expect(screen.getByRole('button', { name: status })).toHaveAttribute('aria-pressed', 'true')
  expect(input).toBeDisabled()
})

it('retries loading and offers a next step for an empty heat', async () => {
  mount('IN_PROGRESS')
  vi.mocked(api.getCompetitionUnitDetails).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce({ ...unit, entries: [] })
  expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось загрузить заплыв')
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByText('В заплыве пока нет участников.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'К списку заплывов' })).toHaveAttribute('href', '/operator/results')
})
