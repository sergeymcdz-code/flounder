import { describe, expect, it } from 'vitest';
import { strFromU8, unzipSync } from 'fflate';
import { ApiError, type Api, type Row } from './api.ts';
import { ORGANIZATION_NAME } from './bodies.ts';
import { calendarPlan, description, venues } from './calendar-plan.ts';
import { profileKey } from './plan.ts';
import { runCalendar, type RunOptions } from './calendar.ts';
import { resetPlan } from './reset.ts';

// Stateful API boundary: enforce lifecycle, capacity, ownership and judge correction rights.
class CalendarApi implements Api {
  calls: { method: string; path: string }[] = [];
  lists = new Map<string, Row[]>([['/sports', [{ id: 'swim', code: 'swim' }]]]);
  users = new Map<string, Row>([['admin@example.invalid', { id: 'admin', email: 'admin@example.invalid', roles: ['platform_admin'] }]]);
  profiles = new Map<string, string>();
  serial = 0;
  failImport = false;
  race = false;
  id() { return `00000000-0000-4000-8000-${String(++this.serial).padStart(12, '0')}`; }
  add(path: string, body: Record<string, unknown>) {
    const row = { id: this.id(), ...body };
    this.lists.set(path, [...(this.lists.get(path) ?? []), row]);
    return row;
  }
  async request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
    this.calls.push({ method, path });
    const data = body as Row;
    const all = () => [...this.lists.values()].flat();
    const row = () => {
      for (const part of path.split(/[/?]/).reverse()) {
        const found = all().find(r => r.id === part);
        if (found) return found;
      }
      return undefined! as Row;
    };
    if (path === '/auth/login') {
      if (!this.users.has(String(data.email))) throw new ApiError(400, 'identity.invalid_credentials', method, path);
      return { accessToken: data.email } as T;
    }
    if (path === '/auth/me') return this.users.get(token!) as T;
    if (path.startsWith('/users?')) return { items: [...this.users.values()] } as T;
    if (path === '/users') {
      const user = { ...data, id: this.id(), profile: data, roles: ['participant'] };
      this.users.set(String(data.email), user); return user as T;
    }
    if (/^\/users\/[^/]+$/.test(path)) return [...this.users.values()].find(u => u.id === path.split('/')[2]) as T;
    if (path.endsWith('/roles')) { const user = [...this.users.values()].find(u => u.id === path.split('/')[2])!; (user.roles as string[]).push(String(data.roleCode)); return undefined as T; }
    if (path.includes('/documents?')) {
      const key = path.split('&')[0];
      if (method === 'GET') return (this.lists.get(key) ?? []) as T;
      return this.add(key, { originalName: 'sportgate-demo-medical.pdf', type: 'MEDICAL_CERT' }) as T;
    }
    if (path.endsWith('/registrations/import')) {
      if (this.failImport) { this.failImport = false; throw new Error('Simulated interruption'); }
      const eventId = path.split('/')[2];
      const file = (body as FormData).get('file') as Blob;
      const xml = strFromU8(unzipSync(new Uint8Array(await file.arrayBuffer()))['xl/worksheets/sheet1.xml']);
      const rows = [...xml.matchAll(/<row[^>]*>(.*?)<\/row>/g)].slice(1).map(match => [...match[1].matchAll(/<t>(.*?)<\/t>/g)].map(x => x[1].replaceAll('&amp;', '&')));
      for (const [lastName, firstName, middleName, birthDate, gender, club, code, time] of rows) {
        const profile = { lastName, firstName, middleName, birthDate, gender, club };
        const discipline = this.lists.get(`/events/${eventId}/disciplines`)!.find(d => d.code === code)!;
        const [minutes, seconds] = time.split(':').map(Number);
        this.register(eventId, discipline.id, profile, minutes * 60_000 + seconds * 1000, 'CONFIRMED');
      }
      return { total: rows.length, created: rows.length, updated: 0, skipped: 0, errors: [] } as T;
    }
    if (path.startsWith('/public/events/')) {
      const event = this.lists.get('/events')!.find(e => e.publicSlug === path.split('/')[3])!;
      if (event.status !== 'REGISTRATION_OPEN') throw new Error('Registration closed');
      const item = (data.items as Row[])[0];
      const profile = data.profile as Parameters<typeof profileKey>[0];
      const key = profileKey(profile);
      const user = this.users.get(token!)!;
      if (!this.profiles.has(key)) this.profiles.set(key, this.id());
      const discipline = all().find(d => d.id === item.eventDisciplineId)!;
      const active = (this.lists.get(`/events/${event.id}/registrations`) ?? []).filter(r => r.eventDisciplineId === discipline.id && ['SUBMITTED', 'CONFIRMED'].includes(String(r.status))).length;
      return [this.register(event.id, discipline.id, profile, Number(item.seedTimeMs), discipline.maxParticipants && active >= Number(discipline.maxParticipants) ? 'WAITLISTED' : 'SUBMITTED', user.id)] as T;
    }
    if (path.endsWith('/review') || path.endsWith('/resubmit')) {
      const registration = row(); registration.status = path.endsWith('/resubmit') ? 'SUBMITTED' : data.decision;
      return registration as T;
    }
    if (path.endsWith('/generate-competition-units')) {
      const discipline = row();
      const registrations = (this.lists.get(`/events/${discipline.eventId}/registrations`) ?? []).filter(r => r.eventDisciplineId === discipline.id && r.status === 'CONFIRMED');
      for (let i = 0; i < registrations.length; i += 8) this.add(`/event-disciplines/${discipline.id}/competition-units`, {
        eventDisciplineId: discipline.id, sequenceNumber: i / 8 + 1, status: 'DRAFT', scheduledAt: `2026-10-19T${discipline.orderInSession === 1 ? '10' : '15'}:00:00Z`,
        entries: registrations.slice(i, i + 8).map((r, j) => ({ entryId: this.id(), registrationId: r.id, laneOrPosition: j + 1 })),
      });
      return undefined as T;
    }
    if (path.endsWith('/schedule/recalculate')) return undefined as T;
    if (path.endsWith('/competition-units/publish')) {
      const ids = (this.lists.get(`/events/${path.split('/')[2]}/disciplines`) ?? []).map(d => d.id);
      for (const unit of all().filter(u => ids.includes(String(u.eventDisciplineId)) && u.entries)) unit.status = 'PUBLISHED';
      return { violations: [] } as T;
    }
    if (path.startsWith('/competition-units/')) {
      const heat = row();
      if (method === 'GET') return heat as T;
      if (path.endsWith('/start')) heat.status = 'IN_PROGRESS';
      if (path.endsWith('/finish')) heat.status = 'FINISHED';
      if (path.endsWith('/results')) for (const result of body as Row[]) {
        if (heat.status === 'FINISHED' && token !== 'sec@flounder.demo') throw new Error('Judge cannot correct');
        Object.assign((heat.entries as Row[]).find(e => e.entryId === result.competitionUnitEntryId)!, {
          resultStatus: heat.status === 'FINISHED' ? 'CORRECTED' : result.status, timeMs: result.timeMs,
        });
      }
      return heat as T;
    }
    if (/\/(publish|open-registration|close-registration|complete)$/.test(path)) {
      const event = row();
      if (path.endsWith('/open-registration') && this.race && event.status === 'PUBLISHED' && String(event.registrationCloseAt) < '2026-10-19') {
        event.status = 'REGISTRATION_CLOSED'; throw new ApiError(400, 'events.invalid_dates', method, path);
      }
      event.status = path.endsWith('/open-registration') ? 'REGISTRATION_OPEN' : path.endsWith('/close-registration') ? 'REGISTRATION_CLOSED' : path.endsWith('/complete') ? 'COMPLETED' : 'PUBLISHED';
      return event as T;
    }
    if (path.startsWith('/regulation-templates/') && path.endsWith('/versions')) return this.add('/regulation-versions', { ...data, regulationTemplateId: path.split('/')[2] }) as T;
    if (method === 'PATCH') { Object.assign(row(), data); return row() as T; }
    if (method === 'GET') return (this.lists.has(path) ? this.lists.get(path) : /\/(members|sessions|disciplines|competition-units|registrations)$/.test(path) ? [] : row() ?? []) as T;
    if (method === 'POST') return this.add(path, { ...data, status: 'DRAFT', ...(path === '/events' ? { publicSlug: 'event-' + this.serial } : {}), ...(path.endsWith('/disciplines') ? { eventId: path.split('/')[2] } : {}) }) as T;
    throw new Error(`Unknown route ${method} ${path}`);
  }
  register(eventId: string, eventDisciplineId: string, profile: Parameters<typeof profileKey>[0], seedTimeMs: number, status: string, participantUserId?: string) {
    const key = profileKey(profile);
    if (!this.profiles.has(key)) this.profiles.set(key, this.id());
    const rows = this.lists.get(`/events/${eventId}/registrations`) ?? [];
    const found = rows.find(r => r.eventDisciplineId === eventDisciplineId && r.participantProfileId === this.profiles.get(key));
    return found ?? this.add(`/events/${eventId}/registrations`, { eventId, eventDisciplineId, participantProfileId: this.profiles.get(key), participantSnapshot: profile, seedTimeMs, status, participantUserId });
  }
}
const options: RunOptions = { enabled: true, adminEmail: 'admin@example.invalid', adminPassword: 'test-only', demoDate: '2026-10-19',
  ownerEmail: 'owner@example.com', passwords: { ORG: 'test-only', SEC: 'test-only', JUDGE: 'test-only', PARENT: 'test-only' } };
