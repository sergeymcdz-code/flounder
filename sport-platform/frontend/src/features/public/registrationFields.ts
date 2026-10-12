import type {
  RegistrationDiscipline,
  RegistrationRequest,
} from './public.types'

export function eligible(
  discipline: RegistrationDiscipline,
  profile: RegistrationRequest['profile'],
) {
  const year = Number(profile.birthDate.slice(0, 4))
  return (
    (discipline.gender === 'X' || discipline.gender === profile.gender) &&
    !!year &&
    (!discipline.ageGroups.length ||
      discipline.ageGroups.some(
        (group) =>
          (group.gender === 'X' || group.gender === profile.gender) &&
          (group.birthYearFrom === null || year >= group.birthYearFrom) &&
          (group.birthYearTo === null || year <= group.birthYearTo),
      ))
  )
}
export function maskSeedTime(input: string) {
  const digits = input.replace(/\D/g, '').slice(0, 6)
  return (
    digits.slice(0, 2) +
    (digits.length > 2 ? `:${digits.slice(2, 4)}` : '') +
    (digits.length > 4 ? `.${digits.slice(4)}` : '')
  )
}
export function parseSeedTime(input: string) {
  if (!input) return null
  const match = /^(\d{2}):([0-5]\d)\.(\d{2})$/.exec(input)
  if (!match) return undefined
  const milliseconds =
    (Number(match[1]) * 60 + Number(match[2])) * 1000 + Number(match[3]) * 10
  return milliseconds > 0 ? milliseconds : undefined
}
