import type { ResultStatus, StartList } from './public.types'

export function time(value: string | null) {
  return value
    ? new Intl.DateTimeFormat('ru-RU', {
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(value))
    : 'Время уточняется'
}
export function date(value: string) {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(value))
}
export function seedTime(ms: number | null) {
  if (ms === null) return 'NT'
  const hundredths = Math.floor(ms / 10)
  return `${String(Math.floor(hundredths / 6000)).padStart(2, '0')}:${String(Math.floor(hundredths / 100) % 60).padStart(2, '0')}.${String(hundredths % 100).padStart(2, '0')}`
}
export function findHeat(list: StartList, id: string) {
  for (const session of list.sessions)
    for (const discipline of session.disciplines) {
      const heat = discipline.heats.find((item) => item.id === id)
      if (heat) return { session, discipline, heat }
    }
  return undefined
}

export function isValidResult(status: ResultStatus) {
  return status === 'VALID' || status === 'OK'
}
