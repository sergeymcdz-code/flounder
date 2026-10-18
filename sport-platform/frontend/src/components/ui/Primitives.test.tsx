import { render, screen } from '@testing-library/react'
import { it, expect } from 'vitest'
import { MemoryRouter, Link } from 'react-router'
import { Button } from './Button'
import { StatusBadge } from './StatusBadge'
import { Tabs } from './Tabs'
import { EmptyState } from './EmptyState'
it('keeps button label space and disables while loading', () => {
 render(<Button variant="danger" size="lg" loading>Опубликовать</Button>)
 const button = screen.getByRole('button', { name: 'Опубликовать' })
 expect(button).toHaveClass('fl-btn-danger', 'fl-btn-lg')
 expect(button).toHaveAttribute('aria-busy', 'true')
 expect(button).toBeDisabled()
 expect(button.style.opacity).not.toBe('0.5')
 expect(screen.getByText('Опубликовать')).toHaveStyle({ visibility: 'hidden' })
})
it('renders router links and anchor buttons', () => {
 render(<MemoryRouter><Button as={Link} to="/next">Далее</Button><Button as="a" href="/file">Файл</Button></MemoryRouter>)
 expect(screen.getByRole('link', { name: 'Далее' })).toHaveAttribute('href', '/next')
 expect(screen.getByRole('link', { name: 'Файл' })).toHaveAttribute('href', '/file')
})
it('has status dots only for success and danger and marks live', () => {
 const { container } = render(<><StatusBadge kind="event" code="LIVE" /><StatusBadge kind="document" code="ACCEPTED" /><StatusBadge kind="document" code="PENDING" /><StatusBadge kind="heat" code="DRAFT" /></>)
 expect(container.querySelectorAll('.fl-badge-dot')).toHaveLength(2)
 expect(screen.getByText('Идёт')).toHaveClass('fl-badge-live', 'fl-badge-danger')
 expect(screen.getByText('На проверке')).toHaveClass('fl-badge-info')
})
it('shows tab counters without changing its navigation name', () => {
 render(<MemoryRouter initialEntries={['/entries']}><Tabs items={[{ to: '/entries', label: 'Заявки', count: 12, attention: 2 }]} /></MemoryRouter>)
 expect(screen.getByRole('link', { name: 'Заявки' })).toHaveAttribute('aria-current', 'page')
 expect(screen.getByText('12')).toBeInTheDocument()
 expect(screen.getByLabelText('2 требуют внимания')).toBeInTheDocument()
})
it('offers an action in an empty state', () => {
 render(<EmptyState title="Сессий пока нет" action={<Button>Добавить сессию</Button>} />)
 expect(screen.getByRole('button', { name: 'Добавить сессию' })).toBeEnabled()
})
it.each(['primary','secondary','ghost','danger'] as const)('supports the %s button variant', variant => {
 render(<Button variant={variant}>Действие</Button>)
 expect(screen.getByRole('button', { name: 'Действие' })).toHaveClass(`fl-btn-${variant}`)
})
