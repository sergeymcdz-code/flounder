import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { OrganizationsAdminPage } from './OrganizationsAdminPage'
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
it('omits empty optional fields from organization creation', async () => {
  let submitted: unknown
  server.use(http.post('*/api/v1/organizations', async ({ request }) => {
    submitted = await request.json()
    return HttpResponse.json({ id: 'new' }, { status: 201 })
  }))
  render(<MemoryRouter><OrganizationsAdminPage /></MemoryRouter>)
  await screen.findByText('Клуб Волна')
  await userEvent.click(screen.getByRole('button', { name: 'Создать организацию' }))
  const dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText('Название'), 'Новый клуб')
  await userEvent.type(within(dialog).getByLabelText('Город'), '  ')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Создать' }))
  await waitFor(() => expect(submitted).toEqual({ type: 'club', name: 'Новый клуб' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})
