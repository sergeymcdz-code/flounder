import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'

export function ProtectedRoute() {
  const { isAuthenticated } = useAuth()
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

  return <Outlet />
}