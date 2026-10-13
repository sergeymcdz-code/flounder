import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { LoginPage } from './LoginPage'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
beforeEach(() => setAuthSession(null))
it('shows the login wait time from Retry-After rounded up to minutes', async () => {
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ code: 'identity.too_many_attempts', message: 'Слишком много попыток входа. Попробуйте позже' }, {
    status: 429, headers: { 'Retry-After': '121' },
  })))
  render(<MemoryRouter><AuthProvider><LoginPage /></AuthProvider></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('Email'), 'user@example.org')
  await userEvent.type(screen.getByLabelText('Пароль'), 'Wrong123!')
  await userEvent.click(screen.getByRole('button', { name: 'Войти' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Слишком много попыток входа. Попробуйте через 3 мин.')
})
