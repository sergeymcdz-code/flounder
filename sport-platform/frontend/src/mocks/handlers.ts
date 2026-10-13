import { delay, http, HttpResponse } from 'msw'
import { isDemoParticipant } from './auth-handlers'
import { createFixtures } from './fixtures'
import type {
  DisciplineInput,
  GenerationResult,
  Heat,
  ImportReport,
  SessionInput,
  Violation,
} from '../api/organizer.types'
import templateUrl from './import-template.xlsx?url'
const state = createFixtures()
const path = (value: string) => `*/api/v1${value}`
const error = (
  status: number,
  code: string,
  message: string,
  violations: { field: string; message: string }[] = [],
) =>
  HttpResponse.json(
    { code, message, timestamp: new Date().toISOString(), violations },
    { status },
  )
const notFound = () => error(404, 'demo.not_found', 'Запись не найдена.')
function generate(disciplineId: string, force: boolean): GenerationResult {
  const d = state.event.disciplines.find((d) => d.id === disciplineId)!
  const registrations = state.registrations.filter(
    (r) => r.eventDisciplineId === disciplineId,
  )
  const eligibilityViolations: Violation[] = []
  const seen = new Set<string>()
  const admitted = registrations.filter((r) => {
    if (!['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)) return false
    const year = Number(r.participantSnapshot.birthDate?.slice(0, 4))
    let code = ''
    if (d.gender !== 'X' && d.gender !== r.participantSnapshot.gender)
      code = 'GENDER_MISMATCH'
    else if (
      d.ageGroups.length &&
      !d.ageGroups.some(
        (g) =>
          year >= (g.birthYearFrom ?? 1900) &&
          year <= (g.birthYearTo ?? 2100) &&
          (g.gender === 'X' || g.gender === r.participantSnapshot.gender),
      )
    )
      code = 'AGE_GROUP_MISMATCH'
    else if (seen.has(r.participantProfileId)) code = 'DUPLICATE_ENTRY'
    if (code) {
      eligibilityViolations.push({
        code,
        severity: 'ERROR',
        message: 'Заявка не соответствует условиям допуска.',
        eventDisciplineId: d.id,
        registrationId: r.id,
      })
      return false
    }
    seen.add(r.participantProfileId)
    return true
  })
  state.heats = state.heats.filter((h) => h.eventDisciplineId !== disciplineId)
  const created: Heat[] = []
  const groups =
    d.seedingMode === 'BY_AGE_GROUP' && d.ageGroups.length
      ? d.ageGroups
      : [
          {
            code: '',
            name: '',
            gender: 'X',
            birthYearFrom: 1900,
            birthYearTo: 2100,
          },
        ]
  for (const group of groups) {
    const rows = admitted
      .filter((r) => {
        const year = Number(r.participantSnapshot.birthDate?.slice(0, 4))
        return (
          year >= (group.birthYearFrom ?? 1900) &&
          year <= (group.birthYearTo ?? 2100) &&
          (group.gender === 'X' ||
            group.gender === r.participantSnapshot.gender)
        )
      })
      .sort((a, b) => (b.seedTimeMs ?? Infinity) - (a.seedTimeMs ?? Infinity))
    const sizes: number[] = []
    if (rows.length <= 8) {
      if (rows.length) sizes.push(rows.length)
    } else {
      const rest = rows.length % 8
      if (rest >= 3) sizes.push(rest)
      else if (rest > 0) sizes.push(3, 8 - (3 - rest))
      const remaining = rows.length - sizes.reduce((sum, size) => sum + size, 0)
      for (let i = 0; i < remaining; i += 8) sizes.push(8)
    }
    let start = 0
    for (const size of sizes) {
      created.push({
        id: crypto.randomUUID(),
        eventDisciplineId: d.id,
        label: `Заплыв ${created.length + 1}`,
        sequenceNumber: created.length + 1,
        status: 'DRAFT',
        scheduledAt: null,
        ageGroupCode: group.code || null,
        entries: rows
          .slice(start, start + size)
          .reverse()
          .map((r, index) => ({
            entryId: crypto.randomUUID(),
            registrationId: r.id,
            participantName: `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName}`,
            laneOrPosition: [4, 5, 3, 6, 2, 7, 1, 8][index],
            rawValue: null,
            resultStatus: 'PENDING',
          })),
      })
      start += size
    }
  }
  state.heats.push(...created)
  const violations: Violation[] = created
    .filter((h) => h.entries.length < 3)
    .map((h) => ({
      code: 'SMALL_GROUP',
      severity: 'WARNING',
      message: `В заплыве ${h.sequenceNumber} меньше трёх участников.`,
      eventDisciplineId: h.eventDisciplineId,
      heatNumber: h.sequenceNumber ?? undefined,
    }))
  violations.push(...eligibilityViolations)
  if (force)
    violations.push({
      code: 'REGENERATED_PUBLISHED',
      severity: 'WARNING',
      message:
        'Опубликованные заплывы заменены. Требуется повторная публикация.',
    })
  return {
    runId: crypto.randomUUID(),
    durationMs: 41,
    violations,
    excludedRegistrationIds: registrations
      .filter(
        (r) =>
          !created.some((h) =>
            h.entries.some((e) => e.registrationId === r.id),
          ),
      )
      .map((r) => r.id),
    heats: created.map((h) => ({
      id: h.id,
      number: h.sequenceNumber!,
      ageGroupCode: h.ageGroupCode ?? null,
      scheduledAt: h.scheduledAt,
      lanes: h.entries.map((e) => {
        const r = registrations.find((r) => r.id === e.registrationId)!
        return {
          lane: e.laneOrPosition!,
          registrationId: e.registrationId,
          athlete: {
            lastName: r.participantSnapshot.lastName,
            firstName: r.participantSnapshot.firstName,
            birthYear: Number(r.participantSnapshot.birthDate?.slice(0, 4)),
            club: r.participantSnapshot.clubName ?? '',
          },
          seedTimeMs: r.seedTimeMs,
        }
      }),
    })),
  }
}
export const handlers = [
  http.post(path('/auth/login'), async ({ request }) => {
    const body = (await request.clone().json()) as { email?: string }
    if (isDemoParticipant(body.email ?? '')) return
    await delay(100)
    const token = `demo.${btoa(JSON.stringify({ roles: ['organizer', 'operator'], exp: Math.floor(Date.now() / 1000) + 3600 }))}.demo`
    return HttpResponse.json({
      accessToken: token,
      tokenType: 'Bearer',
      expiresIn: 3600,
    })
  }),
  // Guard the mocked organizer API so missing credentials behave like production.
  http.all(path('/*'), ({ request }) => {
    const pathname = new URL(request.url).pathname
    if (
      pathname.startsWith('/api/v1/public/') ||
      pathname.startsWith('/api/v1/auth/')
    ) return
    const token = request.headers.get('Authorization')?.replace('Bearer ', '')
    if (!token) return error(401, 'auth.unauthorized', 'Войдите в систему.')
    try {
      const payload = JSON.parse(atob(token.split('.')[1])) as {
        roles?: string[]
        exp?: number
      }
      if (payload.exp && payload.exp * 1000 < Date.now())
        return error(401, 'auth.expired', 'Сессия истекла. Войдите снова.')
      if (
        !payload.roles?.some((r) =>
          ['organizer', 'operator', 'platform_admin', 'platformadmin'].includes(
            r.toLowerCase(),
          ),
        )
      )
        return error(
          403,
          'events.access_denied',
          'Нет доступа к этому событию.',
        )
    } catch {
      return error(401, 'auth.unauthorized', 'Сессия недействительна.')
    }
  }),
  http.get(path('/venues'), () =>
    HttpResponse.json([
      {
        id: state.event.venueId,
        name: state.event.venue?.name,
        city: state.event.venue?.city,
        venueMeta: {
          lanes: state.event.venue?.lanes,
          courseLength: state.event.venue?.courseLength,
        },
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        countryCode: 'RU',
        address: 'Демо-площадка',
      },
    ]),
  ),
  http.get(path('/events/:id/disciplines'), () =>
    HttpResponse.json(state.event.disciplines),
  ),
  http.get(path('/organizer/events'), () => HttpResponse.json([state.event])),
  http.get(path('/events/:id'), ({ params }) =>
    params.id === state.event.id ? HttpResponse.json(state.event) : notFound(),
  ),
  http.post(path('/events/:id/:transition'), ({ params }) => {
    if (params.id !== state.event.id) return notFound()
    const transitions: Record<string, { from: string[]; to: string }> = {
      publish: { from: ['DRAFT'], to: 'PUBLISHED' },
      'open-registration': { from: ['PUBLISHED'], to: 'REGISTRATIONOPEN' },
      'close-registration': {
        from: ['REGISTRATIONOPEN'],
        to: 'REGISTRATIONCLOSED',
      },
      complete: { from: ['REGISTRATIONCLOSED', 'LIVE'], to: 'COMPLETED' },
    }
    const transition = transitions[String(params.transition)]
    if (!transition) return
    if (!transition.from.includes(state.event.status))
      return error(
        409,
        'events.invalid_transition',
        'Переход из текущего статуса недоступен.',
      )
    state.event.status = transition.to
    return HttpResponse.json(state.event)
  }),
  http.get(path('/events/:id/sessions'), ({ params }) =>
    params.id === state.event.id
      ? HttpResponse.json(state.sessions)
      : notFound(),
  ),
  http.post(path('/events/:id/sessions'), async ({ request, params }) => {
    if (params.id !== state.event.id) return notFound()
    const body = (await request.json()) as SessionInput
    if (!body.name.trim())
      return error(400, 'sessions.invalid', 'Проверьте поля.', [
        { field: 'name', message: 'Введите название сессии.' },
      ])
    const session = { ...body, id: crypto.randomUUID() }
    state.sessions.push(session)
    return HttpResponse.json(session, { status: 201 })
  }),
  http.patch(path('/event-sessions/:id'), async ({ request, params }) => {
    const s = state.sessions.find((s) => s.id === params.id)
    if (!s) return notFound()
    Object.assign(s, await request.json())
    return HttpResponse.json(s)
  }),
  http.delete(path('/event-sessions/:id'), ({ params }) => {
    if (state.event.disciplines.some((d) => d.sessionId === params.id))
      return error(
        409,
        'sessions.in_use',
        'Сначала перенесите виды программы в другую сессию.',
      )
    state.sessions = state.sessions.filter((s) => s.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
  http.get(path('/discipline-templates'), () =>
    HttpResponse.json(state.templates),
  ),
  http.get(path('/age-group-presets'), () => HttpResponse.json(state.presets)),
  http.post(path('/events/:id/disciplines'), async ({ params, request }) => {
    if (params.id !== state.event.id) return notFound()
    const body = (await request.json()) as DisciplineInput
    const invalid = disciplineErrors(body)
    if (invalid.length)
      return error(400, 'disciplines.invalid', 'Проверьте поля вида.', invalid)
    const d = {
      ...body,
      code: body.code || `SWIM-${state.event.disciplines.length + 1}`,
      id: crypto.randomUUID(),
      name:
        body.name ||
        `${body.distanceM} м ${body.stroke === 'FREE' ? 'вольный стиль' : 'плавание'}, ${body.gender === 'F' ? 'девушки' : 'юноши'}`,
      competitionFormat: 'INDIVIDUAL',
      unitType: 'HEAT',
      resultType: 'TIME',
      rankingStrategy: 'ASC',
      participantLimit: null,
      entryFeeAmount: 0,
      entryFeeCurrency: 'RUB',
      settingsJson: null,
      status: 'DRAFT',
      number: state.event.disciplines.length + 1,
    }
    state.event.disciplines.push(d)
    return HttpResponse.json(d, { status: 201 })
  }),
  http.patch(path('/event-disciplines/:id'), async ({ params, request }) => {
    const d = state.event.disciplines.find((d) => d.id === params.id)
    if (!d) return notFound()
    const body = (await request.json()) as DisciplineInput
    const invalid = disciplineErrors(body, d.id)
    if (invalid.length)
      return error(400, 'disciplines.invalid', 'Проверьте поля вида.', invalid)
    Object.assign(d, body)
    return HttpResponse.json(d)
  }),
  http.delete(path('/event-disciplines/:id'), ({ params }) => {
    if (
      state.event.status !== 'DRAFT' &&
      state.registrations.some((r) => r.eventDisciplineId === params.id)
    )
      return error(
        409,
        'disciplines.has_registrations',
        'Нельзя удалить вид с заявками после публикации события.',
      )
    state.event.disciplines = state.event.disciplines.filter(
      (d) => d.id !== params.id,
    )
    state.heats = state.heats.filter((h) => h.eventDisciplineId !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post(
    path('/events/:id/disciplines/:disciplineId/publish'),
    ({ params }) => {
      const d = state.event.disciplines.find(
        (d) => d.id === params.disciplineId,
      )
      if (!d) return notFound()
      d.status = 'PUBLISHED'
      return HttpResponse.json(d)
    },
  ),
  http.get(path('/registrations'), ({ request }) => {
    const url = new URL(request.url)
    return HttpResponse.json(
      state.registrations.filter(
        (r) =>
          (!url.searchParams.get('eventId') ||
            r.eventId === url.searchParams.get('eventId')) &&
          (!url.searchParams.get('status') ||
            r.status === url.searchParams.get('status')),
      ),
    )
  }),
  http.patch(path('/registrations/:id'), async ({ params, request }) => {
    const r = state.registrations.find((r) => r.id === params.id)
    if (!r) return notFound()
    const body = (await request.json()) as { seedTimeMs: number | null }
    if (
      body.seedTimeMs != null &&
      (!Number.isInteger(body.seedTimeMs) || body.seedTimeMs <= 0)
    )
      return error(
        400,
        'registrations.invalid_seed_time',
        'Заявочное время должно быть больше нуля.',
      )
    Object.assign(r, body)
    return HttpResponse.json(r)
  }),
  http.post(path('/registrations/:id/:action'), async ({ params, request }) => {
    const r = state.registrations.find((r) => r.id === params.id)
    if (!r) return notFound()
    if (params.action === 'review') {
      const body = (await request.json()) as {
        decision: string
        reviewNote: string
      }
      r.status = body.decision
      r.reviewNote = body.reviewNote
    } else if (params.action === 'mark-paid') {
      r.paymentStatus = 'PAID'
      if (r.status === 'CONFIRMED') r.status = 'PAID'
    } else if (params.action === 'withdraw') r.status = 'WITHDRAWN'
    else return
    return HttpResponse.json(r)
  }),
  http.get(
    path('/events/:id/registrations/import-template'),
    async () =>
      new HttpResponse(await (await fetch(templateUrl)).arrayBuffer(), {
        headers: {
          'Content-Type':
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        },
      }),
  ),
  http.post(path('/events/:id/registrations/import'), async ({ request }) => {
    const body = await request.formData()
    if (!(body.get('file') instanceof File))
      return error(400, 'imports.file_required', 'Выберите файл Excel.')
    await delay(650)
    for (let i = 0; i < 3; i++) {
      const base = state.registrations[i]
      state.registrations.push({
        ...structuredClone(base),
        id: crypto.randomUUID(),
        participantProfileId: crypto.randomUUID(),
        status: 'CONFIRMED',
        paymentStatus: 'NOT_REQUIRED',
        source: 'IMPORT',
        participantSnapshot: {
          ...base.participantSnapshot,
          lastName: ['Беляева', 'Громов', 'Лебедева'][i],
        },
      })
    }
    state.registrations[0].seedTimeMs = 31200
    state.registrations[0].source = 'IMPORT'
    return HttpResponse.json({
      total: 5,
      created: 3,
      updated: 1,
      skipped: 0,
      createdProfiles: 3,
      errors: [
        {
          row: 6,
          column: 'Дата рождения',
          message: 'Неверный формат, ожидается ДД.ММ.ГГГГ.',
        },
      ],
    } satisfies ImportReport)
  }),
  http.get(path('/event-disciplines/:id/competition-units'), ({ params }) =>
    HttpResponse.json(
      state.heats.filter((h) => h.eventDisciplineId === params.id),
    ),
  ),
  http.get(path('/competition-units/:id'), ({ params }) => {
    const h = state.heats.find((h) => h.id === params.id)
    return h ? HttpResponse.json(h) : notFound()
  }),
  http.post(
    path('/event-disciplines/:id/generate-competition-units'),
    async ({ params, request }) => {
      const id = String(params.id)
      if (!state.event.disciplines.some((d) => d.id === id)) return notFound()
      const body = (await request.json()) as { force: boolean }
      if (
        !body.force &&
        state.heats.some(
          (h) => h.eventDisciplineId === id && h.status !== 'DRAFT',
        )
      )
        return error(
          409,
          'scheduling.published_units_exist',
          'Есть опубликованные заплывы. Подтвердите повторное формирование.',
        )
      return HttpResponse.json(generate(id, body.force))
    },
  ),
  http.post(
    path('/events/:id/generate-competition-units'),
    async ({ request }) => {
      const body = (await request.json()) as { force: boolean }
      if (!body.force && state.heats.some((h) => h.status !== 'DRAFT'))
        return error(
          409,
          'scheduling.published_units_exist',
          'Есть опубликованные заплывы. Подтвердите повторное формирование.',
        )
      const disciplines = state.event.disciplines.map((d) => ({
        eventDisciplineId: d.id,
        result: generate(d.id, body.force),
      }))
      return HttpResponse.json({
        disciplines,
        durationMs: 41 * disciplines.length,
      })
    },
  ),
  http.post(path('/events/:id/schedule/recalculate'), () => {
    const units = state.event.disciplines.flatMap((d) => {
      const session = state.sessions.find((s) => s.id === d.sessionId)
      const base =
        new Date(session?.startsAt ?? state.event.eventStartAt).getTime() +
        (d.orderInSession - 1) * 600000
      return state.heats
        .filter((h) => h.eventDisciplineId === d.id)
        .map((h, i) => {
          h.scheduledAt = new Date(
            base + i * (d.heatDurationSec ?? 180) * 1000,
          ).toISOString()
          return {
            id: h.id,
            number: h.sequenceNumber,
            scheduledAt: h.scheduledAt,
            sequenceInSession: i + 1,
          }
        })
    })
    return HttpResponse.json({ units })
  }),
  http.get(
    path('/event-disciplines/:id/competition-units/validate'),
    ({ params }) =>
      HttpResponse.json({
        violations: state.heats
          .filter((h) => h.eventDisciplineId === params.id)
          .flatMap((h) => {
            const list: Violation[] = []
            if (h.entries.length < 3)
              list.push({
                code: 'SMALL_GROUP',
                severity: 'WARNING',
                message: `В заплыве ${h.sequenceNumber} меньше трёх участников.`,
                eventDisciplineId: h.eventDisciplineId,
                heatNumber: h.sequenceNumber ?? undefined,
              })
            if (
              h.entries.some(
                (e) =>
                  state.registrations.find((r) => r.id === e.registrationId)
                    ?.status === 'WITHDRAWN',
              )
            )
              list.push({
                code: 'WITHDRAWN_ENTRY',
                severity: 'ERROR',
                message: `В заплыве ${h.sequenceNumber} есть снятая заявка. Сформируйте заплывы заново.`,
                eventDisciplineId: h.eventDisciplineId,
                heatNumber: h.sequenceNumber ?? undefined,
              })
            return list
          }),
      }),
  ),
  http.patch(
    path('/competition-unit-entries/:id'),
    async ({ params, request }) => {
      const h = state.heats.find((h) =>
        h.entries.some((e) => e.entryId === params.id),
      )
      const entry = h?.entries.find((e) => e.entryId === params.id)
      if (!h || !entry) return notFound()
      const body = (await request.json()) as { laneOrPosition: number }
      if (
        h.entries.some(
          (e) =>
            e.entryId !== entry.entryId &&
            e.laneOrPosition === body.laneOrPosition,
        )
      )
        return error(
          409,
          'scheduling.lane_conflict',
          'Дорожка уже занята. Выберите свободную дорожку.',
        )
      entry.laneOrPosition = body.laneOrPosition
      return new HttpResponse(null, { status: 204 })
    },
  ),
  http.post(path('/events/:id/competition-units/publish'), () => {
    state.heats.forEach((h) => {
      h.status = 'PUBLISHED'
    })
    return new HttpResponse(null, { status: 204 })
  }),
  http.post(
    path('/competition-units/:id/publication'),
    async ({ params, request }) => {
      const h = state.heats.find((h) => h.id === params.id)
      if (!h) return notFound()
      const body = (await request.json()) as { published: boolean }
      h.status = body.published ? 'PUBLISHED' : 'DRAFT'
      return new HttpResponse(null, { status: 204 })
    },
  ),
]
function disciplineErrors(body: DisciplineInput, id?: string) {
  const errors: { field: string; message: string }[] = []
  if (!state.templates.some((t) => t.id === body.disciplineTemplateId))
    errors.push({
      field: 'disciplineTemplateId',
      message: 'Выберите шаблон вида.',
    })
  const forbidden = [
    'id',
    'eventId',
    'status',
    'settingsJson',
    'competitionFormat',
    'unitType',
    'resultType',
    'rankingStrategy',
  ]
  for (const field of forbidden)
    if (field in body)
      errors.push({ field, message: 'Служебное поле не принимается.' })
  if (
    body.code &&
    state.event.disciplines.some((d) => d.code === body.code && d.id !== id)
  )
    errors.push({ field: 'code', message: 'Код вида уже используется.' })
  if (!state.sessions.some((s) => s.id === body.sessionId))
    errors.push({
      field: 'sessionId',
      message: 'Выберите существующую сессию.',
    })
  body.ageGroups.forEach((g, i) => {
    if (
      g.birthYearFrom != null &&
      g.birthYearTo != null &&
      g.birthYearFrom > g.birthYearTo
    )
      errors.push({
        field: `ageGroups[${i}].birthYearTo`,
        message: 'Год окончания должен быть не меньше года начала.',
      })
  })
  return errors
}
