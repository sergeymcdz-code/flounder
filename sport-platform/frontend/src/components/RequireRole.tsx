import { Link, Navigate, Outlet, useLocation, useOutletContext } from 'react-router'
import { ErrorState } from './ErrorState'
import { useAuth } from '../app/providers/AuthProvider'

type RequireRoleProps = {
  roles: string[]
  deniedMessage?: string
}

export function RequireRole({ roles, deniedMessage }: RequireRoleProps) {
  const { isAuthenticated, hasAnyRole, authLoading } = useAuth()
  const location = useLocation()
  const ctx = useOutletContext()

  if (!isAuthenticated) {
    return (
      <Navigate
        replace
        to="/login"
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  if (authLoading) return <p role="status">Загружаем права доступа…</p>

  if (!hasAnyRole(roles)) {
    return <div><ErrorState title="Нет доступа" message={deniedMessage ?? 'У вашей учётной записи нет доступа к этой странице.'} /><Link to="/">На главную</Link></div>
  }

  return <Outlet context={ctx} />
}