import type { Calendar, PublicOrganization } from '../features/public/calendar.types'
export const demoCalendar: Calendar = {
  items: [{ slug: 'demo', title: 'Осенний кубок SportGate', startDate: '2026-10-15', endDate: '2026-10-15', city: 'Самара', venueName: 'Бассейн «Волна»', organization: { name: 'Клуб Волна', publicSlug: 'volna', hasLogo: false }, status: 'REGISTRATION_OPEN', registrationOpen: true, registrationCloseAt: '2026-10-14T23:59:00+03:00', participantsCount: 200, disciplinesCount: 4, hasResults: false }],
  page: 0, size: 20, total: 1, cities: ['Самара'],
}
export const demoOrganization: PublicOrganization = {
  name: 'Клуб Волна', publicSlug: 'volna', city: 'Самара', description: 'Открытые соревнования по плаванию.', website: null,
  publicEmail: 'club@example.org', publicPhone: null, hasLogo: false,
  stats: { eventsTotal: 1, eventsUpcoming: 1, participantsTotal: 200 }, upcoming: demoCalendar.items, past: [],
}
