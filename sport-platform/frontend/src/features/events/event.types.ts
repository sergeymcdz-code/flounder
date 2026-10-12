export type EventStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'FINISHED'
  | 'CANCELLED'

export type CompetitionFormat = 'INDIVIDUAL' | 'TEAM'

export type UnitType = 'TIME' | 'DISTANCE' | 'POINTS' | 'OTHER'

export type RankingStrategy = 'ASC' | 'DESC'

export type DisciplineSettings = {
  gender?: string
  ageGroup?: string
  distanceMeters?: number
  poolLengthMeters?: number
}

export type PublicEventDiscipline = {
  id: string
  code: string
  name: string
  competitionFormat: CompetitionFormat
  unitType: UnitType
  resultType: UnitType
  rankingStrategy: RankingStrategy
  participantLimit: number | null
  entryFeeAmount: number
  entryFeeCurrency: string
  settingsJson: string | null
}

export type PublicEvent = {
  id: string
  sportId: string
  title: string
  description: string | null
  registrationOpenAt: string | null
  registrationCloseAt: string | null
  eventStartAt: string | null
  eventEndAt: string | null
  status: EventStatus
  publicSlug: string
}

export type PublicEventDetails = PublicEvent & {
  disciplines: PublicEventDiscipline[]
}