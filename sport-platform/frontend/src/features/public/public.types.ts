import type { EventStatus } from '../events/event.types'

// Public contracts from docs/02_ТЗ_демо.md §5.4 and §5.7.
export type Gender = 'M' | 'F' | 'X'
export type Stroke = 'FREE' | 'BACK' | 'BREAST' | 'FLY' | 'MEDLEY'
export type UnitStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'PUBLISHED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FINISHED'
  | 'CANCELLED'
export type ResultStatus = 'OK' | 'VALID' | 'DNS' | 'DNF' | 'DSQ'
export type AgeGroup = {
  code: string
  name: string
  gender: Gender
  birthYearFrom: number | null
  birthYearTo: number | null
}
export type Session = {
  id: string
  name: string
  orderNo: number
  startsAt: string
  breakBetweenEventsSec: number
}
export type Discipline = {
  id: string
  number: number
  name: string
  distanceM: number | null
  stroke: Stroke
  gender: Gender
  sessionId: string | null
  orderInSession: number | null
  unitsPublished: boolean
}
export type PublicEvent = {
  id: string
  sportId: string
  status: EventStatus
  registrationOpenAt: string | null
  registrationCloseAt: string | null
  title: string
  description: string | null
  publicSlug: string
  eventStartAt: string
  eventEndAt: string
  registrationOpen: boolean
  venue: {
    name: string
    city: string
    timezone: string
    lanes: number
    courseLength: number | null
  } | null
  sessions: Session[]
  disciplines: Discipline[]
}
export type Athlete = {
  lastName: string
  firstName: string
  birthYear: number
  club: string | null
}
export type Lane =
  | { lane: number; empty: true }
  | {
      lane: number
      empty?: false
      registrationId: string
      athlete: Athlete
      seedTimeMs: number | null
      withdrawn?: boolean
    }
export type Heat = {
  id: string
  number: number
  ageGroupCode: string | null
  scheduledAt: string | null
  lanes: Lane[]
}
export type StartList = {
  sessions: {
    id: string
    name: string
    disciplines: { id: string; number: number; name: string; heats: Heat[] }[]
  }[]
}
export type ScheduleItem = {
  sessionName: string
  disciplineName: string
  heatNumber: number
  sequenceInSession: number
  scheduledAt: string | null
  status: UnitStatus
}
export type LiveUnit = Heat & {
  disciplineId?: string
  disciplineNumber?: number
  disciplineName?: string
  sequenceInSession?: number
  status?: UnitStatus
}
export type Live = {
  currentUnit: LiveUnit | null
  next: LiveUnit[]
  updatedAt: string
}
export type SwimResult = {
  display: string | null
  status: ResultStatus
  place: number | null
  placeInAgeGroup: number | null
}
export type AthleteMatch = Athlete & {
  athleteKey: string
  swims: {
    disciplineName: string
    heatNumber: number
    lane: number | null
    scheduledAt: string | null
    result: SwimResult | null
  }[]
}
export type ResultRow = Athlete & {
  display: string | null
  status: ResultStatus
  place: number | null
  timeMs: number | null
}
export type Results = {
  disciplines: {
    id: string
    number: number
    name: string
    overall: ResultRow[]
    byAgeGroup: { code: string; name: string; rows: ResultRow[] }[]
  }[]
}
export type RegistrationDiscipline = {
  id: string
  name: string
  distanceM: number | null
  stroke: Stroke
  gender: Gender
  ageGroups: AgeGroup[]
  entryFee: number
}
export type RegistrationForm = {
  disciplines: RegistrationDiscipline[]
  requiresSeedTime: boolean
}
export type RegistrationRequest = {
  profile: {
    lastName: string
    firstName: string
    middleName: string
    birthDate: string
    gender: Exclude<Gender, 'X'>
    club: string
  }
  items: { eventDisciplineId: string; seedTimeMs: number | null }[]
}
export type FieldViolation = { field: string; message: string }
