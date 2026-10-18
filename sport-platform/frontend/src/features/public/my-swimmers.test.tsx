import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { it, expect, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AthleteSearch } from './LivePage'
import { MY_SWIMMERS_KEY, swimmerStarts } from './my-swimmers'
import { demoStartList as startList } from '../../mocks/demo-data'
const units = startList.sessions.flatMap(s => s.disciplines.flatMap(d => d.heats))
beforeEach(() => localStorage.removeItem(MY_SWIMMERS_KEY))
it('persists and restores a swimmer through the search star', async () => {
 const user = userEvent.setup()
 const lane = units[0].lanes.find(lane => !lane.empty)
 if (!lane || lane.empty) throw new Error('Expected swimmer')
 const { athlete } = lane
 const view = render(<MemoryRouter><AthleteSearch slug="demo" /></MemoryRouter>)
 await user.type(screen.getByRole('searchbox'), athlete.lastName)
 const star = await screen.findByRole('button', { name: `Мои пловцы: ${athlete.lastName} ${athlete.firstName}` }, { timeout: 5000 })
 await user.click(star)
 expect(star).toHaveAttribute('aria-pressed', 'true')
 expect(JSON.parse(localStorage.getItem(MY_SWIMMERS_KEY)!)).toEqual([{ name: `${athlete.lastName} ${athlete.firstName}`, club: athlete.club, yearOfBirth: athlete.birthYear }])
 view.unmount()
 render(<MemoryRouter><AthleteSearch slug="demo" /></MemoryRouter>)
 await user.type(screen.getByRole('searchbox'), athlete.lastName)
 expect(await screen.findByRole('button', { name: `Мои пловцы: ${athlete.lastName} ${athlete.firstName}` }, { timeout: 5000 })).toHaveAttribute('aria-pressed', 'true')
})
it('counts remaining heats using the current heat and matches full identity', () => {
 const heats = startList.sessions.flatMap(s => s.disciplines.flatMap(d => d.heats))
 const target = heats.find((h, index) => index > 0 && h.lanes.some(l => !l.empty))!
 const lane = target.lanes.find(l => !l.empty)!
 if (lane.empty) throw new Error('Expected swimmer')
 const swimmer = { name: `${lane.athlete.lastName} ${lane.athlete.firstName}`, club: lane.athlete.club, yearOfBirth: lane.athlete.birthYear }
 const starts = swimmerStarts(swimmer, startList, { currentUnit: units[0], next: [], updatedAt: '' })
 expect(starts.find(s => s.heat.id === target.id)?.remaining).toBe(heats.indexOf(target))
 expect(swimmerStarts({ ...swimmer, yearOfBirth: 1900 }, startList, { currentUnit: units[0], next: [], updatedAt: '' })).toEqual([])
})
