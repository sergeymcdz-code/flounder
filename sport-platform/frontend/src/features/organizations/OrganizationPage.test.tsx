import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { OrganizationPage } from './OrganizationPage'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost'))
beforeEach(() => {
  localStorage.removeItem('flounder.current-org')
  setAuthSession({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: Date.now() + 60000 })
})
it('sends null when optional organization details are cleared', async () => {
  let body: Record<string, unknown> | undefined
  const organization = { id: 'club', name: 'Клуб Волна', type: 'club', legalName: 'Полное название', inn: '123', contactEmail: 'club@example.org', contactPhone: '123', city: 'Москва' }
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'owner', roles: [], effectiveRoles: ['organizer'], memberships: [{ organizationId: 'club', organizationName: 'Клуб Волна', memberRole: 'owner' }] })),
    http.get('*/api/v1/organizations/club', () => HttpResponse.json(organization)),
    http.get('*/api/v1/organizations/club/members', () => HttpResponse.json([])),
    http.get('*/api/v1/organizations/club/invitations', () => HttpResponse.json([])),
    http.patch('*/api/v1/organizations/club', async ({ request }) => {
      body = await request.json() as Record<string, unknown>
      return HttpResponse.json({ ...organization, ...body })
    }),
  )
  render(<AuthProvider><OrganizationPage /></AuthProvider>)
  await screen.findByRole('button', { name: 'Сохранить реквизиты' })
  for (const label of ['Юридическое название', 'ИНН', 'Контактный email', 'Контактный телефон', 'Город']) {
    await userEvent.clear(screen.getByLabelText(label))
  }
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить реквизиты' }))
  await vi.waitFor(() => expect(body).toEqual({ type: 'club', name: 'Клуб Волна', legalName: null, inn: null, contactEmail: null, contactPhone: null, city: null }))
})
