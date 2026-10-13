import type { Event, EventDiscipline } from './events.api'
import type { Registration } from '../features/registrations/registration.types'

export type AgeGroup = {
  code: string
  name: string
  gender: string
  birthYearFrom: number | null
  birthYearTo: number | null
}
export type AgeGroupPreset = { code: string; name: string; groups: AgeGroup[] }
export type Session = {
  id: string
  name: string
  orderNo: number
  startsAt: string
  breakBetweenEventsSec: number
  heatsCount?: number
  estimatedEndsAt?: string | null
}
export type SessionInput = Pick<
  Session,
  'name' | 'orderNo' | 'startsAt' | 'breakBetweenEventsSec'
>
export type DisciplineInput = {
  code?: string
  name?: string
  disciplineTemplateId: string
  distanceM: number
  stroke: string
  gender: string
  seedingMode: string
  sessionId: string | null
  orderInSession: number
  heatDurationSec: number | null
  ageGroups: AgeGroup[]
}
export type SwimDiscipline = EventDiscipline &
  DisciplineInput & { status: string; number?: number }
export type OrganizerEventDetails = Omit<Event, 'disciplines'> & {
  disciplines: SwimDiscipline[]
  venue?: {
    name: string
    city: string
    lanes: number | null
    courseLength: number | null
  }
}
export type OrganizerRegistration = Omit<Registration, 'status'> & {
  status: string
  seedTimeMs: number | null
  seedTime?: string | null
  paymentStatus: string
  source: string
}
export type Violation = {
  code: string
  severity: 'ERROR' | 'WARNING'
  message: string
  eventDisciplineId?: string
  registrationId?: string
  heatNumber?: number
  unitId?: string
  competitionUnitId?: string
}
export type Heat = {
  id: string
  eventDisciplineId: string
  label: string
  sequenceNumber: number | null
  status: string
  scheduledAt: string | null
  ageGroupCode?: string | null
  entries: HeatEntry[]
}
export type HeatEntry = {
  entryId: string
  registrationId: string
  laneOrPosition: number | null
  participantName: string
  rawValue: string | null
  resultStatus: string | null
}
export type GenerationResult = {
  runId: string
  heats: {
    id: string
    number: number
    ageGroupCode: string | null
    scheduledAt: string | null
    lanes: {
      lane: number
      registrationId: string
      athlete: {
        lastName: string
        firstName: string
        birthYear: number
        club: string
      }
      seedTimeMs: number | null
    }[]
  }[]
  violations: Violation[]
  excludedRegistrationIds: string[]
  durationMs: number
}
export type EventGenerationResult = {
  disciplines: (
    | { eventDisciplineId: string; result: GenerationResult }
    | (GenerationResult & { eventDisciplineId: string })
  )[]
  durationMs: number
  violations?: Violation[]
}
export type ImportReport = {
  total: number
  created: number
  updated: number
  skipped: number
  createdProfiles: number
  errors: { row: number; column: string; message: string }[]
}

export type DisciplineTemplate = {
  id: string
  sportId: string
  code: string
  name: string
}
export type GenerationSummary = {
  durationMs: number
  violations: Violation[]
  excludedRegistrationIds: string[]
}
