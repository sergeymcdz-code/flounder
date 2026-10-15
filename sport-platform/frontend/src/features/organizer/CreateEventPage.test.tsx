import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { it, expect, vi } from 'vitest'
import { CreateEventPage } from './CreateEventPage'
vi.mock('../../app/providers/AuthProvider', () => ({ useAuth: () => ({ session: { accessToken: 'test-token' }, logout: vi.fn() }) }))
it('shows the three visual steps and keeps the participant limit in basic data', async () => {
 render(<MemoryRouter><CreateEventPage /></MemoryRouter>)
 expect(screen.getByRole('list', { name: 'Этапы создания' })).toHaveTextContent('① Основное② Регистрация③ Программа — после создания')
 const basic = screen.getByRole('group', { name: 'Основные данные' })
 expect(basic).toContainElement(screen.getByRole('spinbutton', { name: /Лимит/ }))
})