const mutations = (api: CalendarApi, since = 0) => api.calls.slice(since).filter(c => c.method !== 'GET' && c.path !== '/auth/login');
describe('calendar API orchestration', () => {
  it('is guarded before HTTP and advance without a calendar is read-only', async () => {
    const api = new CalendarApi();
    await expect(runCalendar(api, { ...options, enabled: false }, () => {})).rejects.toThrow('DEMO_SEED_ENABLED');
    expect(api.calls).toEqual([]);
    expect((await runCalendar(api, { ...options, advance: true }, () => {})).events).toEqual([]);
    expect(mutations(api)).toEqual([]);
  });
  it('creates the full calendar and reruns without writes, duplicated profiles, heats or documents', async () => {
    const api = new CalendarApi();
    const first = await runCalendar(api, options, () => {});
    expect(first.events).toHaveLength(29);
    expect(first.events.filter(e => e.status === 'COMPLETED')).toHaveLength(14);
    expect(first.events.find(e => e.states.WAITLISTED)?.states.WAITLISTED).toBe(4);
    expect(first.events.filter(e => e.status === 'COMPLETED').every(e => e.results === e.registrations)).toBe(true);
    const previous = api.calls.length;
    expect((await runCalendar(api, options, () => {})).events).toEqual(first.events);
    expect(mutations(api, previous)).toEqual([]);
    const next = await runCalendar(api, { ...options, advance: true, demoDate: '2026-10-26' }, () => {});
    expect(next.events.filter(e => e.status === 'COMPLETED')).toHaveLength(15);
    expect(next.events.filter(e => e.status === 'REGISTRATION_OPEN' && e.states.WAITLISTED === 4)).toHaveLength(1);
    for (const event of next.events.filter(e => e.status === 'REGISTRATION_OPEN')) {
      const previous = first.events.find(e => e.slug === event.slug)!;
      expect(event.registrations - previous.registrations).toBeGreaterThanOrEqual(5);
      expect(event.registrations - previous.registrations).toBeLessThanOrEqual(15);
    }
    const seededAgain = api.calls.length;
    expect((await runCalendar(api, options, () => {})).events).toEqual(next.events);
    expect(mutations(api, seededAgain)).toEqual([]);
    const beforeEarlierDate = api.calls.length;
    expect((await runCalendar(api, { ...options, advance: true, demoDate: '2026-10-12' }, () => {})).events).toEqual(next.events);
    expect(mutations(api, beforeEarlierDate)).toEqual([]);
    const advanced = api.calls.length;
    expect((await runCalendar(api, { ...options, advance: true, demoDate: '2026-10-26' }, () => {})).events).toEqual(next.events);
    expect(mutations(api, advanced)).toEqual([]);
  });
  it('resumes a partial import and handles an automatic lifecycle closure racing with open', async () => {
    const api = new CalendarApi(); api.failImport = true;
    await expect(runCalendar(api, { ...options, weeks: 2 }, () => {})).rejects.toThrow('Simulated interruption');
    api.race = true;
    const result = await runCalendar(api, { ...options, weeks: 2 }, () => {});
    expect(result.events).toHaveLength(2);
    expect(api.lists.get('/events')).toHaveLength(2);
  });
  it('rejects changed dates before adding more entries and never touches an S02 event', async () => {
    const api = new CalendarApi(); await runCalendar(api, { ...options, weeks: 1 }, () => {});
    api.lists.get('/events')![0].eventStartAt = '2026-08-02T10:00:00+04:00';
    const before = api.calls.length;
    await expect(runCalendar(api, { ...options, weeks: 1 }, () => {})).rejects.toThrow('reset');
    expect(mutations(api, before)).toEqual([]);
  });
  it('refuses an owner email change or an existing different child without changing profiles', async () => {
    const api = new CalendarApi(); await runCalendar(api, options, () => {});
    let before = api.calls.length;
    await expect(runCalendar(api, { ...options, ownerEmail: 'another@example.com' }, () => {})).rejects.toThrow('DEMO_OWNER_EMAIL');
    expect(mutations(api, before)).toEqual([]);
    (api.users.get(options.ownerEmail!)!.profile as Row).firstName = 'Другой';
    before = api.calls.length;
    await expect(runCalendar(api, options, () => {})).rejects.toThrow('Существующий профиль не меняется');
    expect(mutations(api, before)).toEqual([]);
  });
  it('calendar reset excludes S02 and foreign organizations, limits prefixes and stays read-only', async () => {
    const api = new CalendarApi();
    const org = api.add('/organizations', { name: ORGANIZATION_NAME });
    const foreign = api.add('/organizations', { name: 'Чужая организация' });
    const venue = api.add('/venues', { name: venues[0].name });
    const plan = calendarPlan({ ...options, weeks: 1 })[0];
    const event = api.add('/events', { title: plan.title, description: description(plan), organizationId: org.id, venueId: venue.id, publicSlug: 'calendar-one' });
    api.add('/events', { ...event, id: api.id(), organizationId: foreign.id, publicSlug: 'foreign' });
    api.add('/events', { title: 'Открытый кубок Самары по плаванию', description: 'S02', organizationId: org.id, venueId: venue.id, publicSlug: 's02-b' });
    const messages: string[] = [];
    const before = api.calls.length;
    await resetPlan(api, options.adminEmail, options.adminPassword, x => messages.push(x), { calendar: true });
    expect(messages.find(x => x.startsWith('demo_targets:'))).toContain(event.id);
    expect(messages.find(x => x.startsWith('demo_targets:'))).not.toContain('foreign');
    expect(messages.find(x => x.startsWith('demo_targets:'))).not.toContain('s02-b');
    expect(mutations(api, before)).toEqual([]);
    messages.length = 0;
    await resetPlan(api, options.adminEmail, options.adminPassword, x => messages.push(x), { calendar: true, calendarPrefix: 'Другая копия' });
    expect(messages).toContain('demo_targets: []');
  });
});
