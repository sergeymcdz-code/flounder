import indexHtml from '../../index.html?raw'
import { fireEvent, render, screen } from '@testing-library/react'
import { Link, MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider, ThemeToggle } from './ThemeToggle'
import { readTheme, resolveTheme, themeKey } from './theme'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
})

const bootstrap = indexHtml.match(/<script>([\s\S]*?)<\/script>/)![1]

describe('initial theme before React and CSS load', () => {
  it.each([
    ['/', 'dark', false, 'dark'],
    ['/o/volna', null, true, 'dark'],
    ['/o/volna', 'light', true, 'light'],
    ['/e/demo', null, false, 'light'],
    ['/e/demo/live', null, false, 'dark'],
    ['/e/demo/results/', null, false, 'dark'],
    ['/e/demo/start-list', null, true, 'dark'],
    ['/e/demo/register', 'light', true, 'light'],
    ['/e/demo/live', 'light', true, 'light'],
    ['/e/demo', 'dark', false, 'dark'],
    ['/e/demo', 'invalid', false, 'light'],
    ['/events/demo/disciplines/M-50-FREE/results', 'light', true, 'light'],
    ['/events/demo/disciplines/M-50-FREE/results', null, false, 'dark'],
    ['/events/demo/disciplines/M-50-FREE/register', 'dark', true, 'light'],
    ['/login', 'dark', true, 'light'],
    ['/organizer/events', 'dark', true, 'light'],
  ])('%s / saved %s / system dark %s → %s', (path, saved, systemDark, expected) => {
    const dataset: Record<string, string> = {}
    new Function('document', 'location', 'localStorage', 'matchMedia', bootstrap)(
      { documentElement: { dataset } }, { pathname: path },
      { getItem: () => saved }, () => ({ matches: systemDark }),
    )
    expect(dataset.theme).toBe(expected)
    expect(resolveTheme(path as string, saved === 'light' || saved === 'dark' ? saved : null, Boolean(systemDark))).toBe(expected)
  })

  it('works when browser storage is unavailable', () => {
    const dataset: Record<string, string> = {}
    new Function('document', 'location', 'localStorage', 'matchMedia', bootstrap)(
      { documentElement: { dataset } }, { pathname: '/e/demo/live' },
      { getItem: () => { throw new Error('blocked') } }, () => ({ matches: false }),
    )
    expect(dataset.theme).toBe('dark')
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    expect(readTheme()).toBeNull()
  })
})

function mount() {
  return render(<MemoryRouter initialEntries={['/e/demo/live']}><ThemeProvider>
    <ThemeToggle />
    <Link to="/organizer/events">Кабинет</Link><Link to="/e/demo">Событие</Link>
  </ThemeProvider></MemoryRouter>)
}

it('keeps the explicit choice after remount and restores light in the cabinet', () => {
  const first = mount()
  fireEvent.click(screen.getByRole('button', { name: 'Светлая тема' }))
  expect(localStorage.getItem(themeKey)).toBe('light')
  first.unmount()
  mount()
  expect(document.documentElement.dataset.theme).toBe('light')
  fireEvent.click(screen.getByRole('button', { name: 'Тёмная тема' }))
  fireEvent.click(screen.getByRole('link', { name: 'Кабинет' }))
  expect(document.documentElement.dataset.theme).toBe('light')
  fireEvent.click(screen.getByRole('link', { name: 'Событие' }))
  expect(document.documentElement.dataset.theme).toBe('dark')
})

it('keeps working for the session when writing storage fails', () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
  mount()
  fireEvent.click(screen.getByRole('button', { name: 'Светлая тема' }))
  expect(document.documentElement.dataset.theme).toBe('light')
  fireEvent.click(screen.getByRole('button', { name: 'Тёмная тема' }))
  expect(document.documentElement.dataset.theme).toBe('dark')
})

it('reflects another tab changing or clearing the saved preference', () => {
  mount()
  localStorage.setItem(themeKey, 'light')
  fireEvent(window, new StorageEvent('storage', { key: themeKey }))
  expect(document.documentElement.dataset.theme).toBe('light')
  localStorage.removeItem(themeKey)
  fireEvent(window, new StorageEvent('storage', { key: themeKey }))
  expect(document.documentElement.dataset.theme).toBe('dark')
})
