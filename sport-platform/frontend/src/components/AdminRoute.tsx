import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'

type RequireRoleProps = {
  roles: string[]
}

export function RequireRole({ roles }: RequireRoleProps) {
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
    return <Navigate replace to="/" />
  }

  return <Outlet />
}