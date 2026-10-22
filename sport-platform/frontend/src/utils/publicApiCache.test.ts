import { expect, it } from 'vitest'
import { publicApiCachePattern } from './publicApiCache'
it('never caches participant documents or authenticated API responses', () => {
  const matches = (path: string) => publicApiCachePattern.test(`https://sportgate.pro${path}`)
  expect(matches('/api/v1/public/events/cup/results')).toBe(true)
  expect(matches('/api/v1/public/calendar?city=Самара')).toBe(true)
  expect(matches('/api/v1/public/organizations/club/events?status=past')).toBe(true)
  expect(matches('/api/v1/public/organizations/club')).toBe(true)
  expect(matches('/api/v1/public/organizations/club/logo')).toBe(false)
  expect(matches('/api/v1/public/organizations/club/logo?v=2')).toBe(false)
  expect(matches('/api/v1/organizations/club/logo')).toBe(false)
  expect(matches('/api/v1/public/events/cup/start-list.pdf')).toBe(false)
  expect(matches('/api/v1/public/events/cup/results.pdf?version=2')).toBe(false)
  expect(matches('/api/v1/public/events/cup/results.PDF#page=1')).toBe(false)
  expect(matches('/api/v1/public/documents/private-id/content?expires=1&signature=test')).toBe(false)
  expect(matches('/api/v1/public/documents?anything=1')).toBe(false)
  expect(matches('/api/v1/documents/private-id/download')).toBe(false)
  expect(matches('/api/v1/profiles/child/documents')).toBe(false)
})
