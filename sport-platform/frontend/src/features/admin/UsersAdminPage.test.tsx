import { MemoryRouter } from 'react-router'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { UsersAdminPage } from './UsersAdminPage'
import { server } from '../../test/setup'
import { adminHandlers, resetAdminMocks } from '../../mocks/admin-handlers'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: {
  accessToken: `demo.${btoa(JSON.stringify({ roles: ['platform_admin'] }))}.demo`,
} }) }))
beforeEach(() => {
  resetAdminMocks(); server.use(...adminHandlers)
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})
it('searches by name after the debounce and shows the empty state', async () => {
  const queries: string[] = []
  server.use(http.get('*/api/v1/users', ({ request }) => {
    const query = new URL(request.url).searchParams.get('query') ?? ''
    queries.push(query)
    return HttpResponse.json({ items: [], page: 0, size: 20, total: 0 })
  }))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  await screen.findByText(/Пользователи не найдены — измените запрос/)
  await userEvent.type(screen.getByLabelText('Поиск пользователей'), 'Иванова')
  expect(queries).toEqual([''])
  await waitFor(() => expect(queries).toEqual(['', 'Иванова']))
})
it('assigns a role and refreshes the user badges', async () => {
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('user@flounder.demo')).closest('tr')!
  await userEvent.click(within(row).getByRole('button', { name: 'Роли' }))
  const dialog = screen.getByRole('dialog', { name: 'Роли пользователя' })
  await userEvent.click(within(dialog).getByLabelText('Судья'))
  await userEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await waitFor(() => expect(within(screen.getByText('user@flounder.demo').closest('tr')!).getByText('Судья')).toBeInTheDocument())
})
it('keeps an API 403 message under the role form', async () => {
  server.use(http.post('*/api/v1/users/:id/roles', () => HttpResponse.json({ code: 'identity.access_denied', message: 'Доступ к назначению ролей запрещён' }, { status: 403 })))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('user@flounder.demo')).closest('tr')!
  await userEvent.click(within(row).getByRole('button', { name: 'Роли' }))
  await userEvent.click(screen.getByLabelText('Судья'))
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Доступ к назначению ролей запрещён')
  expect(screen.getByRole('dialog')).toBeInTheDocument()
})
it('shows 403 when the user list is forbidden', async () => {
  server.use(http.get('*/api/v1/users', () => HttpResponse.json({ message: 'Доступ запрещён' }, { status: 403 })))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Доступ запрещён')
})
it('requires explicit confirmation before removing platform_admin', async () => {
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('admin@flounder.demo')).closest('tr')!
  await userEvent.click(within(row).getByRole('button', { name: 'Роли' }))
  await userEvent.click(screen.getByLabelText('Администратор платформы'))
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Подтвердите снятие')
  await userEvent.click(screen.getByLabelText('Подтверждаю снятие роли администратора платформы'))
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Нельзя снять у себя')
})
it('checks matching passwords and supports revealing them', async () => {
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('user@flounder.demo')).closest('tr')!
  await userEvent.click(within(row).getByRole('button', { name: 'Действия пользователя user@flounder.demo' }))
  await userEvent.click(screen.getByRole('menuitem', { name: 'Сменить пароль' }))
  await userEvent.type(screen.getByLabelText('Новый пароль'), 'NewPass2026!')
  await userEvent.type(screen.getByLabelText('Повторите пароль'), 'Different2026!')
  await userEvent.click(screen.getByLabelText('Показать пароль'))
  expect(screen.getByLabelText('Новый пароль')).toHaveAttribute('type', 'text')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Пароли должны совпадать')
})
it('keeps scoped assignments separate from editable global roles', async () => {
  server.use(http.get('*/api/v1/users/user-demo', () => HttpResponse.json({ roles: [
    { code: 'participant', name: 'Участник' },
    { code: 'judge', name: 'Судья', organizationId: 'organization-demo' },
  ] })))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('user@flounder.demo')).closest('tr')!
  await userEvent.click(within(row).getByRole('button', { name: 'Роли' }))
  const dialog = screen.getByRole('dialog')
  expect(await within(dialog).findByText(/Роли в отдельных организациях или соревнованиях/)).toBeInTheDocument()
  expect(within(dialog).getByLabelText('Участник')).toBeChecked()
  expect(within(dialog).getByLabelText('Судья')).not.toBeChecked()
})
it('shows deleted users without an unblock action', async () => {
  server.use(http.get('*/api/v1/users', () => HttpResponse.json({ items: [{
    id: 'deleted', email: 'deleted@test.example', firstName: 'Удалённый', lastName: 'Пользователь',
    status: 'INACTIVE', roles: ['participant'], createdAt: '2026-10-13T10:00:00Z',
  }], page: 0, size: 20, total: 1 })))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const row = (await screen.findByText('deleted@test.example')).closest('tr')!
  expect(within(row).getByRole('cell', { name: /^Удалён$/ })).toBeInTheDocument()
  expect(within(row).queryByRole('button', { name: 'Разблокировать' })).not.toBeInTheDocument()
  expect(within(row).queryByRole('button', { name: 'Заблокировать' })).not.toBeInTheDocument()
})

it('closes the overflow menu on Escape and returns focus to its trigger', async () => {
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  const trigger = await screen.findByRole('button', { name: 'Действия пользователя user@flounder.demo' })
  await userEvent.click(trigger)
  expect(screen.getByRole('menu', { name: 'Действия пользователя user@flounder.demo' })).toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await userEvent.keyboard('{ArrowDown}')
  expect(screen.getByRole('menuitem', { name: 'Сменить пароль' })).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  expect(trigger).toHaveFocus()
})
it('keeps existing users visible on a failed search and allows retry', async () => {
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  await screen.findByText('user@flounder.demo')
  server.use(http.get('*/api/v1/users', () => HttpResponse.json({ message: 'Сервис временно недоступен' }, { status: 503 })))
  await userEvent.type(screen.getByLabelText('Поиск пользователей'), 'Анна')
  expect(await screen.findByRole('alert')).toHaveTextContent('Сервис временно недоступен')
  expect(screen.getByText('user@flounder.demo')).toBeInTheDocument()
  server.use(...adminHandlers)
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  expect(screen.getByText('user@flounder.demo')).toBeInTheDocument()
})
it('waits for roles before showing the unavailable-role hint', async () => {
  let resolve!: () => void
  const pending = new Promise<void>(done => { resolve = done })
  server.use(http.get('*/api/v1/roles', async () => {
    await pending
    return HttpResponse.json([])
  }))
  render(<MemoryRouter initialEntries={['/admin/users']}><UsersAdminPage /></MemoryRouter>)
  expect(screen.queryByText('Создание пользователя станет доступно после загрузки справочника ролей.')).not.toBeInTheDocument()
  resolve()
  expect(await screen.findByText('Создание пользователя станет доступно после загрузки справочника ролей.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Создать пользователя' })).toBeDisabled()
})
