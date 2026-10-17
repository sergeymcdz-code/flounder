import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'
it('requires a rejection reason and restores focus to the trigger', async () => {
  const trigger = document.createElement('button'); document.body.append(trigger); trigger.focus()
  const confirm = vi.fn()
  const { unmount } = render(<ConfirmDialog title="Отклонить документ?" description="Участник увидит причину" confirmLabel="Отклонить" tone="danger" reason onClose={() => {}} onConfirm={confirm} />)
  expect(screen.getByRole('button', { name: 'Отмена' })).toHaveFocus()
  expect(screen.getByRole('button', { name: 'Отклонить' })).toBeDisabled()
  await userEvent.type(screen.getByRole('textbox', { name: 'Причина' }), 'Истёк срок')
  await userEvent.click(screen.getByRole('button', { name: 'Отклонить' }))
  expect(confirm).toHaveBeenCalledWith('Истёк срок')
  unmount(); expect(trigger).toHaveFocus(); trigger.remove()
})
