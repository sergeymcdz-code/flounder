import { describe, expect, it } from 'vitest';
import { ApiError, type Api, type Row } from './api.ts';
import { accounts } from './bodies.ts';
import { dataset, DEFAULT_OWNER_EMAIL, demoEvents, eventDates, profileKey, selectedEvents, shiftDate } from './plan.ts';
import { seed, type SeedOptions } from './seed.ts';
import { femaleNames, maleNames, surnames } from './names.ts';
import { resetPlan } from './reset.ts';

// Model the existing API state machine; real Excel/API contracts are also checked by test:demo.
class FakeApi implements Api {
  calls: { method: string; path: string; body: unknown; token?: string }[] = [];
  lists = new Map<string, Row[]>([['/sports', [{ id: 'swim', code: 'swim' }]]]);
  users = new Map<string, Row & { roles: string[] }>([['admin@example.invalid', { id: 'admin', email: 'admin@example.invalid', roles: ['platform_admin'] }]]);
  profiles = new Map<string, string>();
  imports = 0;
  missingImport = false;
  missingSessions = false;
  supportsVenueMeta = true;
  lowercaseRegulations = false;
  openRaceStatus: string | undefined;
  closeRaceStatus: string | undefined;
  closeBeforeRead = false;
  wrongPasswordEmail?: string;
  private serial = 0;
  private id() { return `00000000-0000-4000-8000-${String(++this.serial).padStart(12, '0')}`; }
  private add(path: string, data: Record<string, unknown>) {
    const row = { ...data, id: this.id() };
    this.lists.set(path, [...(this.lists.get(path) ?? []), row]);
    return row as Row;
  }
  private registration(event: Row, entry: Awaited<ReturnType<typeof dataset>>['C'][number], status: string) {
    const key = profileKey(entry.profile);
    if (!this.profiles.has(key)) this.profiles.set(key, this.id());
    const discipline = this.lists.get(`/events/${event.id}/disciplines`)!.find(d => d.code === entry.code)!;
    return this.add(`/events/${event.id}/registrations`, { eventId: event.id, eventDisciplineId: discipline.id,
      participantProfileId: this.profiles.get(key), participantSnapshot: entry.profile, seedTimeMs: entry.seedTimeMs, status });
  }
  async request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
    this.calls.push({ method, path, body, token });
    const data = body as Record<string, unknown>;
    const all = () => [...this.lists.values()].flat();
    if (path === '/auth/login') {
      if (!this.users.has(String(data.email)) || data.email === this.wrongPasswordEmail) throw new ApiError(400, 'identity.invalid_credentials', method, path);
      return { accessToken: data.email } as T;
    }
    if (path === '/auth/me') return this.users.get(token!) as T;
    if (path.startsWith('/users?')) return { items: [...this.users.values()] } as T;
    if (path === '/users') {
      const user = { ...data, id: this.id(), roles: ['participant'] };
      this.users.set(String(data.email), user); return user as T;
    }
    if (/\/users\/.*\/roles$/.test(path)) {
      this.users.forEach(user => { if (path === `/users/${user.id}/roles`) user.roles.push(String(data.roleCode)); });
      return undefined as T;
    }
    if (path.endsWith('/sessions') && this.missingSessions) throw new ApiError(404, '', method, path);
    if (path.endsWith('/registrations/import')) {
      if (this.missingImport) throw new ApiError(404, '', method, path);
      this.imports++;
      const event = this.lists.get('/events')!.find(e => e.id === path.split('/')[2])!;
      const spec = demoEvents.find(spec => String(event.title).startsWith(spec.title))!;
      const entries = (await dataset('2026-10-19'))[spec.key].filter(entry => !entry.state);
      let created = 0;
      for (const entry of entries) {
        const rows = this.lists.get(`/events/${event.id}/registrations`) ?? [];
        if (!rows.some(row => profileKey(row.participantSnapshot as never) === profileKey(entry.profile) &&
          this.lists.get(`/events/${event.id}/disciplines`)!.find(d => d.id === row.eventDisciplineId)?.code === entry.code)) {
          this.registration(event, entry, 'CONFIRMED'); created++;
        }
      }
      return { total: entries.length, created, updated: entries.length - created, skipped: 0, errors: [] } as T;
    }
    if (path.startsWith('/public/events/') && path.endsWith('/registrations')) {
      const event = this.lists.get('/events')!.find(e => e.publicSlug === path.split('/')[3])!;
      const item = (data.items as { eventDisciplineId: string; seedTimeMs: number }[])[0];
      const discipline = this.lists.get(`/events/${event.id}/disciplines`)!.find(d => d.id === item.eventDisciplineId)!;
      const rows = this.lists.get(`/events/${event.id}/registrations`) ?? [];
      const status = rows.filter(row => row.eventDisciplineId === discipline.id && row.status !== 'NEEDS_CORRECTION').length >= 16 ? 'WAITLISTED' : 'SUBMITTED';
      const registration = this.registration(event, { profile: data.profile as never, code: String(discipline.code), seedTimeMs: item.seedTimeMs }, status);
      registration.participantUserId = this.users.get(token!)!.id;
      return [registration] as T;
    }
    if (path.endsWith('/review')) {
      const row = all().find(row => row.id === path.split('/')[2])!;
      row.status = data.decision; return row as T;
    }
    if (path.includes('/documents')) {
      const base = path.split('?')[0];
      if (method === 'GET') return (this.lists.get(base) ?? []) as T;
      return this.add(base, { type: 'MEDICAL_CERT', originalName: 'sportgate-demo-medical.pdf' }) as T;
    }
    if (path.endsWith('/generate-competition-units')) {
      const discipline = all().find(row => row.id === path.split('/')[2])!;
      const rows = (this.lists.get(`/events/${discipline.eventId}/registrations`) ?? []).filter(row => row.eventDisciplineId === discipline.id);
      for (let i = 0; i < rows.length; i += 8) {
        this.add(`/event-disciplines/${discipline.id}/competition-units`, { eventDisciplineId: discipline.id, sequenceNumber: i / 8 + 1, status: 'DRAFT',
          scheduledAt: `${String(this.lists.get('/events')!.find(e => e.id === discipline.eventId)!.eventStartAt).slice(0, 10)}T${discipline.code?.toString().includes('-50-') ? '10' : '15'}:${String(i).padStart(2, '0')}:00+04:00`,
          entries: rows.slice(i, i + 8).map(row => ({ entryId: this.id(), registrationId: row.id })) });
      }
      return {} as T;
    }
    if (path.endsWith('/schedule/recalculate')) return {} as T;
    if (path.endsWith('/competition-units/publish')) {
      const eventId = path.split('/')[2];
      const disciplines = this.lists.get(`/events/${eventId}/disciplines`)!;
      disciplines.forEach(d => this.lists.get(`/event-disciplines/${d.id}/competition-units`)?.forEach(unit => { unit.status = 'PUBLISHED'; }));
      return { violations: [] } as T;
    }
    if (/^\/competition-units\//.test(path)) {
      const unit = all().find(row => row.id === path.split('/')[2])!;
      if (method === 'GET') return unit as T;
      if (path.endsWith('/start')) unit.status = 'IN_PROGRESS';
      if (path.endsWith('/finish') || path.endsWith('/publication')) unit.status = 'FINISHED';
      if (path.endsWith('/results')) {
        for (const result of body as { competitionUnitEntryId: string; status: string; timeMs: number | null }[]) {
          const entry = (unit.entries as Row[]).find(e => e.entryId === result.competitionUnitEntryId)!;
          if (unit.status === 'FINISHED' && token === 'judge@flounder.demo') throw new ApiError(403, '', method, path);
          entry.resultStatus = unit.status === 'FINISHED' ? 'CORRECTED' : result.status;
          entry.timeMs = result.timeMs;
        }
      }
      return unit as T;
    }
    if (/\/(publish|open-registration|close-registration|complete)$/.test(path)) {
      const row = all().find(row => row.id === path.split('/').at(-2))!;
      if (path.endsWith('open-registration') && this.openRaceStatus) {
        row.status = this.openRaceStatus; throw new ApiError(400, 'events.invalid_transition', method, path);
      }
      if (path.endsWith('close-registration') && this.closeRaceStatus) {
        row.status = this.closeRaceStatus; throw new ApiError(400, 'events.invalid_transition', method, path);
      }
      row.status = path.endsWith('open-registration') ? 'REGISTRATION_OPEN' : path.endsWith('close-registration') ? 'REGISTRATION_CLOSED' : path.endsWith('complete') ? 'COMPLETED' :
        this.lowercaseRegulations && path.startsWith('/regulation-versions/') ? 'published' : 'PUBLISHED';
      return row as T;
    }
    if (path.startsWith('/regulation-templates/') && path.endsWith('/versions'))
      return this.add('/regulation-versions', { ...data, regulationTemplateId: path.split('/')[2], status: this.lowercaseRegulations ? 'draft' : 'DRAFT' }) as T;
    if (method === 'GET' && /^\/(venues|organizations|events)\/[^/]+$/.test(path)) {
      const row = all().find(row => row.id === path.split('/')[2])!;
      if (this.closeBeforeRead && path.startsWith('/events/') && row.status === 'REGISTRATION_OPEN' && this.imports) row.status = 'REGISTRATION_CLOSED';
      return row as T;
    }
    if (method === 'GET') return (this.lists.get(path) ?? []) as T;
    if (method === 'POST') {
      if (path === '/venues' && !this.supportsVenueMeta) { const { venueMeta: _meta, ...rest } = data; return this.add(path, rest) as T; }
      if (/\/events\/[^/]+\/disciplines$/.test(path)) {
        const event = this.lists.get('/events')!.find(e => e.id === path.split('/')[2])!;
        if (!['DRAFT', 'PUBLISHED'].includes(String(event.status))) throw new ApiError(400, 'competition.event_not_editable', method, path);
      }
      const spec = path === '/events' ? demoEvents.find(s => String(data.title).startsWith(s.title)) : undefined;
      return this.add(path, { ...data, ...(path.endsWith('/disciplines') ? { eventId: path.split('/')[2] } : {}), status: 'DRAFT', ...(spec ? { publicSlug: spec.slug + (String(data.title).includes('резерв') ? '-backup' : '') } : {}) }) as T;
    }
    throw new Error(`Unimplemented test route: ${method} ${path}`);
  }
}
const options: SeedOptions = { adminEmail: 'admin@example.invalid', adminPassword: 'unit-test-password',
  passwords: Object.fromEntries(accounts.map(account => [account.key, 'unit-test-password'])), demoDate: '2026-10-19',
  now: new Date('2026-10-14T12:00:00Z'), workbook: new Uint8Array([80, 75]), skipImport: false };
