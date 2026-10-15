export type CalendarCard = {
  slug: string; title: string; startDate: string | null; endDate: string | null; city: string | null; venueName: string | null
  organization: { name: string; publicSlug: string | null; hasLogo: boolean } | null
  status: string; registrationOpen: boolean; registrationCloseAt: string | null
  participantsCount: number; disciplinesCount: number; hasResults: boolean
}
export type Calendar = { items: CalendarCard[]; page: number; size: number; total: number; cities: string[] }
export type PublicOrganization = {
  name: string; publicSlug: string; city: string | null; description: string | null
  website: string | null; publicEmail: string | null; publicPhone: string | null; hasLogo: boolean
  stats: { eventsTotal: number; eventsUpcoming: number; participantsTotal: number }
  upcoming: CalendarCard[]; past: CalendarCard[]
}
export type PublicProfile = {
  publicSlug: string | null; description: string | null; website: string | null; publicEmail: string | null
  publicPhone: string | null; isPublic: boolean; hasLogo: boolean
}
