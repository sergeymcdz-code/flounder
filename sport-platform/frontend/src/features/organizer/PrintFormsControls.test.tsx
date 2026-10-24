import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ResultsPrintControls } from './PrintFormsControls'
vi.mock('./organizer-event-context', () => ({ useOrganizerEvent: () => ({ event: { id: 'cup', publicSlug: 'cup', disciplines: [] }, heats: [], accessToken: 'token' }) }))
it('opens the print sheet, explains disabled PDF and returns focus after Escape', async () => {
  window.matchMedia = vi.fn().mockReturnValue({ matches: false })
  render(<ResultsPrintControls />)
  const trigger = screen.getByRole('button', { name: 'Печать ▾' })
  await userEvent.click(trigger)
  expect(screen.getByRole('dialog', { name: 'Печать результатов' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Скачать PDF' })).toHaveAttribute('title', 'Сначала введите результаты заплывов')
  expect(screen.getByText('Параметры награждения').closest('details')).not.toHaveAttribute('open')
  await userEvent.click(screen.getByText('Параметры награждения'))
  await userEvent.clear(screen.getByLabelText('Число мест'))
  await userEvent.type(screen.getByLabelText('Число мест'), '5')
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { bubbles: true, cancelable: true }))
  await vi.waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(trigger).toHaveFocus()
  await userEvent.click(trigger)
  await userEvent.click(screen.getByText('Параметры награждения'))
  expect(screen.getByLabelText('Число мест')).toHaveValue(5)
})
