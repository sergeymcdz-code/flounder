import { http, HttpResponse } from 'msw'
import {
  demoEvent,
  demoForm,
  demoResults,
  demoSchedule,
  demoStartList,
} from './demo-data'
import type {
  AthleteMatch,
  Live,
  RegistrationRequest,
} from '../features/public/public.types'

const path = '*/api/v1/public/events/:slug'
const units = demoStartList.sessions.flatMap((session) =>
  session.disciplines.flatMap((discipline) =>
    discipline.heats.map((heat) => ({
      ...heat,
      disciplineId: discipline.id,
      disciplineNumber: discipline.number,
      disciplineName: discipline.name,
      sequenceInSession: demoSchedule.find(
        (item) =>
          item.disciplineName === discipline.name &&
          item.heatNumber === heat.number &&
          item.scheduledAt === heat.scheduledAt,
      )?.sequenceInSession,
    })),
  ),
)
const startedAt = Date.now()
function currentIndex() {
  return Math.floor((Date.now() - startedAt) / 15_000) % units.length
}
function checkSlug(slug: unknown) {
  return slug !== 'demo'
    ? HttpResponse.json(
        {
          code: 'event.not_found',
          message: 'Соревнование не найдено.',
          timestamp: new Date().toISOString(),
          violations: [],
        },
        { status: 404 },
      )
    : undefined
}
export const publicHandlers = [
  http.get('*/api/v1/public/events', () => HttpResponse.json([demoEvent])),
  http.get(
    path,
    ({ params }) => checkSlug(params.slug) ?? HttpResponse.json(demoEvent),
  ),
  http.get(
    `${path}/start-list`,
    ({ params }) => checkSlug(params.slug) ?? HttpResponse.json(demoStartList),
  ),
  http.get(
    `${path}/schedule`,
    ({ params }) =>
      checkSlug(params.slug) ??
      HttpResponse.json(
        demoSchedule.map((item, index) => ({
          ...item,
          status:
            index === currentIndex()
              ? 'IN_PROGRESS'
              : index < currentIndex()
                ? 'FINISHED'
                : 'PUBLISHED',
        })),
      ),
  ),
  http.get(`${path}/live`, ({ params }) => {
    const index = currentIndex()
    const live: Live = {
      currentUnit: { ...units[index], status: 'IN_PROGRESS' },
      next: units.slice(index + 1, index + 4),
      updatedAt: new Date().toISOString(),
    }
    return checkSlug(params.slug) ?? HttpResponse.json(live)
  }),
  http.get(
    `${path}/results`,
    ({ params }) => checkSlug(params.slug) ?? HttpResponse.json(demoResults),
  ),
  http.get(
    `${path}/registration-form`,
    ({ params }) => checkSlug(params.slug) ?? HttpResponse.json(demoForm),
  ),
  http.get(`${path}/athletes`, ({ params, request }) => {
    const query =
      new URL(request.url).searchParams
        .get('q')
        ?.trim()
        .toLocaleLowerCase('ru-RU')
        .replaceAll('ё', 'е') ?? ''
    const matches = new Map<string, AthleteMatch>()
    for (const session of demoStartList.sessions)
      for (const discipline of session.disciplines)
        for (const heat of discipline.heats)
          for (const lane of heat.lanes) {
            if (
              lane.empty ||
              !query ||
              !lane.athlete.lastName
                .toLocaleLowerCase('ru-RU')
                .replaceAll('ё', 'е')
                .startsWith(query)
            )
              continue
            const row = demoResults.disciplines
              .find((item) => item.id === discipline.id)
              ?.overall.find(
                (item) =>
                  item.lastName === lane.athlete.lastName &&
                  item.firstName === lane.athlete.firstName &&
                  item.birthYear === lane.athlete.birthYear,
              )
            if (!row) continue
            const athleteKey = lane.registrationId.split('-').at(-1)!
            const groupRow = demoResults.disciplines
              .find((item) => item.id === discipline.id)
              ?.byAgeGroup.flatMap((group) => group.rows)
              .find(
                (item) =>
                  item.lastName === row.lastName &&
                  item.firstName === row.firstName &&
                  item.birthYear === row.birthYear,
              )
            const match = matches.get(athleteKey) ?? {
              ...lane.athlete,
              athleteKey,
              swims: [],
            }
            match.swims.push({
              disciplineName: discipline.name,
              heatNumber: heat.number,
              lane: lane.lane,
              scheduledAt: heat.scheduledAt,
              result: {
                display: row.display,
                status: row.status,
                place: row.place,
                placeInAgeGroup: groupRow?.place ?? null,
              },
            })
            matches.set(athleteKey, match)
          }
    return (
      checkSlug(params.slug) ??
      HttpResponse.json([...matches.values()].slice(0, 20))
    )
  }),
  http.post(`${path}/registrations`, async ({ params, request }) => {
    const missing = checkSlug(params.slug)
    if (missing) return missing
    if (!request.headers.get('authorization'))
      return HttpResponse.json(
        {
          code: 'auth.required',
          message: 'Войдите, чтобы подать заявку.',
          violations: [],
        },
        { status: 401 },
      )
    const body = (await request.json()) as RegistrationRequest
    // Deterministic validation scenario for manual per-discipline error checks.
    if (body.profile.lastName === 'Ошибка')
      return HttpResponse.json(
        {
          code: 'registration.gender_mismatch',
          message: 'Проверьте выбранные виды.',
          timestamp: new Date().toISOString(),
          violations: [
            {
              field: 'items[0].eventDisciplineId',
              message: 'Этот вид не подходит спортсмену.',
            },
          ],
        },
        { status: 400 },
      )
    return new HttpResponse(null, { status: 201 })
  }),
]
