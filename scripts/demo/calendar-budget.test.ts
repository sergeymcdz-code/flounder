import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpApi } from './api.ts';
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });
describe('calendar HTTP budgets', () => {
  it.each([600_000, 5_400_000])('enforces %i ms without leaking credentials', async budget => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}')));
    const api = new HttpApi('http://example.invalid', budget);
    vi.advanceTimersByTime(budget + 1);
    await expect(api.request('GET', '/events')).rejects.toThrow(`бюджет ${budget / 1000} секунд`);
    expect(fetch).not.toHaveBeenCalled();
  });
});
