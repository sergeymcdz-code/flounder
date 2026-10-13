import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { http, HttpResponse } from 'msw'
import { server } from '../../test/setup'
import { AuthProvider } from '../../app/providers/AuthProvider'
import { setAuthSession } from '../../api/auth-session'
import { RegisterPage } from './RegisterPage'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost') })
it('explains that an imported athlete must be linked by the organizer', async () => {
  setAuthSession(null)
  server.use(http.post('*/api/v1/auth/register', () => HttpResponse.json({
    code: 'identity.profile_already_attached', message: 'Conflict',
  }, { status: 409 })))
  render(<MemoryRouter><AuthProvider><RegisterPage /></AuthProvider></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('Имя'), 'Иван')
  await userEvent.type(screen.getByLabelText('Фамилия'), 'Иванов')
  await userEvent.type(screen.getByLabelText('Дата рождения'), '2014-03-05')
  await userEvent.type(screen.getByLabelText('Email'), 'signup@qa04.example')
  await userEvent.type(screen.getByLabelText('Телефон'), '+79991234567')
  await userEvent.type(screen.getByLabelText('Пароль'), 'Signup123!')
  await userEvent.type(screen.getByLabelText('Повторите пароль'), 'Signup123!')
  await userEvent.click(screen.getByRole('checkbox'))
  await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Спортсмен с такими ФИО, датой рождения и полом уже есть в списке заявок организатора. Для привязки аккаунта обратитесь к организатору.',
  )
})
