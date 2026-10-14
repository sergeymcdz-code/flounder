import { describe, expect, it } from 'vitest';
import { ApiError, type Api, type Row } from './api.ts';
import { accounts } from './bodies.ts';
import { seed, type SeedOptions } from './seed.ts';
import { resetPlan } from './reset.ts';

// An in-memory API double checks orchestration without sockets or backend dependencies.
class FakeApi implements Api {
  calls: { method: string; path: string; body: unknown; token?: string }[] = [];
  lists = new Map<string, Row[]>([['/sports', [{ id: 'swim', code: 'swim' }]]]);
  users = new Map<string, Row & { roles: string[] }>([['admin@example.invalid', { id: 'admin', email: 'admin@example.invalid', roles: ['platform_admin'] }]]);
  imports = 0;
  missingImport = false;
  openRaceStatus: string | undefined;
  missingSessions = false;
  supportsVenueMeta = false;
  lowercaseRegulations = false;
  private serial = 0;
  async request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
    this.calls.push({ method, path, body, token });
    let data = body as Record<string, unknown>;
    if (method === 'POST' && path === '/venues') {
      const allowed = ['name', 'countryCode', 'city', 'address', 'timezone', ...(this.supportsVenueMeta ? ['venueMeta'] : [])];
      // Spring Boot ignores unknown fields on both DTO versions.
      data = Object.fromEntries(Object.entries(data).filter(([key]) => allowed.includes(key)));
    }
    if (method === 'POST' && /^\/organizations\/[^/]+\/members$/.test(path)) {
      if (!['owner', 'admin', 'member', 'organizer', 'operator', 'judge'].includes(String(data.memberRole))) {
        throw new ApiError(400, 'organizations.invalid_member_role', method, path);
      }
    }
    if (method === 'POST' && /^\/events\/[^/]+\/disciplines$/.test(path)) {
      const event = this.lists.get('/events')?.find(row => row.id === path.split('/')[2]);
      if (!event || !['DRAFT', 'PUBLISHED'].includes(String(event.status))) throw new ApiError(400, 'competition.event_not_editable', method, path);
    }
    if (path === '/auth/login') {
      if (!this.users.has(String(data.email))) throw new ApiError(400, 'identity.invalid_credentials', method, path);
      return { accessToken: data.email } as T;
    }
    if (path === '/auth/me') return this.users.get(token!) as T;
    if (path === '/users') {
      const user = { ...data, id: `user-${++this.serial}`, roles: ['participant'] };
      this.users.set(String(data.email), user);
      return user as T;
    }
    if (/\/users\/.*\/roles$/.test(path)) {
      const user = [...this.users.values()].find(user => path === `/users/${user.id}/roles`)!;
      user.roles.push(String(data.roleCode));
      return undefined as T;
    }
    if (path.endsWith('/registrations/import')) {
      if (this.missingImport) throw new ApiError(404, '', method, path);
      const created = this.imports++ === 0 ? 200 : 0;
      return { total: 200, created, updated: 200 - created, skipped: 0, errors: [] } as T;
    }
    if (path.endsWith('/sessions') && this.missingSessions) throw new ApiError(404, '', method, path);
    if (/\/(publish|open-registration)$/.test(path)) {
      const id = path.split('/').at(-2);
      const row = [...this.lists.values()].flat().find(row => row.id === id)!;
      if (this.lowercaseRegulations && path.startsWith('/regulation-versions/')) {
        if (row.status !== 'draft') throw new ApiError(400, 'regulations.invalid_status_transition', method, path);
        row.status = 'published';
        return row as T;
      }
      if (path.endsWith('open-registration') && this.openRaceStatus) {
        row.status = this.openRaceStatus;
        throw new ApiError(400, 'events.invalid_transition', method, path);
      }
      row.status = path.endsWith('open-registration') ? 'REGISTRATION_OPEN' : 'PUBLISHED';
      return row as T;
    }
    if (path.startsWith('/regulation-templates/') && path.endsWith('/versions') && method === 'POST') {
      const row = { ...data, id: `id-${++this.serial}`, regulationTemplateId: path.split('/')[2], status: this.lowercaseRegulations ? 'draft' : 'DRAFT' };
      this.lists.set('/regulation-versions', [...(this.lists.get('/regulation-versions') ?? []), row]);
      return row as T;
    }
    if (method === 'GET' && /^\/(venues|organizations|events)\/[^/]+$/.test(path)) {
      const row = this.lists.get(`/${path.split('/')[1]}`)!.find(row => row.id === path.split('/')[2])!;
      if (path.startsWith('/venues/')) {
        const { venueMeta, ...response } = row;
        return { ...response, ...(this.supportsVenueMeta && venueMeta !== undefined ? { venueMeta } : {}) } as T;
      }
      return row as T;
    }
    const listPath = path === '/organizer/events' ? '/events' : path;
    const list = this.lists.get(listPath) ?? [];
    if (method === 'GET') return list as T;
    if (method === 'POST') {
      const row = { ...data, id: `id-${++this.serial}`, status: 'DRAFT', publicSlug: 'demo-swim' };
      list.push(row);
      this.lists.set(listPath, list);
      return row as T;
    }
    throw new Error(`Unimplemented test route: ${method} ${path}`);
  }
}
const options: SeedOptions = { adminEmail: 'admin@example.invalid', adminPassword: 'unit-test-password',
  passwords: Object.fromEntries(accounts.map(account => [account.key, 'unit-test-password'])),
  now: new Date('2026-10-12T12:00:00Z'), workbook: new Uint8Array([80, 75]), skipImport: false };
