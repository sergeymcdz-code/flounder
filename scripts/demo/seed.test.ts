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
  missingSessions = false;
  private serial = 0;
  async request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
    this.calls.push({ method, path, body, token });
    const data = body as Record<string, unknown>;
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
      row.status = path.endsWith('open-registration') ? 'REGISTRATION_OPEN' : 'PUBLISHED';
      return row as T;
    }
    if (path.startsWith('/regulation-templates/') && path.endsWith('/versions') && method === 'POST') {
      const row = { ...data, id: `id-${++this.serial}`, regulationTemplateId: path.split('/')[2], status: 'DRAFT' };
      this.lists.set('/regulation-versions', [...(this.lists.get('/regulation-versions') ?? []), row]);
      return row as T;
    }
    if (method === 'GET' && /^\/(venues|organizations)\/[^/]+$/.test(path)) {
      return this.lists.get(`/${path.split('/')[1]}`)!.find(row => row.id === path.split('/')[2]) as T;
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
});
