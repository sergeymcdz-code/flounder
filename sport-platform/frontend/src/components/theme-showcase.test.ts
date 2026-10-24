import { expect, it } from 'vitest'
import { resolveTheme } from './theme'
it('honors the saved showcase theme, including its example tabs', () => {
  expect(resolveTheme('/dev/ui', 'dark', false)).toBe('dark')
  expect(resolveTheme('/dev/ui/results', 'light', true)).toBe('light')
  expect(resolveTheme('/organizer/events', 'dark', false)).toBe('light')
})
