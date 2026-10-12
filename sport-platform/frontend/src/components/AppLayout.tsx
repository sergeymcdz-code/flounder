import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'
import styles from './AppLayout.module.css'

export function AppLayout() {
  const {
    isAuthenticated,
    isPlatformAdmin,
    isOrganizer,
    isOperator,
    isParticipant,
    logout,
  } = useAuth()

  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/', { replace: true })
  }

  function navLinkClassName({ isActive }: { isActive: boolean }) {
    return isActive
      ? `${styles.navLink} ${styles.navLinkActive}`
      : styles.navLink
  }

  const canManageEvents = isPlatformAdmin || isOrganizer
  const canReviewRegistrations = isPlatformAdmin || isOrganizer
  const canProcessPayments = isPlatformAdmin || isOperator
  const canManageResults = isPlatformAdmin || isOperator

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <NavLink className={styles.brand} to="/">
          Flounder
        </NavLink>

        <nav className={styles.navigation} aria-label="Основная навигация">
          <NavLink className={navLinkClassName} to="/" end>
            События
          </NavLink>

          {isAuthenticated && isParticipant ? (
            <NavLink className={navLinkClassName} to="/my/registrations">
              Мои заявки
            </NavLink>
          ) : null}

          {canManageEvents ? (
            <NavLink className={navLinkClassName} to="/organizer/events">
              Мои события
            </NavLink>
          ) : null}

          {canReviewRegistrations ? (
            <NavLink
              className={navLinkClassName}
              to="/admin/registrations/review"
            >
              Заявки
            </NavLink>
          ) : null}

          {isPlatformAdmin ? (
            <NavLink className={navLinkClassName} to="/admin/sports">
              Виды спорта
            </NavLink>
          ) : null}

          {canManageResults ? (
            <NavLink className={navLinkClassName} to="/operator/results">
              Результаты
            </NavLink>
          ) : null}

          {canProcessPayments ? (
            <NavLink className={navLinkClassName} to="/operator/payments">
              Заявки и платежи
            </NavLink>
          ) : null}

          {isAuthenticated ? (
            <button
              className={`${styles.navLink} ${styles.navButton}`}
              onClick={handleLogout}
              type="button"
            >
              Выйти
            </button>
          ) : (
            <NavLink className={navLinkClassName} to="/login">
              Войти
            </NavLink>
          )}
        </nav>
      </header>

      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}