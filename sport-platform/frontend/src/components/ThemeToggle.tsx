import { createContext, useContext, useEffect, useLayoutEffect, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { readTheme, resolveTheme, themeKey, type Theme } from './theme'
import styles from './ThemeToggle.module.css'
import { Button } from './ui/Button'

const ThemeContext = createContext<{ theme: Theme; toggle: () => void } | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const [saved, setSaved] = useState(readTheme)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches)
  const theme = resolveTheme(pathname, saved, systemDark)

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    function onSchemeChange() { setSystemDark(media.matches) }
    function onStorage(event: StorageEvent) {
      if (event.key === themeKey || event.key === null) setSaved(readTheme())
    }
    media.addEventListener('change', onSchemeChange)
    window.addEventListener('storage', onStorage)
    return () => {
      media.removeEventListener('change', onSchemeChange)
      window.removeEventListener('storage', onStorage)
    }
  }, [])

  function toggle() {
    const next = theme === 'dark' ? 'light' : 'dark'
    setSaved(next)
    try { localStorage.setItem(themeKey, next) } catch { /* Keep the choice for this session. */ }
  }

  return <ThemeContext value={{ theme, toggle }}>{children}</ThemeContext>
}

export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const context = useContext(ThemeContext)
  if (!context) return null
  const { theme, toggle } = context
  const dark = theme === 'dark'
  return <Button variant="ghost" className={`${styles.toggle} ${compact ? styles.compact : ''}`} onClick={toggle}
    aria-label={dark ? 'Светлая тема' : 'Тёмная тема'}>
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      {dark ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></>
        : <path d="M20.5 14a9 9 0 0 1-10.5-10.5A9 9 0 1 0 20.5 14Z" />}
    </svg>
    <span className={styles.label}>{dark ? 'Светлая' : 'Тёмная'}</span>
  </Button>
}
