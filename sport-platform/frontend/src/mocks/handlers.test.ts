import { beforeEach, expect, it } from 'vitest'
import { adminHandlers, resetAdminMocks } from './admin-handlers'
import { handlers } from './handlers'
import { DEMO_EVENT_ID, DEMO_ORGANIZATION_ID } from './fixtures'
import { server } from '../test/setup'

const headers = (role: string) => ({ Authorization: `Bearer demo.${btoa(JSON.stringify({ roles: [role] }))}.demo` })
beforeEach(() => {
  resetAdminMocks()
  server.use(...adminHandlers, ...handlers)
})

it('returns the same fixture events for admin and organizer lists', async () => {
  const adminResponse = await fetch('http://localhost/api/v1/events', { headers: headers('platform_admin') })
  const organizerResponse = await fetch('http://localhost/api/v1/organizer/events', { headers: headers('organizer') })
  expect(adminResponse.status).toBe(200)
  expect(organizerResponse.status).toBe(200)
  const events = await adminResponse.json()
  expect(events).toEqual(await organizerResponse.json())
  expect(events).toEqual([expect.objectContaining({ id: DEMO_EVENT_ID, organizationId: DEMO_ORGANIZATION_ID })])
  const organizations = await fetch('http://localhost/api/v1/organizations', { headers: headers('platform_admin') })
  expect(await organizations.json()).toContainEqual(expect.objectContaining({ id: events[0].organizationId }))
})

it.each([
  [undefined, 401],
  ['participant', 403],
  ['organizer', 403],
] as const)('keeps the admin event list restricted for %s', async (role, status) => {
  const response = await fetch('http://localhost/api/v1/events', { headers: role ? headers(role) : undefined })
  expect(response.status).toBe(status)
})
