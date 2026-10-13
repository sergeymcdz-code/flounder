import type { AuthSession } from '../features/auth/auth.types'

const KEY = 'flounder.auth-session'
export const SESSION_EXPIRED = 'Сессия истекла, войдите снова'
let raw: string | null | undefined
let state: { session: AuthSession | null; message: string | null } = { session: null, message: null }
const listeners = new Set<() => void>()

export function getAuthState() {
  const stored = sessionStorage.getItem(KEY)
  if (stored !== raw) {
    raw = stored
    let session: AuthSession | null = null
    try {
      const parsed = stored ? JSON.parse(stored) as AuthSession : null
      if (parsed?.accessToken && (parsed.refreshToken || parsed.expiresAt > Date.now())) session = parsed
    } catch { /* Discard invalid stored sessions. */ }
    state = { session, message: null }
  }
  return state
}

export function setAuthSession(session: AuthSession | null, message: string | null = null) {
  raw = session ? JSON.stringify(session) : null
  if (raw) sessionStorage.setItem(KEY, raw)
  else sessionStorage.removeItem(KEY)
  state = { session, message }
  listeners.forEach(listener => listener())
}

export function subscribeAuth(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}
