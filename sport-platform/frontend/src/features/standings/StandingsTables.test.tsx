import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Standings } from '../../api/standings.api'
import { StandingsTables } from './StandingsTables'

const data: Standings = {
  title: null, scope: 'AGE_GROUP', medals: [], unitsPending: 0, preliminary: false,
  disciplinesIncluded: 1, calculatedAt: '2026-10-17T12:00:00Z',
  team: [{ club: 'Волна', points: 16, gold: 1, silver: 1, bronze: 1, scoredResults: 2,
    breakdown: [
      { disciplineId: 'free', ageGroupCode: 'young', place: 1, points: 9, athlete: 'Иванов Иван', counted: true },
      { disciplineId: 'free', ageGroupCode: 'young', place: 2, points: 7, athlete: 'Петров Пётр', counted: true },
      { disciplineId: 'free', ageGroupCode: 'young', place: 3, points: 0, athlete: 'Сидоров Сергей', counted: false },
    ],
  }],
}
describe('Standings tables', () => {
  it('explains the total and the result excluded by the club cap', () => {
    render(<StandingsTables data={data} disciplineNames={{ free: '50 м вольный стиль' }} />)
    fireEvent.click(screen.getByText('Из чего сложилось'))
    expect(screen.getByText('Зачтено результатов: 2 из 3')).toBeVisible()
    expect(screen.getByText(/Сидоров Сергей: место 3, очки 0 · Не зачтён: лимит клуба/)).toBeVisible()
    expect(screen.getByText(/Иванов Иван: место 1, очки 9/)).toBeVisible()
  })
  it.each([
    [1, 'заплыв'], [2, 'заплыва'], [5, 'заплывов'], [11, 'заплывов'], [14, 'заплывов'],
    [21, 'заплыв'], [22, 'заплыва'], [25, 'заплывов'], [111, 'заплывов'],
  ])('declines %i pending heats as %s', (count, noun) => {
    render(<StandingsTables data={{ ...data, unitsPending: count, preliminary: true }} />)
    expect(screen.getByRole('status')).toHaveTextContent(`не завершено ${count} ${noun}`)
  })
})
