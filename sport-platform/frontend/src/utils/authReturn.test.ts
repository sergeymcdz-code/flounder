import { expect, it } from 'vitest'
import { authReturnTarget, roleHomeTarget } from './authReturn'
it.each([
  ['participant', '/my/registrations'], ['organizer', '/organizer/events'], ['operator', '/organizer/events'], ['platform_admin', '/organizer/events'], ['judge', '/operator/results'],
])('lands %s in the role workspace and preserves internal return links', (role, expected) => {
  expect(authReturnTarget('', undefined, [role])).toBe(expected)
  expect(roleHomeTarget([role])).toBe(expected)
  expect(authReturnTarget('?next=%2Fe%2Fcup%2Fregister', '/wrong', [role])).toBe('/e/cup/register')
  expect(authReturnTarget('', '/e/cup/register', [role])).toBe('/e/cup/register')
  for (const next of ['//evil.test', 'https://evil.test', '/\\evil.test', '/\n/evil.test']) expect(authReturnTarget(`?next=${encodeURIComponent(next)}`, undefined, [role])).toBe(expected)
})

it('uses the catalog for guests and prioritizes the manager workspace for combined roles', () => {
  expect(roleHomeTarget()).toBe('/')
  expect(roleHomeTarget(['participant'], false)).toBe('/')
  expect(roleHomeTarget(['judge', 'organizer'])).toBe('/organizer/events')
  expect(roleHomeTarget(['platformadmin'])).toBe('/organizer/events')
})
