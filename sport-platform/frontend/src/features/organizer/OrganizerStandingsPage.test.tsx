import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getStandings, getStandingsConfig, saveStandingsConfig, type Standings, type StandingsConfig } from '../../api/standings.api'
import { OrganizerStandingsPage } from './OrganizerStandingsPage'

vi.mock('../../api/standings.api', () => ({
  getStandings: vi.fn(), getStandingsConfig: vi.fn(), saveStandingsConfig: vi.fn(),
}))
const initialConfig: StandingsConfig = {
  enabled: false, scope: 'AGE_GROUP', pointsByPlace: [9,7,6,5,4,3,2,1],
  maxScoredPerClubPerDiscipline: null, disciplineIds: null, published: false, title: null,
}
const result: Standings = {
  title: null, scope: 'AGE_GROUP', medals: [{ club: 'Волна', gold: 1, silver: 0, bronze: 0 }],
  team: [{ club: 'Волна', points: 9, gold: 1, silver: 0, bronze: 0, scoredResults: 1,
    breakdown: [{ disciplineId: 'free', ageGroupCode: '2014', place: 1, points: 9, athlete: 'Иванов Иван' }] }],
  unitsPending: 0, preliminary: false, disciplinesIncluded: 2, calculatedAt: '2026-10-15T12:00:00Z',
}
function page(status = 'LIVE') {
  return render(<MemoryRouter><Routes>
    <Route element={<Outlet context={{
      event: { id: 'cup', status, disciplines: [{ id: 'free', name: '50 м вольный стиль' }, { id: 'back', name: '50 м на спине' }] },
      accessToken: 'test-token', busy: false, run: async (action: () => Promise<unknown>) => { await action(); return true },
    }} />}><Route index element={<OrganizerStandingsPage />} /></Route>
  </Routes></MemoryRouter>)
}
beforeEach(() => {
  vi.mocked(getStandingsConfig).mockResolvedValue(initialConfig)
  vi.mocked(getStandings).mockResolvedValue(result)
  vi.mocked(saveStandingsConfig).mockImplementation(async (_id, config) => config)
})
describe('Organizer standings', () => {
  it('saves all configuration fields and recalculates the displayed standings', async () => {
    const user = userEvent.setup()
    page()
    await user.click(await screen.findByLabelText('Включить зачёт'))
    await user.selectOptions(screen.getByLabelText('Область зачёта'), 'ABSOLUTE')
    const points = screen.getByLabelText('Очки за места')
    await user.clear(points); await user.type(points, '10,8,6')
    await user.type(screen.getByLabelText('Лимит результатов клуба в виде / группе'), '2')
    await user.type(screen.getByLabelText('Заголовок'), 'Кубок клубов')
    await user.click(screen.getByLabelText('50 м на спине'))
    await user.click(screen.getByLabelText('Показывать публично'))
    await user.click(screen.getByRole('button', { name: 'Сохранить настройки' }))
    await waitFor(() => expect(saveStandingsConfig).toHaveBeenCalledWith('cup', {
      enabled: true, scope: 'ABSOLUTE', pointsByPlace: [10,8,6], maxScoredPerClubPerDiscipline: 2,
      disciplineIds: ['free'], title: 'Кубок клубов', published: true,
    }, 'test-token'))
    expect(await screen.findByText('Настройки сохранены, зачёт пересчитан.')).toBeInTheDocument()
    expect(getStandings).toHaveBeenCalledTimes(2)
    await user.click(screen.getByText('Из чего сложилось'))
    expect(screen.getByText(/Иванов Иван: место 1, очки 9/)).toBeVisible()
  })
  it('keeps archived configuration read only while allowing a PDF download', async () => {
    page('ARCHIVED')
    expect(await screen.findByLabelText('Включить зачёт')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Сохранить настройки' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Скачать PDF' })).toBeEnabled()
  })
})
