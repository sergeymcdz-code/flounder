import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, HttpApi } from './api.ts';
import { importBody } from './bodies.ts';

afterEach(() => vi.unstubAllGlobals());
describe('HTTP adapter without network', () => {
  it('encodes JSON, attaches auth and keeps the API prefix once', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"id":"id"}'));
    vi.stubGlobal('fetch', fetch);
    await new HttpApi('http://example.invalid').request('POST', '/organizations', { name: 'demo' }, 'unit-test-token');
    expect(fetch).toHaveBeenCalledWith('http://example.invalid/api/v1/organizations', expect.objectContaining({
      method: 'POST', body: '{"name":"demo"}', headers: { Authorization: 'Bearer unit-test-token', 'Content-Type': 'application/json' }, redirect: 'error',
    }));
  });
  it('leaves multipart boundary to fetch and handles 204', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetch);
    const form = importBody(new Uint8Array([80, 75]));
    expect(await new HttpApi('http://example.invalid').request('POST', '/events/id/registrations/import', form, 'unit-test-token')).toBeUndefined();
    expect(fetch.mock.calls[0][1].body).toBe(form);
    expect(fetch.mock.calls[0][1].headers).not.toHaveProperty('Content-Type');
  });
  it.each([404, 405])('explains missing endpoints even for HTML errors (%s)', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<h1>Not found</h1>', { status })));
    await expect(new HttpApi('http://example.invalid').request('POST', '/events/id/registrations/import'))
      .rejects.toThrow('Эндпоинт не найден');
  });
  it('keeps credential values and server bodies out of errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"code":"identity.invalid_credentials","message":"unit-test-password"}', { status: 400 })));
    await expect(new HttpApi('http://example.invalid').request('POST', '/auth/login')).rejects.toMatchObject({ code: 'identity.invalid_credentials' });
    expect(new ApiError(400, 'identity.invalid_credentials', 'POST', '/auth/login').message).not.toContain('unit-test-password');
  });
});
