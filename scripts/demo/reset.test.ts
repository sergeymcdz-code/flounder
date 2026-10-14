import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applyReset, resetPlan } from './reset.ts';
import { ORGANIZATION_NAME, VENUE_NAME } from './bodies.ts';
import type { Api } from './api.ts';
import { demoEvents } from './plan.ts';
const title = demoEvents[1].title + ' · резерв';
const targets = [{ id: '11111111-2222-3333-4444-555555555555', title, slug: 'demo-backup' }];

vi.mock('node:child_process', () => ({ execFile: vi.fn() }));
beforeEach(() => vi.clearAllMocks());
const eventId = '11111111-2222-3333-4444-555555555555';
const databaseUrl = 'postgresql://test:test-only-password@localhost/demo';
const fakeApi: Api = {
  async request<T>(_method: string, path: string): Promise<T> {
    const data = path === '/auth/login' ? { accessToken: 'unit-test-token' }
      : path === '/events' ? [{ id: eventId, title: title, publicSlug: 'demo-backup', organizationId: 'org', venueId: 'venue' }]
      : path.startsWith('/organizations/') ? { id: 'org', name: ORGANIZATION_NAME }
      : { id: 'venue', name: VENUE_NAME };
    return data as T;
  },
};
// The mock completes execFile callbacks without opening sockets or starting psql.
function processResult(error: unknown, stdout = '', stderr = '') {
  vi.mocked(execFile).mockImplementation((...args: unknown[]) => {
    const callback = args.at(-1) as (error: unknown, stdout: string, stderr: string) => void;
    callback(error, stdout, stderr);
    return {} as ReturnType<typeof execFile>;
  });
}
describe('explicit SQL reset without a process or database', () => {
  it('passes UUID and exact title as separate arguments and prints psql output', async () => {
    processResult(null, 'BEGIN\nDELETE 1\nCOMMIT\n');
    const messages: string[] = [];
    await resetPlan(fakeApi, 'admin@example.invalid', 'unit-test-password', message => messages.push(message),
      { apply: true, databaseUrl, eventTitleSuffix: ' · резерв' });
    expect(execFile).toHaveBeenCalledWith('psql', [
      '-v', 'ON_ERROR_STOP=1', '-v', `demo_targets=${JSON.stringify(targets)}`,
      '-f', fileURLToPath(new URL('./reset.sql', import.meta.url)),
    ], expect.objectContaining({ env: expect.objectContaining({ PGDATABASE: databaseUrl }) }), expect.any(Function));
    expect(messages.join()).toContain('COMMIT');
    expect(messages.join()).not.toContain('test-only-password');
  });
  it('keeps plan mode read-only', async () => {
    const messages: string[] = [];
    await resetPlan(fakeApi, 'admin@example.invalid', 'unit-test-password', message => messages.push(message), { eventTitleSuffix: ' · резерв' });
    expect(execFile).not.toHaveBeenCalled();
    expect(messages.join()).toContain('данные ещё не удалены');
  });
  it('redacts connection values from successful process output', async () => {
    processResult(null, databaseUrl, 'test-only-password');
    const output = await applyReset(targets, databaseUrl);
    expect(output).not.toContain(databaseUrl);
    expect(output).not.toContain('test-only-password');
  });
  it('propagates a failed process without printing database credentials', async () => {
    processResult({ code: 'ENOENT', message: databaseUrl }, '', databaseUrl);
    await expect(applyReset(targets, databaseUrl)).rejects.toThrow('ENOENT');
    await expect(applyReset(targets, databaseUrl)).rejects.not.toThrow('test-only-password');
  });
});
