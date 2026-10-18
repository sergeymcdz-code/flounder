import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { server } from '../../test/setup'
import { LoginPage } from './LoginPage'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
beforeEach(() => setAuthSession(null))

it.each([
  [403, 'identity.user_blocked', 'Пользователь заблокирован', 'Аккаунт заблокирован, обратитесь к администратору'],
  [429, 'identity.too_many_attempts', 'HTTP 429', 'Слишком много попыток входа. Попробуйте позже'],
  [401, 'identity.invalid_credentials', 'Неверная почта или пароль', 'Проверьте email и пароль.'],
  [403, 'identity.access_denied', 'Доступ запрещён', 'Доступ запрещён'],
] as const)('shows the appropriate login error for %s %s', async (status, code, message, expected) => {
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ code, message }, { status })))
  render(<MemoryRouter><AuthProvider><LoginPage /></AuthProvider></MemoryRouter>)

  await userEvent.type(screen.getByLabelText('Email'), 'user@flounder.demo')
  await userEvent.type(screen.getByLabelText('Пароль'), 'Demo2026!')
  await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(expected)
  expect(screen.getByRole('button', { name: 'Войти' })).toBeEnabled()
  expect(sessionStorage.getItem('flounder.auth-session')).toBeNull()
})

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

it.each([['organizer', '/organizer/events'], ['operator', '/organizer/events'], ['judge', '/operator/results'], ['participant', '/my/registrations'], ['platform_admin', '/organizer/events']])('redirects %s after successful login', async (role, target) => {
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: `demo.${btoa(JSON.stringify({ roles: [role] }))}.demo`, tokenType: 'Bearer', refreshToken: 'test', expiresIn: 3600 })))
  render(<MemoryRouter initialEntries={['/login']}><AuthProvider><Routes><Route path="/login" element={<LoginPage />} /><Route path={target} element={<p>Кабинет роли</p>} /></Routes></AuthProvider></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('Email'), 'user@example.test'); await userEvent.type(screen.getByLabelText('Пароль'), 'Test2026!')
  await userEvent.click(screen.getByRole('button', { name: 'Войти' }))
  expect(await screen.findByText('Кабинет роли')).toBeInTheDocument()
})