const quiet = () => {};
describe('seed and reset orchestration without network', () => {
  it('QA05: reuses a regulation returned as lowercase published by the real API', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    api.lists.get('/regulation-versions')![0].status = 'published';
    const before = api.calls.length;
    await seed(api, { ...options, skipImport: true }, quiet);
    expect(api.calls.slice(before).filter(call => call.method === 'POST' && call.path.startsWith('/regulation-versions/') && call.path.endsWith('/publish'))).toEqual([]);
  });
  it('warns and opens registration when an explicit DEMO_DATE has already closed', async () => {
    const api = new FakeApi();
    const messages: string[] = [];
    await seed(api, { ...options, demoDate: '2026-10-17', now: new Date('2026-10-18T12:00:00Z'), skipImport: true }, message => messages.push(message));
    const event = api.lists.get('/events')![0];
    expect(event.registrationOpenAt).toBe('2026-10-18T11:59:00.000Z');
    expect(event.registrationCloseAt).toBe('2026-10-18T12:01:00.000Z');
    expect(messages.some(message => message.includes('WARNING: плановое закрытие регистрации уже наступило'))).toBe(true);
  });
  it('QA03-05 reuses published regulations returned in lowercase by the backend', async () => {
    const api = new FakeApi();
    api.lowercaseRegulations = true;
    await seed(api, options, quiet);
    const sizes = [...api.lists].map(([path, list]) => [path, list.length]);
    await seed(api, options, quiet);
    expect([...api.lists].map(([path, list]) => [path, list.length])).toEqual(sizes);
    expect(api.calls.filter(call => call.method === 'POST' && call.path.startsWith('/regulation-versions/') && call.path.endsWith('/publish'))).toHaveLength(1);
  });
  it('reruns without duplicate entities or generation, and imports through multipart', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    const sizes = [...api.lists].map(([path, list]) => [path, list.length]);
    await seed(api, options, quiet);
    expect([...api.lists].map(([path, list]) => [path, list.length])).toEqual(sizes);
    expect(api.users.size).toBe(5);
    expect(api.imports).toBe(2);
    expect(api.lists.get('/events')).toHaveLength(1);
    expect(api.calls.some(call => /generate-competition-units|schedule\/recalculate/.test(call.path))).toBe(false);
    expect(api.calls.filter(call => call.path.endsWith('/registrations/import')).every(call => call.body instanceof FormData)).toBe(true);
    expect(api.calls.find(call => call.path === '/events' && call.method === 'POST')?.token).toBe('org@flounder.demo');
  });
  it('resumes after missing T03 and lets --skip-import bypass only import', async () => {
    const api = new FakeApi();
    api.missingSessions = true;
    await expect(seed(api, { ...options, skipImport: true }, quiet)).rejects.toThrow('Эндпоинт не найден');
    api.missingSessions = false;
    api.missingImport = true;
    await expect(seed(api, options, quiet)).rejects.toThrow('--skip-import');
    await seed(api, { ...options, skipImport: true }, quiet);
    expect(api.lists.get('/events')).toHaveLength(1);
    expect(api.imports).toBe(0);
    expect(api.lists.get('/events')![0].status).toBe('REGISTRATION_OPEN');
  });
  it('rejects ambiguous demo objects instead of creating another', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    api.lists.get('/events')!.push({ ...api.lists.get('/events')![0], id: 'duplicate' });
    await expect(seed(api, options, quiet)).rejects.toThrow('несколько объектов');
  });
  it('keeps the existing event date on a later rerun', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    await seed(api, { ...options, now: new Date('2026-10-19T12:00:00Z') }, quiet);
    expect(api.lists.get('/events')![0].eventStartAt).toBe('2026-10-17T10:00:00+04:00');
    await expect(seed(api, { ...options, demoDate: '2026-10-24' }, quiet)).rejects.toThrow('DEMO_DATE отличается');
  });
  it('continues to import without venueMeta, with valid T01 membership roles', async () => {
    const api = new FakeApi();
    const messages: string[] = [];
    await seed(api, options, message => messages.push(message));
    expect(api.imports).toBe(1);
    expect(messages.join()).toContain('WARNING');
    expect(messages.join()).toContain('8 дорожек по умолчанию');
    expect(api.calls.find(call => call.method === 'POST' && call.path === '/venues')?.body).toHaveProperty('venueMeta', { lanes: 8, courseLength: 25 });
    expect(api.calls.filter(call => call.method === 'POST' && call.path.endsWith('/members')).map(call => (call.body as Row).memberRole))
      .toEqual(['owner', 'operator', 'judge']);
  });
  it('checks venue metadata strictly only when requested', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    await expect(seed(api, { ...options, requireVenueMeta: true }, quiet)).rejects.toThrow('площадка сохранена без venueMeta; API обновления площадки нет — задайте параметры в БД или создайте площадку заново');
    expect(api.imports).toBe(1);
    api.supportsVenueMeta = true;
    api.lists.get('/venues')![0].venueMeta = { lanes: 8, courseLength: 25 };
    await seed(api, { ...options, requireVenueMeta: true }, quiet);
    expect(api.imports).toBe(2);
  });
  it.each([false, true])('saves metadata with updated T01, strict=%s', async requireVenueMeta => {
    const api = new FakeApi();
    api.supportsVenueMeta = true;
    const messages: string[] = [];
    await seed(api, { ...options, requireVenueMeta }, message => messages.push(message));
    expect(api.imports).toBe(1);
    expect(messages.join()).not.toContain('WARNING');
    const venue = api.lists.get('/venues')![0];
    expect(await api.request('GET', `/venues/${venue.id}`)).toHaveProperty('venueMeta', { lanes: 8, courseLength: 25 });
    expect(api.calls.find(call => call.method === 'POST' && call.path === '/venues')?.body)
      .toHaveProperty('venueMeta', { lanes: 8, courseLength: 25 });
  });
  it('ignores unknown venue fields but rejects unsupported roles and closed discipline creation', async () => {
    const api = new FakeApi();
    const venue = await api.request<Row>('POST', '/venues', { name: 'test', lanes: 8, venueMeta: { lanes: 8, courseLength: 25 } });
    expect(venue).not.toHaveProperty('lanes');
    expect(venue).not.toHaveProperty('venueMeta');
    expect(await api.request('GET', `/venues/${venue.id}`)).not.toHaveProperty('venueMeta');
    await expect(api.request('POST', '/organizations/org/members', { userId: 'user', memberRole: 'staff' }))
      .rejects.toMatchObject({ code: 'organizations.invalid_member_role' });
    for (const status of ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'COMPLETED']) {
      api.lists.set('/events', [{ id: 'event', status }]);
      await expect(api.request('POST', '/events/event/disciplines', { code: 'M-50-FREE' }))
        .rejects.toMatchObject({ code: 'competition.event_not_editable' });
    }
  });
  it('explains a missing discipline while registration is open before POST', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    const id = api.lists.get('/events')![0].id;
    api.lists.get(`/events/${id}/disciplines`)!.pop();
    const before = api.calls.length;
    await expect(seed(api, options, quiet)).rejects.toThrow('выполните reset');
    expect(api.calls.slice(before).some(call => call.method === 'POST' && call.path.endsWith('/disciplines'))).toBe(false);
  });
  it('converts a UTC start across midnight to the Samara date', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    api.lists.get('/events')![0].eventStartAt = '2026-10-16T22:00:00Z';
    await seed(api, options, quiet);
    expect(api.imports).toBe(2);
  });
  it('creates and reuses a separate backup event selected by the title suffix', async () => {
    const api = new FakeApi();
    await seed(api, options, quiet);
    await seed(api, { ...options, eventTitleSuffix: ' · резерв' }, quiet);
    await seed(api, { ...options, eventTitleSuffix: ' · резерв' }, quiet);
    const events = api.lists.get('/events')!;
    expect(events).toHaveLength(2);
    expect(events[1].title).toBe(`${events[0].title} · резерв`);
    events[1].id = '11111111-2222-3333-4444-555555555555';
    const messages: string[] = [];
    await resetPlan(api, options.adminEmail, options.adminPassword, message => messages.push(message), { eventTitleSuffix: ' · резерв' });
    expect(messages.join()).toContain('demo_event_title=');
    expect(messages.join()).toContain(' · резерв');
    expect(messages.join()).toContain('demo_event_id=11111111-2222-3333-4444-555555555555');
  });
  it('requires the database URL before --apply can start', async () => {
    const api = new FakeApi();
    await expect(resetPlan(api, options.adminEmail, options.adminPassword, quiet, { apply: true })).rejects.toThrow('DEMO_DATABASE_URL');
    expect(api.calls).toHaveLength(0);
  });
  it('prepares a scoped SQL reset without guessing a DELETE route', async () => {
    const api = new FakeApi();
    const messages: string[] = [];
    await resetPlan(api, options.adminEmail, options.adminPassword, message => messages.push(message));
    expect(messages.join()).toContain('сброс не требуется');
    await seed(api, options, quiet);
    api.lists.get('/events')![0].id = '11111111-2222-3333-4444-555555555555';
    await resetPlan(api, options.adminEmail, options.adminPassword, message => messages.push(message));
    expect(messages.join()).toContain('-v demo_event_id=11111111-2222-3333-4444-555555555555');
    expect(api.calls.some(call => call.method === 'DELETE')).toBe(false);
  });
  it('continues after the scheduler opens registration between publish and open', async () => {
    const api = new FakeApi(); api.openRaceStatus = 'REGISTRATION_OPEN';
    expect((await seed(api, options, quiet)).status).toBe('REGISTRATION_OPEN');
    expect(api.imports).toBe(1);
    expect(api.calls.some(c => c.method === 'GET' && /^\/events\/[^/]+$/.test(c.path))).toBe(true);
  });
  it('does not swallow an invalid transition when registration was not opened', async () => {
    const api = new FakeApi(); api.openRaceStatus = 'REGISTRATION_CLOSED';
    await expect(seed(api, options, quiet)).rejects.toMatchObject({ code: 'events.invalid_transition' });
    expect(api.imports).toBe(0);
  });
  it('reruns a closed event with a warning without reopening registration', async () => {
    const api = new FakeApi(); await seed(api, options, quiet);
    api.lists.get('/events')![0].status = 'REGISTRATION_CLOSED';
    const messages: string[] = []; const before = api.calls.length;
    expect((await seed(api, options, m => messages.push(m))).status).toBe('REGISTRATION_CLOSED');
    expect(api.imports).toBe(2);
    expect(messages.join()).toContain('WARNING: регистрация закрыта');
    expect(api.calls.slice(before).some(c => c.path.endsWith('/open-registration'))).toBe(false);
  });

});
