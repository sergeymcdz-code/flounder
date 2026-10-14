import type {
  OrganizerEventDetails,
  OrganizerRegistration,
  Session,
  Heat,
  AgeGroupPreset,
} from '../api/organizer.types'
export const DEMO_EVENT_ID = '11111111-1111-4111-8111-111111111111'
const id = (kind: number, n: number) =>
  `${String(kind).padStart(8, '0')}-1111-4111-8111-${String(n).padStart(12, '0')}`
export const DEMO_ORGANIZATION_ID = id(7, 1)
export function createFixtures() {
  const sessions: Session[] = [
    {
      id: id(2, 1),
      name: 'Утренняя сессия',
      orderNo: 1,
      startsAt: '2026-10-14T10:00:00+03:00',
      breakBetweenEventsSec: 120,
    },
    {
      id: id(2, 2),
      name: 'Дневная сессия',
      orderNo: 2,
      startsAt: '2026-10-14T14:00:00+03:00',
      breakBetweenEventsSec: 180,
    },
  ]
  const presets: AgeGroupPreset[] = [
    {
      code: 'children-two-years',
      name: 'Детские группы · 2 года',
      groups: ['F', 'M'].flatMap((gender) =>
        [2012, 2014].map((year) => ({
          code: `${gender}-${year}-${year + 1}`,
          name: `${gender === 'F' ? 'Девушки' : 'Юноши'} ${year}–${year + 1}`,
          gender,
          birthYearFrom: year,
          birthYearTo: year + 1,
        })),
      ),
    },
  ]
  const event: OrganizerEventDetails = {
    id: DEMO_EVENT_ID,
    organizationId: DEMO_ORGANIZATION_ID,
    sportId: id(8, 1),
    venueId: id(9, 1),
    title: 'Осенний кубок SportGate',
    description: 'Открытые соревнования по плаванию среди юношей и девушек.',
    publicSlug: 'flounder-autumn-2026',
    status: 'DRAFT',
    registrationOpenAt: '2026-10-01T09:00:00+03:00',
    registrationCloseAt: '2026-10-13T18:00:00+03:00',
    eventStartAt: sessions[0].startsAt,
    eventEndAt: '2026-10-14T18:00:00+03:00',
    venue: {
      name: 'Бассейн «Волна»',
      city: 'Москва',
      lanes: 8,
      courseLength: 25,
    },
    disciplines: Array.from({ length: 4 }, (_, i) => {
      const gender = i % 2 === 0 ? 'F' : 'M'
      return {
        id: id(3, i + 1),
        disciplineTemplateId: id(12, 1),
        code: `${gender}-${i < 2 ? 50 : 100}-FREE`,
        name: `${i < 2 ? 50 : 100} м вольный стиль, ${gender === 'F' ? 'девушки' : 'юноши'}`,
        competitionFormat: 'INDIVIDUAL',
        unitType: 'HEAT',
        resultType: 'TIME',
        rankingStrategy: 'ASC',
        participantLimit: null,
        entryFeeAmount: 500,
        entryFeeCurrency: 'RUB',
        settingsJson: null,
        distanceM: i < 2 ? 50 : 100,
        stroke: 'FREE',
        gender,
        seedingMode: 'BY_AGE_GROUP',
        sessionId: sessions[i < 2 ? 0 : 1].id,
        orderInSession: (i % 2) + 1,
        heatDurationSec: null,
        ageGroups: presets[0].groups.filter((g) => g.gender === gender),
        status: 'DRAFT',
        number: i + 1,
      }
    }),
  }
  const surnames = [
    'Иванова',
    'Петров',
    'Соколова',
    'Смирнов',
    'Морозова',
    'Волков',
    'Кузнецова',
    'Орлов',
    'Новикова',
    'Зайцев',
    'Попова',
    'Васильев',
    'Фёдорова',
    'Павлов',
    'Макарова',
  ]
  const registrations: OrganizerRegistration[] = Array.from(
    { length: 30 },
    (_, i) => {
      const d = event.disciplines[i % 4]
      return {
        id: id(4, i + 1),
        eventId: event.id,
        eventDisciplineId: d.id,
        participantUserId: id(5, i + 1),
        participantProfileId: id(6, i + 1),
        participantSnapshot: {
          lastName: surnames[i % surnames.length],
          firstName: d.gender === 'F' ? 'Анна' : 'Александр',
          middleName: null,
          birthDate: `${2012 + (i % 4)}-05-12`,
          gender: d.gender,
          city: 'Москва',
          countryCode: 'RU',
          clubName: ['СШ «Волна»', 'Дельфин', 'Олимп'][i % 3],
          sportMeta: {},
        },
        registrationMeta: {
          emergencyContactName: '',
          emergencyContactPhone: '',
          medicalCertificate: true,
          agreeToRules: true,
        },
        status: 'CONFIRMED',
        seedTimeMs: 32150 + i * 580 + (i % 4 >= 2 ? 30000 : 0),
        paymentStatus: i % 3 === 0 ? 'PAID' : 'PENDING',
        source: i % 3 === 0 ? 'IMPORT' : 'FORM',
        reviewNote: null,
        reviewedByUserId: null,
        reviewedAt: null,
        submittedAt: '2026-10-10T12:00:00+03:00',
        createdAt: '2026-10-10T12:00:00+03:00',
        updatedAt: '2026-10-10T12:00:00+03:00',
      }
    },
  )
  const heats: Heat[] = event.disciplines.flatMap((d, i) => {
    const rows = registrations.filter((r) => r.eventDisciplineId === d.id)
    const chunks = i < 2 ? [rows.slice(0, 4), rows.slice(4)] : [rows]
    return chunks.map((chunk, j) => ({
      id: id(10, i * 10 + j + 1),
      eventDisciplineId: d.id,
      sequenceNumber: j + 1,
      label: `Заплыв ${j + 1}`,
      status: 'DRAFT',
      scheduledAt: `2026-10-14T${i < 2 ? '07' : '11'}:${String((i % 2) * 10 + j * 3).padStart(2, '0')}:00Z`,
      ageGroupCode: d.ageGroups[i < 2 ? 0 : 1].code,
      entries: chunk.map((r, k) => ({
        entryId: id(11, i * 100 + j * 10 + k + 1),
        registrationId: r.id,
        participantName: `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName}`,
        laneOrPosition: [4, 5, 3, 6, 2, 7, 1, 8][k],
        rawValue: i === 2 ? '01:05.12' : null,
        resultStatus: i === 2 ? 'VALID' : 'PENDING',
      })),
    }))
  })
  const templates = [
    {
      id: id(12, 1),
      sportId: event.sportId,
      code: 'SWIM-INDIVIDUAL',
      name: 'Плавание: индивидуальный заплыв',
    },
    {
      id: id(12, 2),
      sportId: event.sportId,
      code: 'SWIM-MEDLEY',
      name: 'Плавание: комплекс',
    },
  ]
  return { event, sessions, registrations, heats, presets, templates }
}
