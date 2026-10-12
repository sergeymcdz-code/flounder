import {
  createContext,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { login as loginRequest } from '../../api/auth.api'
import type {
  AuthSession,
  LoginRequest,
} from '../../features/auth/auth.types'

const SESSION_STORAGE_KEY = 'flounder.auth-session'

export const ROLE_PLATFORM_ADMIN = 'platform_admin'
export const ROLE_ORGANIZER = 'organizer'
export const ROLE_OPERATOR = 'operator'
export const ROLE_PARTICIPANT = 'participant'

type JwtPayload = {
  roles?: unknown
}

type AuthContextValue = {
  session: AuthSession | null
  isAuthenticated: boolean
  roles: string[]
  hasRole: (role: string) => boolean
  hasAnyRole: (roles: string[]) => boolean
  isPlatformAdmin: boolean
  isOrganizer: boolean
  isOperator: boolean
  isParticipant: boolean
  isAdmin: boolean
  login: (request: LoginRequest) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function decodeJwtPayload(accessToken: string): JwtPayload | null {
  const [, payload] = accessToken.split('.')

  if (!payload) {
    return null
  }

  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')

    const json = decodeURIComponent(
      Array.from(atob(base64))
        .map((character) => {
          return `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`
        })
        .join(''),
    )

    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

function getRoles(accessToken: string | null): string[] {
  if (!accessToken) {
    return []
  }

  const roles = decodeJwtPayload(accessToken)?.roles

  return Array.isArray(roles)
    ? roles
        .filter((role): role is string => typeof role === 'string')
        .map((role) => role.toLowerCase())
    : []
}

function readStoredSession(): AuthSession | null {
  const rawSession = sessionStorage.getItem(SESSION_STORAGE_KEY)

  if (!rawSession) {
    return null
  }

  try {
    const session = JSON.parse(rawSession) as AuthSession

    if (!session.accessToken || session.expiresAt <= Date.now()) {
      sessionStorage.removeItem(SESSION_STORAGE_KEY)
      return null
    }

    return session
  } catch {
    sessionStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthSession | null>(() =>
    readStoredSession(),
  )

  async function login(request: LoginRequest): Promise<void> {
    const response = await loginRequest(request)

    const nextSession: AuthSession = {
      accessToken: response.accessToken,
      tokenType: response.tokenType,
      expiresAt: Date.now() + response.expiresIn * 1000,
    }

    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nextSession))
    setSession(nextSession)
  }

  function logout() {
    sessionStorage.removeItem(SESSION_STORAGE_KEY)
    setSession(null)
  }

  const roles = getRoles(session?.accessToken ?? null)

  function hasRole(role: string) {
    return roles.includes(role.toLowerCase())
  }

  function hasAnyRole(requiredRoles: string[]) {
    return requiredRoles.some((role) => roles.includes(role.toLowerCase()))
  }

  const isPlatformAdmin = roles.includes(ROLE_PLATFORM_ADMIN)
  const isOrganizer = roles.includes(ROLE_ORGANIZER)
  const isOperator = roles.includes(ROLE_OPERATOR)
  const isParticipant = roles.includes(ROLE_PARTICIPANT)
  const isAdmin = isPlatformAdmin || isOrganizer

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      roles,
      hasRole,
      hasAnyRole,
      isPlatformAdmin,
      isOrganizer,
      isOperator,
      isParticipant,
      isAdmin,
      login,
      logout,
    }),
    [
      session,
      roles,
      isPlatformAdmin,
      isOrganizer,
      isOperator,
      isParticipant,
      isAdmin,
    ],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider.')
  }

  return context
}