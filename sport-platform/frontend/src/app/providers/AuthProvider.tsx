import {
  createContext,
  useEffect, useState, useCallback, useMemo,
  useContext,
  useSyncExternalStore,
  type PropsWithChildren,
} from 'react'
import { login as loginRequest } from '../../api/auth.api'
import type {
  AuthSession,
  LoginRequest,
} from '../../features/auth/auth.types'

import { getAuthState, setAuthSession, subscribeAuth } from '../../api/auth-session'
import { apiFetch, sanitizeStoredSession, settleRefresh, restoreCookieSession } from '../../api/http'
import { LoadingState } from '../../components/LoadingState'

export const ROLE_PLATFORM_ADMIN = 'platform_admin'
export const ROLE_ORGANIZER = 'organizer'
export const ROLE_OPERATOR = 'operator'
export const ROLE_PARTICIPANT = 'participant'

type JwtPayload = {
  roles?: unknown
}

export type Membership = { organizationId: string; organizationName: string; memberRole: string }
const EMPTY_MEMBERSHIPS: Membership[] = []
type Profile = { id: string; roles: string[]; effectiveRoles: string[]; memberships: Membership[] }
type AuthContextValue = {
  memberships: Membership[]
  currentOrgId: string
  selectOrg: (id: string) => void
  userId: string | null
  authLoading: boolean
  refreshProfile: () => Promise<void>
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
  login: (request: LoginRequest) => Promise<AuthSession & { roles: string[] }>
  logout: () => Promise<void>
  sessionMessage: string | null
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

export function AuthProvider({ children }: PropsWithChildren) {
  const [restore] = useState(sanitizeStoredSession)
  const [restoring, setRestoring] = useState(restore)
  useEffect(() => {
    if (!restore) return
    let active = true
    void restoreCookieSession().finally(() => { if (active) setRestoring(false) })
    return () => { active = false }
  }, [restore])
  const { session, message: sessionMessage } = useSyncExternalStore(subscribeAuth, getAuthState)

  const [profile, setProfile] = useState<{ token: string; user: Profile | null } | null>(null)
  const [selectedOrg, setSelectedOrg] = useState(() => localStorage.getItem('flounder.current-org') ?? '')
  const token = session?.accessToken ?? ''
  const refreshProfile = useCallback(async () => {
    if (!token) return
    const user = await apiFetch<Profile>('/api/v1/auth/me', { accessToken: token })
    setProfile({ token, user })
  }, [token])
  useEffect(() => {
    if (!token) return
    let active = true
    apiFetch<Profile>('/api/v1/auth/me', { accessToken: token })
      .then(user => { if (active) setProfile({ token, user }) })
      .catch(() => { if (active) setProfile({ token, user: null }) })
    return () => { active = false }
  }, [token])
  const currentProfile = profile?.token === token ? profile.user : null
  const memberships = currentProfile?.memberships ?? EMPTY_MEMBERSHIPS
  const currentOrgId = memberships.some(m => m.organizationId === selectedOrg) ? selectedOrg : memberships[0]?.organizationId ?? ''
  const selectOrg = useCallback((id: string) => {
    localStorage.setItem('flounder.current-org', id)
    setSelectedOrg(id)
  }, [])
  const authLoading = restoring || (!!token && profile?.token !== token)

  const login = useCallback(async (request: LoginRequest): Promise<AuthSession & { roles: string[] }> => {
    await settleRefresh()
    const response = await loginRequest(request)

    const nextSession: AuthSession = {
      accessToken: response.accessToken,
      tokenType: response.tokenType,
      expiresAt: Date.now() + response.expiresIn * 1000,
    }

    const user = await apiFetch<Profile>('/api/v1/auth/me', { accessToken: nextSession.accessToken }).catch(() => null)
    setProfile({ token: nextSession.accessToken, user })
    setAuthSession(nextSession)
    return { ...nextSession, roles: user?.effectiveRoles ?? getRoles(nextSession.accessToken) }
  }, [])

  const logout = useCallback(async () => {
    setAuthSession(null)
    await settleRefresh()
    await apiFetch('/api/v1/auth/logout', { method: 'POST', skipRefresh: true })
  }, [])

  const roles = useMemo(() => currentProfile?.effectiveRoles ?? getRoles(token || null), [currentProfile?.effectiveRoles, token])

  const hasRole = useCallback((role: string) => roles.includes(role.toLowerCase()), [roles])
  const hasAnyRole = useCallback((requiredRoles: string[]) =>
    requiredRoles.some(role => roles.includes(role.toLowerCase())), [roles])

  const isPlatformAdmin = roles.includes(ROLE_PLATFORM_ADMIN)
  const isOrganizer = roles.includes(ROLE_ORGANIZER)
  const isOperator = roles.includes(ROLE_OPERATOR)
  const isParticipant = roles.includes(ROLE_PARTICIPANT)
  const isAdmin = isPlatformAdmin || isOrganizer

  const userId = currentProfile?.id ?? null
  const value = useMemo<AuthContextValue>(() => ({
      session,
      memberships, currentOrgId, selectOrg, userId, authLoading, refreshProfile,
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
      sessionMessage,
  }), [session, memberships, currentOrgId, selectOrg, userId, authLoading, refreshProfile,
    roles, hasRole, hasAnyRole, isPlatformAdmin, isOrganizer, isOperator, isParticipant,
    isAdmin, login, logout, sessionMessage])

  return (
    <AuthContext.Provider value={value}>
      {restoring ? <LoadingState message="Восстанавливаем сессию…" /> : children}
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