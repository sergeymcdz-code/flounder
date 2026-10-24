import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { getAuthState, setAuthSession } from '../../api/auth-session'
import { PasswordPage } from './PasswordPage'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
beforeEach(() => setAuthSession(null))
function page(mode: 'forgot' | 'reset' | 'change', url = '/reset-password#token=reset-token') {
  render(<MemoryRouter initialEntries={[url]}><AuthProvider><Routes>
    <Route path="/login" element={<p>Страница входа</p>} />
    <Route path="*" element={<PasswordPage mode={mode} />} />
  </Routes></AuthProvider></MemoryRouter>)
}
async function passwords(value: string, confirmation = value) {
  await userEvent.type(screen.getByLabelText('Новый пароль'), value)
  await userEvent.type(screen.getByLabelText('Повторите пароль'), confirmation)
}

it('requests reset and shows a generic success message without revealing account existence', async () => {
  let submitted: unknown
  server.use(http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
    submitted = await request.json(); return new HttpResponse(null, { status: 202 })
  }))
  page('forgot', '/forgot-password')
  await userEvent.type(screen.getByLabelText('Email'), 'unknown@example.org')
  await userEvent.click(screen.getByRole('button', { name: 'Отправить ссылку' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Если аккаунт с этим email существует')
  expect(submitted).toEqual({ email: 'unknown@example.org' })
})

it('validates weak passwords and confirmation before sending reset', async () => {
  let requests = 0
  server.use(http.post('*/api/v1/auth/password-reset/confirm', () => { requests++; return new HttpResponse(null, { status: 204 }) }))
  page('reset'); await passwords('abcdefgh')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(screen.getByRole('alert')).toHaveTextContent('буквы и цифры')
  await userEvent.clear(screen.getByLabelText('Новый пароль'))
  await userEvent.type(screen.getByLabelText('Новый пароль'), 'Reset123!')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(screen.getByRole('alert')).toHaveTextContent('Пароли не совпадают')
  expect(requests).toBe(0)
})

it('confirms reset using the URL token and disables fields while submitting', async () => {
  let finish!: () => void
  const gate = new Promise<void>(resolve => { finish = resolve })
  let submitted: unknown
  server.use(http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
    submitted = await request.json(); await gate; return new HttpResponse(null, { status: 204 })
  }))
  page('reset'); await passwords('Reset123!')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(screen.getByRole('button', { name: 'Отправляем…' })).toBeDisabled()
  expect(screen.getByLabelText('Новый пароль')).toBeDisabled()
  finish()
  expect(await screen.findByRole('status')).toHaveTextContent('Пароль изменён')
  expect(submitted).toEqual({ token: 'reset-token', newPassword: 'Reset123!' })
})

it('shows expired reset errors and allows another attempt', async () => {
  server.use(http.post('*/api/v1/auth/password-reset/confirm', () => HttpResponse.json({ code: 'identity.reset_token_invalid', message: 'Ссылка восстановления недействительна или истекла' }, { status: 400 })))
  page('reset'); await passwords('Reset123!')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Ссылка восстановления недействительна')
  expect(screen.getByRole('button', { name: 'Сохранить пароль' })).toBeEnabled()
})

it('explains missing reset tokens and links to a new request', () => {
  page('reset', '/reset-password')
  expect(screen.getByRole('alert')).toHaveTextContent('В ссылке отсутствует токен')
  expect(screen.getByRole('link', { name: 'Запросить новую ссылку' })).toHaveAttribute('href', '/forgot-password')
})

it('sends current and new passwords with authentication and displays mismatch errors', async () => {
  setAuthSession({ accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer', expiresAt: Date.now() + 100000 })
  let submitted: unknown
  server.use(http.post('*/api/v1/auth/change-password', async ({ request }) => {
    expect(request.headers.get('Authorization')).toBe('Bearer access')
    submitted = await request.json()
    return HttpResponse.json({ code: 'identity.password_mismatch', message: 'Текущий пароль указан неверно' }, { status: 400 })
  }))
  page('change', '/account/password')
  await userEvent.type(screen.getByLabelText('Текущий пароль'), 'Wrong123!'); await passwords('Newpass123!')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Текущий пароль указан неверно')
  expect(submitted).toEqual({ currentPassword: 'Wrong123!', newPassword: 'Newpass123!' })
})

it('redirects anonymous password changes to login', async () => {
  page('change', '/account/password')
  expect(await screen.findByText('Страница входа')).toBeInTheDocument()
})

it('signs out after changing the password and keeps the success message visible', async () => {
  setAuthSession({ accessToken: 'access', refreshToken: 'revoked-refresh', tokenType: 'Bearer', expiresAt: Date.now() + 100000 })
  let revoked: unknown
  server.use(
    http.post('*/api/v1/auth/change-password', () => new HttpResponse(null, { status: 204 })),
    http.post('*/api/v1/auth/logout', async ({ request }) => {
      revoked = await request.text(); return new HttpResponse(null, { status: 204 })
    }),
  )
  page('change', '/account/password')
  await userEvent.type(screen.getByLabelText('Текущий пароль'), 'Initial123!')
  await passwords('Changed123!')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Пароль изменён. Войдите заново с новым паролем')
  await vi.waitFor(() => expect(revoked).toBe(''))
  expect(getAuthState().session).toBeNull()
  expect(sessionStorage.getItem('flounder.auth-session')).toBeNull()
  expect(screen.queryByText('Страница входа')).not.toBeInTheDocument()
})
