import { expect, it } from 'vitest'
import { disabledReason } from './organizer-event-context'
it('uses the same archive reason and respects busy and domain-specific reasons', () => {
  expect(disabledReason({ archived: true, busy: true })).toBe('Соревнование в архиве: доступен только просмотр')
  expect(disabledReason({ busy: true, reason: 'Нет заявок' })).toBe('Выполняем действие…')
  expect(disabledReason({ reason: 'Нет заявок' })).toBe('Нет заявок')
  expect(disabledReason()).toBe('')
})
