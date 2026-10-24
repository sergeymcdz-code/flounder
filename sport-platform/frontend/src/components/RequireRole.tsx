import { Link, Navigate, Outlet, useLocation, useOutletContext } from 'react-router'
import { Button } from './ui/Button'
import { LoadingState } from './LoadingState'
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

  if (authLoading) return <LoadingState message="Загружаем права доступа…" />

  if (!hasAnyRole(roles)) {
    return <div><ErrorState title="Нет доступа" message={deniedMessage ?? 'У вашей учётной записи нет доступа к этой странице.'} /><Button as={Link} to="/" variant="ghost">← На главную</Button></div>
  }

  return <Outlet context={ctx} />
}