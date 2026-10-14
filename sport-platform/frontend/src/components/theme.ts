export type Theme = 'light' | 'dark'
export const themeKey = 'flounder.theme'

export function readTheme(): Theme | null {
  try {
    const saved = localStorage.getItem(themeKey)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

export function resolveTheme(pathname: string, saved: Theme | null, systemDark: boolean): Theme {
  if (!/^\/e\/[^/]+(?:\/|$)/.test(pathname)) return 'light'
  return saved ?? (systemDark || /\/(live|results)\/?$/.test(pathname) ? 'dark' : 'light')
}
