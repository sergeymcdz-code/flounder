import {
  createContext,
  useEffect, useState, useCallback,
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
import { apiFetch } from '../../api/http'

export const ROLE_PLATFORM_ADMIN = 'platform_admin'
export const ROLE_ORGANIZER = 'organizer'
export const ROLE_OPERATOR = 'operator'
export const ROLE_PARTICIPANT = 'participant'

type JwtPayload = {
  roles?: unknown
}

export type Membership = { organizationId: string; organizationName: string; memberRole: string }
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
  login: (request: LoginRequest) => Promise<void>
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
  const memberships = currentProfile?.memberships ?? []
  const currentOrgId = memberships.some(m => m.organizationId === selectedOrg) ? selectedOrg : memberships[0]?.organizationId ?? ''
  function selectOrg(id: string) {
    localStorage.setItem('flounder.current-org', id)
    setSelectedOrg(id)
  }
  const authLoading = !!token && profile?.token !== token

  async function login(request: LoginRequest): Promise<void> {
    const response = await loginRequest(request)

    const nextSession: AuthSession = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      tokenType: response.tokenType,
      expiresAt: Date.now() + response.expiresIn * 1000,
    }

    setAuthSession(nextSession)
  }

  async function logout() {
    const current = getAuthState().session
    setAuthSession(null)
    if (current?.refreshToken) {
      await apiFetch('/api/v1/auth/logout', {
        method: 'POST', body: JSON.stringify({ refreshToken: current.refreshToken }), skipRefresh: true,
      })
    }
  }

  const roles = currentProfile?.effectiveRoles ?? getRoles(session?.accessToken ?? null)

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

  const value: AuthContextValue = {
      session,
      memberships, currentOrgId, selectOrg, userId: currentProfile?.id ?? null, authLoading, refreshProfile,
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
  }

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