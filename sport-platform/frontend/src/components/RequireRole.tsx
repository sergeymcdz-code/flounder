import { Navigate, Outlet, useLocation } from 'react-router'
import { ErrorState } from './ErrorState'
import { useAuth } from '../app/providers/AuthProvider'

type RequireRoleProps = {
  roles: string[]
  deniedMessage?: string
}

export function RequireRole({ roles, deniedMessage }: RequireRoleProps) {
  const { isAuthenticated, hasAnyRole } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return (
      <Navigate
        replace
        to="/login"
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  if (!hasAnyRole(roles)) {
    return deniedMessage
      ? <ErrorState title="Нет доступа" message={deniedMessage} />
      : <Navigate replace to="/" />
  }

  return <Outlet />
}