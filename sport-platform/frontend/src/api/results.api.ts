import { apiFetch } from './http'

export type CompetitionUnitId = string
export type CompetitionUnitEntryId = string

export type ResultType = 'TIME' | 'POINTS'
export type RankingStrategy = 'ASC' | 'DESC'

export type CompetitionUnitResponse = {
  id: CompetitionUnitId
  eventDisciplineId: string
  label: string
  sequenceNumber: number | null
  status: string
  scheduledAt: string | null
}

export type CreateCompetitionUnitRequest = {
  eventDisciplineId: string
  label: string
  sequenceNumber: number
}

export type AssignRegistrationRequest = {
  registrationId: string
  laneOrPosition: number | null
}

export type RecordResultRequest = {
  competitionUnitEntryId: CompetitionUnitEntryId
  rawValue: string
  status?: (
    | 'PENDING'
    | 'DID_NOT_START'
    | 'DID_NOT_FINISH'
    | 'DISQUALIFIED'
  )
}

export type ResultResponse = {
  id: string
  competitionUnitEntryId: CompetitionUnitEntryId
  rawValue: string | null
  resultType: string
  status: string
  finalPlace: number | null
}

export type CompetitionUnitEntryView = {
  entryId: string
  participantName: string
  registrationId: string
  laneOrPosition: number | null
  rawValue: string | null
  resultType: string | null
  resultStatus: string | null
  finalPlace: number | null
}

export type CompetitionUnitDetailsResponse = {
  id: CompetitionUnitId
  eventDisciplineId: string
  label: string
  status: string
  entries: CompetitionUnitEntryView[]
}

export type PublicCompetitionUnitResultEntry = {
  participantName: string
  laneOrPosition: number | null
  rawValue: string | null
  resultType: string | null
  resultStatus: string | null
  finalPlace: number | null
}

export type PublicCompetitionUnitResultsResponse = {
  id: CompetitionUnitId
  eventDisciplineId: string
  label: string
  entries: PublicCompetitionUnitResultEntry[]
}

export function getCompetitionUnitDetails(
  unitId: CompetitionUnitId,
  accessToken: string,
): Promise<CompetitionUnitDetailsResponse> {
  return apiFetch<CompetitionUnitDetailsResponse>(
    `/api/v1/competition-units/${encodeURIComponent(unitId)}`,
    { accessToken },
  )
}

export function getPublicCompetitionUnitResults(
  unitId: CompetitionUnitId,
): Promise<PublicCompetitionUnitResultsResponse> {
  return apiFetch<PublicCompetitionUnitResultsResponse>(
    `/api/v1/public/competition-units/${encodeURIComponent(unitId)}/results`,
  )
}

/**
 * Создаёт стартовый протокол: заплыв, матч, группу или финал.
 * Backend: PLATFORM_ADMIN или ORGANIZER.
 */
export function createCompetitionUnit(
  payload: CreateCompetitionUnitRequest,
  accessToken: string,
): Promise<CompetitionUnitResponse> {
  return apiFetch<CompetitionUnitResponse>(
    '/api/v1/competition-units',
    {
      method: 'POST',
      accessToken,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

/**
 * Назначает подтверждённую заявку в unit и задаёт дорожку/позицию.
 * Backend: PLATFORM_ADMIN или ORGANIZER.
 */
export function assignRegistrationToUnit(
  unitId: CompetitionUnitId,
  payload: AssignRegistrationRequest,
  accessToken: string,
): Promise<string> {
  return apiFetch<string>(
    `/api/v1/competition-units/${encodeURIComponent(unitId)}/entries`,
    {
      method: 'POST',
      accessToken,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

/**
 * Сохраняет новый результат или обновляет уже существующий.
 * Backend: PLATFORM_ADMIN или OPERATOR.
 *
 * Для обычного результата передавайте rawValue.
 * Для DNS/DNF/DSQ передавайте status без rawValue.
 */
export function recordResult(
  payload: RecordResultRequest,
  options: {
    resultType: ResultType
    accessToken: string
  },
): Promise<ResultResponse> {
  const params = new URLSearchParams({
    resultType: options.resultType,
  })

  return apiFetch<ResultResponse>(
    `/api/v1/results?${params.toString()}`,
    {
      method: 'POST',
      accessToken: options.accessToken,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )
}

/**
 * Пересчитывает итоговые места.
 *
 * ASC — меньшее число лучше, например время.
 * DESC — большее число лучше, например очки.
 *
 * Backend возвращает обновлённую CompetitionUnitDetailsResponse.
 */
export function recalculatePlaces(
  unitId: CompetitionUnitId,
  options: {
    rankingStrategy: RankingStrategy
    accessToken: string
  },
): Promise<CompetitionUnitDetailsResponse> {
  const params = new URLSearchParams({
    rankingStrategy: options.rankingStrategy,
  })

  return apiFetch<CompetitionUnitDetailsResponse>(
    `/api/v1/competition-units/${encodeURIComponent(
      unitId,
    )}/recalculate?${params.toString()}`,
    {
      method: 'POST',
      accessToken: options.accessToken,
    },
  )
}

/**
 * Публикует результаты либо возвращает unit в DRAFT.
 * Backend: PLATFORM_ADMIN, OPERATOR, ORGANIZER или JUDGE.
 */
export function changeCompetitionUnitPublication(
  unitId: CompetitionUnitId,
  published: boolean,
  accessToken: string,
): Promise<CompetitionUnitDetailsResponse> {
  return apiFetch<CompetitionUnitDetailsResponse>(
    `/api/v1/competition-units/${encodeURIComponent(unitId)}/publication`,
    {
      method: 'POST',
      accessToken,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ published }),
    },
  )
}

export function getCompetitionUnitsByDiscipline(
  eventDisciplineId: string,
  accessToken: string,
): Promise<CompetitionUnitResponse[]> {
  return apiFetch<CompetitionUnitResponse[]>(
    `/api/v1/event-disciplines/${encodeURIComponent(
      eventDisciplineId,
    )}/competition-units`,
    {
      accessToken,
    },
  )
}



export type PublicDisciplineResultEntry = {
  place: number | null
  participantName: string
  clubName: string | null
  laneOrPosition: number | null
  rawValue: string | null
  resultType: string | null
  status: string
}

export type PublicDisciplineResultUnit = {
  id: CompetitionUnitId
  label: string
  sequenceNumber: number | null
  entries: PublicDisciplineResultEntry[]
}

export type PublicDisciplineResultsResponse = {
  eventId: string
  eventDisciplineId: string
  units: PublicDisciplineResultUnit[]
}

export function getPublicDisciplineResults(
  eventId: string,
  eventDisciplineId: string,
): Promise<PublicDisciplineResultsResponse> {
  return apiFetch<PublicDisciplineResultsResponse>(
    `/api/v1/public/events/${encodeURIComponent(
      eventId,
    )}/disciplines/${encodeURIComponent(
      eventDisciplineId,
    )}/results`,
  )
}