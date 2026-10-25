import type { RegistrationDiscipline, RegistrationForm, RegistrationRequest } from './public.types'

export function eligible(
  discipline: RegistrationDiscipline,
  profile: { birthDate: string; gender: string },
) {
  const year = Number(profile.birthDate.slice(0, 4))
  return (
    (profile.gender === 'M' || profile.gender === 'F') &&
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
  const short = /^(\d{1,2})\.(\d{0,2})$/.exec(input)
  if (short)
    return short[2].length === 2
      ? `00:${short[1].padStart(2, '0')}.${short[2]}`
      : input
  const digits = input.replace(/\D/g, '').slice(0, 6)
  return (
    digits.slice(0, 2) +
    (digits.length > 2 ? `:${digits.slice(2, 4)}` : '') +
    (digits.length > 4 ? `.${digits.slice(4)}` : '')
  )
}
export function parseSeedTime(input: string) {
  if (!input) return null
  const normalized = /^\d{1,2}\.\d{2}$/.test(input)
    ? maskSeedTime(input)
    : input
  const match = /^(\d{2}):([0-5]\d)\.(\d{2})$/.exec(normalized)
  if (!match) return undefined
  const milliseconds =
    (Number(match[1]) * 60 + Number(match[2])) * 1000 + Number(match[3]) * 10
  return milliseconds > 0 ? milliseconds : undefined
}

export type RegistrationProfile = Omit<RegistrationRequest['profile'], 'gender'> & { gender: '' | 'M' | 'F' }
export type RegistrationDraft = {
  profile: RegistrationProfile
  selected: Record<string, string>
  withoutTime: Record<string, boolean>
  consent: boolean
  step: number
}

export function emptyRegistrationDraft(): RegistrationDraft {
  return { profile: { lastName: '', firstName: '', middleName: '', birthDate: '', gender: '', club: '' }, selected: {}, withoutTime: {}, consent: false, step: 0 }
}

export function readRegistrationDraft(key: string): RegistrationDraft {
  const empty = emptyRegistrationDraft()
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? 'null')
    if (!saved || saved.version !== 1 || !saved.profile || !saved.selected) return empty
    if (typeof saved.savedAt !== 'number' || !Number.isFinite(saved.savedAt)
      || Date.now() - saved.savedAt > 7 * 24 * 60 * 60 * 1000) return empty
    for (const field of ['lastName', 'firstName', 'middleName', 'birthDate', 'gender', 'club'] as const) {
      if (typeof saved.profile[field] !== 'string') return empty
    }
    if (!['', 'M', 'F'].includes(saved.profile.gender) || ![0, 1, 2].includes(saved.step)) return empty
    if (typeof saved.selected !== 'object' || Array.isArray(saved.selected)
      || !Object.values(saved.selected).every(value => typeof value === 'string')) return empty
    return { profile: { lastName: saved.profile.lastName, firstName: saved.profile.firstName, middleName: saved.profile.middleName,
      birthDate: saved.profile.birthDate, gender: saved.profile.gender, club: saved.profile.club },
    selected: saved.selected, withoutTime: saved.withoutTime && typeof saved.withoutTime === 'object' ? Object.fromEntries(Object.entries(saved.withoutTime).filter(([, value]) => value === true)) as Record<string, boolean> : {}, consent: saved.consent === true, step: saved.step }
  } catch { return empty }
}

export function validateRegistration(
  profile: RegistrationProfile,
  selected: Record<string, string>,
  consent: boolean,
  form: RegistrationForm,
  today: string,
) {
  const errors: Record<string, string> = {}
  if (!profile.lastName.trim()) errors.lastName = 'Укажите фамилию спортсмена.'
  if (!profile.firstName.trim()) errors.firstName = 'Укажите имя спортсмена.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(profile.birthDate) || !Number.isFinite(Date.parse(profile.birthDate))
    || new Date(profile.birthDate).toISOString().slice(0, 10) !== profile.birthDate || profile.birthDate > today)
    errors.birthDate = 'Укажите дату рождения не позднее сегодняшней.'
  if (!['M', 'F'].includes(profile.gender)) errors.gender = 'Укажите пол спортсмена.'
  const disciplines = form.disciplines.filter(item => Object.hasOwn(selected, item.id))
  if (!disciplines.length) errors.disciplines = 'Выберите хотя бы один вид программы.'
  if (Object.keys(selected).length > 3) errors.disciplines = 'Выберите не больше трёх видов программы.'
  else if (disciplines.length !== Object.keys(selected).length) errors.disciplines = 'Программа изменилась. Выберите доступные виды заново.'
  for (const item of disciplines) {
    if (!eligible(item, profile)) errors[item.id] = 'Вид не подходит по полу или году рождения.'
    else if (form.requiresSeedTime && parseSeedTime(selected[item.id]) === null)
      errors[item.id] = 'Укажите заявочное время в формате мм:сс.сс.'
    else if (parseSeedTime(selected[item.id]) === undefined)
      errors[item.id] = 'Введите ненулевое время в формате мм:сс.сс (секунды 00–59).'
  }
  if (!consent) errors.consent = 'Необходимо согласие на обработку персональных данных.'
  return errors
}

export function clearRegistrationDrafts() {
  try {
    for (let index = localStorage.length - 1; index >= 0; index--) {
      const key = localStorage.key(index)
      if (key?.startsWith('sportgate.registration.')) localStorage.removeItem(key)
    }
  } catch { /* Storage may be unavailable; signing out must still succeed. */ }
}
