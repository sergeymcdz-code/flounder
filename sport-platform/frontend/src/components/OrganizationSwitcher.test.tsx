import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../test/setup'
import { AuthProvider, useAuth } from '../app/providers/AuthProvider'
import { setAuthSession } from '../api/auth-session'
import { OrganizationSwitcher } from './OrganizationSwitcher'
import { AppLayout } from './AppLayout'
import { OrganizerEventsPage } from '../features/organizer/OrganizerEventsPage'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost'))
const memberships = [
  { organizationId: 'one', organizationName: 'Первый клуб', memberRole: 'operator' },
  { organizationId: 'two', organizationName: 'Второй клуб', memberRole: 'owner' },
]
beforeEach(() => {
  localStorage.removeItem('flounder.current-org')
  setAuthSession({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: Date.now() + 60000 })
})
it('uses effective membership roles for navigation without global staff roles', async () => {
  server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'user', roles: ['participant'], effectiveRoles: ['operator', 'participant'], memberships })))
  render(<MemoryRouter><AuthProvider><AppLayout /></AuthProvider></MemoryRouter>)
  expect(await screen.findByRole('link', { name: 'Мои соревнования' })).toHaveAttribute('href', '/organizer/events')
  expect(screen.getByRole('link', { name: 'На главную' })).toHaveAttribute('href', '/organizer/events')
})
it('filters events and creation rights when switching and persists the selected organization', async () => {
  const organizations: (string | null)[] = []
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'user', roles: [], effectiveRoles: ['operator', 'organizer'], memberships })),
    http.get('*/api/v1/organizer/events', ({ request }) => {
      const id = new URL(request.url).searchParams.get('organizationId')
      organizations.push(id)
      return HttpResponse.json([{ id: id ?? 'all', title: id === 'two' ? 'Второй старт' : 'Первый старт', status: 'DRAFT', eventStartAt: '2030-01-01T10:00:00Z' }])
    }),
  )
  render(<MemoryRouter initialEntries={['/organizer/events?all=1']}><AuthProvider><OrganizationSwitcher /><OrganizerEventsPage /></AuthProvider></MemoryRouter>)
  await screen.findByRole('combobox', { name: 'Организация' })
  await vi.waitFor(() => expect(organizations).toContain('one'))
  expect(screen.queryByRole('link', { name: 'Создать соревнование' })).not.toBeInTheDocument()
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Организация' }), 'two')
  expect(await screen.findByText('Второй старт')).toBeInTheDocument()
  expect(organizations.at(-1)).toBe('two')
  expect(localStorage.getItem('flounder.current-org')).toBe('two')
  expect(screen.getByRole('link', { name: 'Создать соревнование' })).toBeInTheDocument()
})
it('restores a valid selection and shows a single organization as text', async () => {
  localStorage.setItem('flounder.current-org', 'removed-organization')
  server.use(http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'user', roles: [], effectiveRoles: ['operator'], memberships: memberships.slice(0, 1) })))
  render(<AuthProvider><OrganizationSwitcher /></AuthProvider>)
  expect(await screen.findByText('Первый клуб')).toBeInTheDocument()
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
})

it('shows all events for a platform administrator even with a selected membership', async () => {
  const filters: (string | null)[] = []
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'admin', roles: ['platform_admin'], effectiveRoles: ['platform_admin'], memberships })),
    http.get('*/api/v1/organizer/events', ({ request }) => {
      const organization = new URL(request.url).searchParams.get('organizationId')
      filters.push(organization)
      const events = [
        { id: 'one', title: 'Первый старт', organizationId: 'one', status: 'DRAFT', eventStartAt: '2030-01-01T10:00:00Z' },
        { id: 'two', title: 'Второй старт', organizationId: 'two', status: 'DRAFT', eventStartAt: '2030-01-01T10:00:00Z' },
      ]
      return HttpResponse.json(organization ? events.filter(event => event.organizationId === organization) : events)
    }),
  )
  function ReadyEvents() {
    const { authLoading } = useAuth()
    return authLoading ? null : <><OrganizationSwitcher /><OrganizerEventsPage /></>
  }
  render(<MemoryRouter><AuthProvider><ReadyEvents /></AuthProvider></MemoryRouter>)
  const selector = await screen.findByRole('combobox', { name: 'Организация' })
  await vi.waitFor(() => {
    expect(screen.queryByText('Загружаем соревнования…')).not.toBeInTheDocument()
    expect(screen.getByText('Первый старт')).toBeInTheDocument()
    expect(screen.getByText('Второй старт')).toBeInTheDocument()
  })
  await userEvent.selectOptions(selector, 'two')
  await vi.waitFor(() => {
    expect(screen.queryByText('Загружаем соревнования…')).not.toBeInTheDocument()
    expect(screen.getByText('Первый старт')).toBeInTheDocument()
    expect(screen.getByText('Второй старт')).toBeInTheDocument()
  })
  expect(filters.every(filter => filter === null)).toBe(true)
})

it('returns effective organization roles at login for the D03 home redirect', async () => {
  setAuthSession(null)
  server.use(
    http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: 'membership-token', refreshToken: 'refresh', tokenType: 'Bearer', expiresIn: 3600 })),
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ id: 'secretary', roles: ['participant'], effectiveRoles: ['participant', 'operator'], memberships })),
  )
  const loggedIn = vi.fn()
  function LoginProbe() {
    const { login } = useAuth()
    return <button onClick={() => { void login({ email: 'secretary@example.com', password: 'test-password' }).then(loggedIn) }}>Вход</button>
  }
  render(<MemoryRouter><AuthProvider><LoginProbe /><AppLayout /></AuthProvider></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Вход' }))
  await vi.waitFor(() => expect(loggedIn).toHaveBeenCalledWith(expect.objectContaining({ roles: ['participant', 'operator'] })))
  expect(screen.getByRole('link', { name: 'На главную' })).toHaveAttribute('href', '/organizer/events')
  expect(screen.getByRole('link', { name: 'Мои соревнования' })).toBeInTheDocument()
})
