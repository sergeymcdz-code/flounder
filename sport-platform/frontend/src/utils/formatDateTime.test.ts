import { expect, it } from 'vitest'
import { formatDateTime } from './formatDateTime'
it('formats dates in the venue timezone independently of the browser', () => {
  expect(formatDateTime('2026-10-17T06:00:00Z', 'Europe/Samara')).toBe('17.10.2026 10:00')
  expect(formatDateTime('2026-10-17T06:00:00Z', 'Europe/Moscow')).toBe('17.10.2026 09:00')
  expect(formatDateTime(null)).toBe('Не назначено')
  expect(formatDateTime('invalid')).toBe('Не назначено')
})
