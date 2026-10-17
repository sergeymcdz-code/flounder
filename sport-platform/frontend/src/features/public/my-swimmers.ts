import { useSyncExternalStore } from 'react'
import type { Live, StartList } from './public.types'
export type MySwimmer = { name: string; club: string | null; yearOfBirth: number }
export const MY_SWIMMERS_KEY = 'sportgate.my-swimmers'
let raw: string | null | undefined
let cached: MySwimmer[] = []
function snapshot() {
  let value: string | null = null
  try { value = localStorage.getItem(MY_SWIMMERS_KEY) } catch { /* Storage can be unavailable in private mode. */ }
  if (raw !== value) {
    raw = value
    try { const parsed: unknown = JSON.parse(value ?? '[]'); cached = Array.isArray(parsed) ? parsed.filter((v): v is MySwimmer => v && typeof v.name === 'string' && (v.club === null || typeof v.club === 'string') && typeof v.yearOfBirth === 'number') : [] } catch { cached = [] }
  }
  return cached
}
function subscribe(callback: () => void) { window.addEventListener('storage', callback); window.addEventListener('sportgate:swimmers', callback); return () => { window.removeEventListener('storage', callback); window.removeEventListener('sportgate:swimmers', callback) } }
export const sameSwimmer = (a: MySwimmer, b: MySwimmer) => a.name === b.name && a.club === b.club && a.yearOfBirth === b.yearOfBirth
export function useMySwimmers() {
  const swimmers = useSyncExternalStore(subscribe, snapshot)
  function toggle(swimmer: MySwimmer) {
    const current = snapshot()
    const next = current.some(v => sameSwimmer(v, swimmer)) ? current.filter(v => !sameSwimmer(v, swimmer)) : [...current, swimmer]
    // Keep the feature usable for this tab when persistent storage is blocked.
    try { localStorage.setItem(MY_SWIMMERS_KEY, JSON.stringify(next)) } catch { return }
    window.dispatchEvent(new Event('sportgate:swimmers'))
  }
  return { swimmers, toggle }
}
export function swimmerStarts(swimmer: MySwimmer, list: StartList, live: Live) {
  const heats = list.sessions.flatMap(session => session.disciplines.flatMap(discipline => discipline.heats.map(heat => ({ heat, discipline }))))
  const current = heats.findIndex(v => v.heat.id === live.currentUnit?.id)
  return heats.flatMap(({ heat, discipline }, index) => heat.lanes.flatMap(lane => {
    if (lane.empty || !sameSwimmer(swimmer, { name: `${lane.athlete.lastName} ${lane.athlete.firstName}`, club: lane.athlete.club, yearOfBirth: lane.athlete.birthYear })) return []
    return [{ heat, discipline, lane: lane.lane, remaining: current < 0 ? null : index - current }]
  })).filter(v => v.remaining === null || v.remaining >= 0)
}