const quiet = () => {};
describe('dates and profile plan', () => {
  it('accepts Monday demo dates and uses Samara 10:00 for all three starts', () => {
    expect(demoEvents.map(event => eventDates('2026-10-19', event).date)).toEqual(['2026-10-11', '2026-10-20', '2026-10-24']);
    for (const event of demoEvents) expect(eventDates('2026-10-19', event).eventStartAt).toMatch(/T10:00:00\+04:00$/);
  });
  it('crosses month/year/leap-day boundaries without using the host timezone', () => {
    expect(shiftDate('2027-01-03', -8)).toBe('2026-12-26');
    expect(shiftDate('2028-02-28', 1)).toBe('2028-02-29');
    expect(shiftDate('2026-12-30', 5)).toBe('2027-01-04');
    expect(() => shiftDate('2026-02-29', 1)).toThrow();
  });
  it('validates event selectors before making requests', () => {
    expect(selectedEvents('C,A').map(e => e.key)).toEqual(['A', 'C']);
    for (const value of ['', 'A,A', 'D', 'A,']) expect(() => selectedEvents(value)).toThrow('DEMO_EVENTS');
  });
  it('has 300 unique swimmers, 40 shared B/C profiles, 12 clubs and no accidental A overlap', async () => {
    const data = await dataset('2026-10-19');
    const keys = (entries: typeof data.A) => new Set(entries.map(entry => profileKey(entry.profile)));
    const a = keys(data.A), b = keys(data.B), c = keys(data.C);
    expect([data.A.length, data.B.length, data.C.length]).toEqual([160, 200, 124]);
    expect(new Set([...a, ...b, ...c]).size).toBe(300);
    expect([...c].filter(key => b.has(key))).toHaveLength(40);
    expect([...a].filter(key => b.has(key) || c.has(key))).toEqual([]);
    expect(new Set(Object.values(data).flat().map(entry => entry.profile.club)).size).toBe(12);
    expect(new Set(Object.values(data).flat().map(entry => Number(entry.profile.birthDate.slice(0, 4))))).toEqual(new Set([2010, 2011, 2012, 2013, 2014, 2015, 2016]));
  });
  it('uses at least 40 first names per gender in A and at least 120 surnames across 300 swimmers', async () => {
    expect(new Set(surnames).size).toBeGreaterThanOrEqual(120);
    expect(new Set(maleNames).size).toBeGreaterThanOrEqual(40);
    expect(new Set(femaleNames).size).toBeGreaterThanOrEqual(40);
    const data = await dataset('2026-10-19');
    for (const gender of ['M', 'F']) {
      expect(new Set(data.A.filter(entry => entry.profile.gender === gender).map(entry => entry.profile.firstName)).size).toBeGreaterThanOrEqual(40);
      for (const { profile } of [...data.A, ...data.C.filter(entry => !data.B.some(b => profileKey(b.profile) === profileKey(entry.profile)))]) {
        const base = profile.gender === 'F' ? profile.lastName.slice(0, -1) : profile.lastName;
        expect(surnames).toContain(base);
      }
    }
    expect(new Set(Object.values(data).flat().map(entry => entry.profile.lastName)).size).toBeGreaterThanOrEqual(120);
    expect(await dataset('2026-10-19')).toEqual(data);
  });
  it('uses the configured owner mailbox and rejects invalid addresses', async () => {
    expect((await dataset('2026-10-19', ' Owner@Example.com ')).C.filter(entry => entry.email === 'owner@example.com')).toHaveLength(1);
    await expect(dataset('2026-10-19', 'invalid')).rejects.toThrow('DEMO_OWNER_EMAIL');
  });
  it('keeps C within discipline capacity and places exactly four overflow entries on M-50', async () => {
    const { C } = await dataset('2026-10-19');
    for (const code of new Set(C.map(entry => entry.code))) expect(C.filter(entry => entry.code === code && !entry.state).length).toBeLessThanOrEqual(16);
    expect(C.filter(entry => entry.state === 'NEEDS_CORRECTION')).toHaveLength(6);
    expect(C.filter(entry => entry.state === 'WAITLISTED').map(entry => entry.code)).toEqual(Array(4).fill('M-50-FREE'));
    expect(C.filter(entry => entry.email === DEFAULT_OWNER_EMAIL)).toHaveLength(1);
  });
});
describe('seed and reset API orchestration', () => {
  it.each([{ roles: ['platform_admin'] }, { roles: ['organizer'] }, { roles: ['participant', 'platform_admin'] }])('rejects an existing owner with roles %j before creating any demo data', async ({ roles }) => {
    const api = new FakeApi(); api.users.set(DEFAULT_OWNER_EMAIL, { id: 'existing-owner', email: DEFAULT_OWNER_EMAIL, roles });
    await expect(seed(api, options, quiet)).rejects.toThrow('Задайте другой DEMO_OWNER_EMAIL');
    expect(api.calls.filter(call => call.method !== 'GET' && call.path !== '/auth/login')).toEqual([]);
    expect(api.calls.some(call => call.path === '/auth/login' && (call.body as Row).email === DEFAULT_OWNER_EMAIL)).toBe(false);
  });
  it('explains an existing participant password mismatch before any data changes', async () => {
    const api = new FakeApi(); api.users.set(DEFAULT_OWNER_EMAIL, { id: 'owner', email: DEFAULT_OWNER_EMAIL, roles: ['participant'] }); api.wrongPasswordEmail = DEFAULT_OWNER_EMAIL;
    await expect(seed(api, options, quiet)).rejects.toThrow('Проверьте PARENT_PASSWORD/DEMO_PASSWORD или задайте другой DEMO_OWNER_EMAIL');
    expect(api.lists.has('/events')).toBe(false);
    expect(api.calls.some(call => call.path.includes('/public/events/'))).toBe(false);
  });
  it('creates missing service accounts before their first login', async () => {
    const api = new FakeApi();
    await seed(api, { ...options, skipImport: true, demoEvents: 'A' }, quiet);
    for (const account of accounts) {
      const creation = api.calls.findIndex(c => c.method === 'POST' && c.path === '/users' && (c.body as Row).email === account.email);
      const firstLogin = api.calls.findIndex(c => c.path === '/auth/login' && (c.body as Row).email === account.email);
      expect(creation).toBeGreaterThanOrEqual(0);
      expect(firstLogin).toBeGreaterThan(creation);
    }
  });
  it('refuses an existing service account password mismatch without attempting creation', async () => {
    const api = new FakeApi();
    api.users.set('org@flounder.demo', { id: 'org', email: 'org@flounder.demo', roles: ['organizer'] });
    api.wrongPasswordEmail = 'org@flounder.demo';
    await expect(seed(api, options, quiet)).rejects.toThrow('ORG_PASSWORD');
    expect(api.calls.filter(c => c.method === 'POST' && c.path === '/users')).toEqual([]);
    expect(api.lists.get('/events')).toBeUndefined();
  });
  it('uses a separate configured owner while the original owner is an administrator', async () => {
    const api = new FakeApi(); api.users.set('sergeymcdz@yandex.ru', { id: 'real-admin', email: 'sergeymcdz@yandex.ru', roles: ['platform_admin'] });
    await seed(api, { ...options, demoEvents: 'C', ownerEmail: 'owner@example.com' }, quiet);
    expect(api.users.has('owner@example.com')).toBe(true);
    expect(api.calls.some(call => call.path === '/auth/login' && (call.body as Row).email === 'sergeymcdz@yandex.ru')).toBe(false);
  });
  it('accepts a scheduler closure discovered before the close request', async () => {
    const api = new FakeApi(); api.closeBeforeRead = true;
    expect((await seed(api, { ...options, demoEvents: 'A' }, quiet))[0].status).toBe('COMPLETED');
    expect(api.calls.some(call => call.path.endsWith('/close-registration'))).toBe(false);
  });
  it('accepts a scheduler closure racing with the close request', async () => {
    const api = new FakeApi(); api.closeRaceStatus = 'REGISTRATION_CLOSED';
    expect((await seed(api, { ...options, demoEvents: 'A' }, quiet))[0].status).toBe('COMPLETED');
  });
  it('does not swallow a close transition failure in an unexpected state', async () => {
    const api = new FakeApi(); api.closeRaceStatus = 'PUBLISHED';
    await expect(seed(api, { ...options, demoEvents: 'A' }, quiet)).rejects.toMatchObject({ code: 'events.invalid_transition' });
  });
  it('reruns all three completed plans without duplicate entities, imports, generations or documents', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    const sizes = [...api.lists].map(([path, rows]) => [path, rows.length]);
    const before = api.calls.length;
    const events = await seed(api, options, quiet);
    expect(events.map(event => event.status)).toEqual(['COMPLETED', 'REGISTRATION_CLOSED', 'REGISTRATION_OPEN']);
    expect([...api.lists].map(([path, rows]) => [path, rows.length])).toEqual(sizes);
    expect(api.calls.slice(before).filter(call => call.method !== 'GET' && call.path !== '/auth/login')).toEqual([]);
    expect(api.imports).toBe(3);
    expect(api.profiles.size).toBe(300);
    expect([...api.lists].filter(([path]) => /documents$/.test(path))).toHaveLength(2);
  });
  it.each(['A', 'B', 'C'])('seeds only the selected event %s', async key => {
    const api = new FakeApi(); await seed(api, { ...options, demoEvents: key }, quiet);
    expect(api.lists.get('/events')).toHaveLength(1);
    expect(api.lists.get('/events')![0].title).toBe(demoEvents.find(e => e.key === key)!.title);
  });
  it('keeps profiles shared when B and C are seeded in either order', async () => {
    for (const order of [['B', 'C'], ['C', 'B']]) {
      const api = new FakeApi();
      for (const demoEvents of order) await seed(api, { ...options, demoEvents }, quiet);
      expect(api.profiles.size).toBe(204);
    }
  });
  it('uses judge API for initial results and operator API for two published corrections', async () => {
    const api = new FakeApi(); await seed(api, { ...options, demoEvents: 'A' }, quiet);
    const results = api.calls.filter(call => call.path.endsWith('/results') && call.method === 'POST');
    expect(results.filter(call => call.token === 'sec@flounder.demo')).toHaveLength(2);
    expect(results.filter(call => call.token === 'judge@flounder.demo').length).toBeGreaterThan(0);
    expect(api.calls.some(call => call.path.endsWith('/finish'))).toBe(true);
    expect(api.calls.filter(call => call.path.endsWith('/registrations/import')).every(call => call.body instanceof FormData)).toBe(true);
  });
  it('finishes exactly the first two B heats and leaves the others published', async () => {
    const api = new FakeApi(); await seed(api, { ...options, demoEvents: 'B' }, quiet);
    const units = [...api.lists].filter(([path]) => path.endsWith('/competition-units')).flatMap(([, rows]) => rows);
    expect(units.filter(unit => unit.status === 'FINISHED')).toHaveLength(2);
    expect(units.filter(unit => unit.status === 'PUBLISHED').length).toBeGreaterThan(0);
  });
  it.each([false, true])('reuses lowercase published regulations, lowercase create=%s', async lowercaseRegulations => {
    const api = new FakeApi(); api.lowercaseRegulations = lowercaseRegulations;
    await seed(api, { ...options, skipImport: true }, quiet);
    api.lists.get('/regulation-versions')![0].status = 'published';
    await seed(api, { ...options, skipImport: true }, quiet);
    expect(api.calls.filter(c => c.path.startsWith('/regulation-versions/') && c.path.endsWith('/publish'))).toHaveLength(1);
  });
  it('resumes after missing sessions and lets --skip-import bypass import only', async () => {
    const api = new FakeApi(); api.missingSessions = true;
    await expect(seed(api, { ...options, skipImport: true }, quiet)).rejects.toThrow('Эндпоинт не найден');
    api.missingSessions = false; api.missingImport = true;
    await expect(seed(api, options, quiet)).rejects.toThrow('Эндпоинт не найден');
    await seed(api, { ...options, skipImport: true }, quiet);
    expect(api.lists.get('/events')).toHaveLength(3);
    expect(api.imports).toBe(0);
  });
  it('rejects ambiguous demo objects instead of creating another', async () => {
    const api = new FakeApi(); await seed(api, { ...options, skipImport: true }, quiet);
    api.lists.get('/events')!.push({ ...api.lists.get('/events')![0], id: 'duplicate' });
    await expect(seed(api, options, quiet)).rejects.toThrow('несколько объектов');
  });
  it('requires reset for changed DEMO_DATE and never shifts dates from wall clock time', async () => {
    const api = new FakeApi(); await seed(api, options, quiet);
    await seed(api, { ...options, now: new Date('2026-10-20T23:00:00Z') }, quiet);
    expect(api.lists.get('/events')![0].eventStartAt).toBe('2026-10-11T10:00:00+04:00');
    await expect(seed(api, { ...options, demoDate: '2026-10-24' }, quiet)).rejects.toThrow('DEMO_DATE отличается');
  });
  it('continues without venueMeta with a warning and valid membership roles', async () => {
    const api = new FakeApi(); api.supportsVenueMeta = false; const messages: string[] = [];
    await seed(api, { ...options, skipImport: true }, msg => messages.push(msg));
    expect(messages.join()).toContain('WARNING');
    expect(api.calls.filter(c => c.path.endsWith('/members') && c.method === 'POST').map(c => (c.body as Row).memberRole)).toEqual(['owner', 'operator', 'judge']);
  });
  it('stops on missing venue metadata in strict mode', async () => {
    const api = new FakeApi(); api.supportsVenueMeta = false;
    await expect(seed(api, { ...options, requireVenueMeta: true }, quiet)).rejects.toThrow('площадка сохранена без venueMeta');
    expect(api.imports).toBe(0);
  });
  it('rejects an existing venue in a different timezone', async () => {
    const api = new FakeApi(); await seed(api, { ...options, skipImport: true }, quiet);
    api.lists.get('/venues')![0].timezone = 'Europe/Moscow';
    await expect(seed(api, options, quiet)).rejects.toThrow('Europe/Samara');
    expect(api.imports).toBe(0);
  });
  it('always sends supported pool metadata', async () => {
    const api = new FakeApi(); await seed(api, { ...options, requireVenueMeta: true, skipImport: true }, quiet);
    expect(api.calls.find(c => c.path === '/venues' && c.method === 'POST')?.body).toHaveProperty('venueMeta', { lanes: 8, courseLength: 25 });
  });
  it('explains a missing discipline after registration opens before attempting POST', async () => {
    const api = new FakeApi(); await seed(api, { ...options, skipImport: true }, quiet);
    const id = api.lists.get('/events')![0].id; api.lists.get(`/events/${id}/disciplines`)!.pop();
    const before = api.calls.length;
    await expect(seed(api, options, quiet)).rejects.toThrow('Выполните reset');
    expect(api.calls.slice(before).some(c => c.method === 'POST' && c.path.endsWith('/disciplines'))).toBe(false);
  });
  it('reuses equivalent UTC timestamps across the Samara date boundary', async () => {
    const api = new FakeApi(); await seed(api, { ...options, skipImport: true }, quiet);
    api.lists.get('/events')![0].eventStartAt = '2026-10-11T06:00:00Z';
    await seed(api, { ...options, skipImport: true }, quiet);
    expect(api.lists.get('/events')).toHaveLength(3);
  });
  it('creates separate backup titles and reset selects only their slugs', async () => {
    const api = new FakeApi(); await seed(api, { ...options, demoEvents: 'B', skipImport: true }, quiet);
    await seed(api, { ...options, demoEvents: 'B', eventTitleSuffix: ' · резерв', skipImport: true }, quiet);
    const events = api.lists.get('/events')!; expect(events).toHaveLength(2);
    const messages: string[] = [];
    await resetPlan(api, options.adminEmail, options.adminPassword, msg => messages.push(msg), { eventTitleSuffix: ' · резерв', demoSlugs: String(events[1].publicSlug) });
    expect(messages.join()).toContain(events[1].id);
    expect(messages.join()).not.toContain(events[0].id);
  });
  it('requires database URL before --apply can start', async () => {
    const api = new FakeApi(); await expect(resetPlan(api, options.adminEmail, options.adminPassword, quiet, { apply: true })).rejects.toThrow('DEMO_DATABASE_URL');
    expect(api.calls).toHaveLength(0);
  });
  it('prepares all three reset targets without guessing a DELETE route', async () => {
    const api = new FakeApi(); const messages: string[] = [];
    await resetPlan(api, options.adminEmail, options.adminPassword, msg => messages.push(msg));
    expect(messages.join()).toContain('сброс не требуется');
    await seed(api, { ...options, skipImport: true }, quiet);
    await resetPlan(api, options.adminEmail, options.adminPassword, msg => messages.push(msg));
    for (const event of api.lists.get('/events')!) expect(messages.join()).toContain(String(event.publicSlug));
    expect(api.calls.some(c => c.method === 'DELETE')).toBe(false);
  });
  it('continues when the scheduler opens registration concurrently', async () => {
    const api = new FakeApi(); api.openRaceStatus = 'REGISTRATION_OPEN';
    expect((await seed(api, { ...options, demoEvents: 'B' }, quiet))[0].status).toBe('REGISTRATION_CLOSED');
  });
  it('does not swallow a scheduler race that leaves C closed', async () => {
    const api = new FakeApi(); api.openRaceStatus = 'REGISTRATION_CLOSED';
    await expect(seed(api, { ...options, demoEvents: 'C' }, quiet)).rejects.toMatchObject({ code: 'events.invalid_transition' });
    expect(api.imports).toBe(0);
  });
  it('resumes closed B without reopening registration or repeating import', async () => {
    const api = new FakeApi(); await seed(api, { ...options, demoEvents: 'B' }, quiet); const before = api.calls.length;
    await seed(api, { ...options, demoEvents: 'B' }, quiet);
    expect(api.calls.slice(before).some(c => c.path.endsWith('/open-registration'))).toBe(false);
    expect(api.imports).toBe(1);
  });
});
