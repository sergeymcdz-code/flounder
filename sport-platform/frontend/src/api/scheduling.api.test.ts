import { beforeEach, expect, it, vi } from 'vitest'
import { changeCompetitionUnitPublication } from './results.api'
import { publishDisciplineResults } from './scheduling.api'
import type { Heat } from './organizer.types'

vi.mock('./results.api', () => ({ changeCompetitionUnitPublication: vi.fn() }))
const heat = (id: string, status: string, number: number): Heat => ({
  id, status, sequenceNumber: number, label: `Заплыв ${number}`, eventDisciplineId: 'discipline',
  scheduledAt: null, entries: [],
})
beforeEach(() => vi.resetAllMocks())

it('skips published results when retrying publication of a discipline', async () => {
  await publishDisciplineResults([
    heat('finished', 'FINISHED', 1), heat('completed', 'COMPLETED', 2), heat('next', 'IN_PROGRESS', 3),
  ], 'token')
  expect(changeCompetitionUnitPublication).toHaveBeenCalledExactlyOnceWith('next', true, 'token')
})

it('identifies the failed heat and preserves earlier successful publications', async () => {
  vi.mocked(changeCompetitionUnitPublication).mockRejectedValueOnce(new Error('У участника нет результата.'))
  await expect(publishDisciplineResults([heat('missing', 'PUBLISHED', 2), heat('next', 'PUBLISHED', 3)], 'token'))
    .rejects.toThrow('Заплыв 2 не опубликован. У участника нет результата.')
  expect(changeCompetitionUnitPublication).toHaveBeenCalledTimes(1)
})
