import { Link, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { roleHomeTarget } from '../../utils/authReturn'
import { ThemeToggle } from '../../components/ThemeToggle'
import styles from './Calendar.module.css'

export function DirectoryLayout() {
  const { roles, isAuthenticated, isPlatformAdmin, isOrganizer, isOperator, isParticipant, hasRole, logout, sessionMessage } = useAuth()
  const navigate = useNavigate()
  function handleLogout() {
    void logout().catch(() => { /* The local session is already cleared. */ })
    navigate('/', { replace: true })
  }
  return <div className={styles.root}>
    <a className={styles.skip} href="#public-content">К содержимому</a>
    <header className={styles.header}>
      <Link className={styles.brand} to={roleHomeTarget(roles, isAuthenticated)} aria-label="На главную">SportGate</Link>
      <nav aria-label="Основная навигация">
        <Link to="/">Календарь</Link>
        {isAuthenticated ? <>
          {isParticipant && <Link to="/my/registrations">Мои заявки</Link>}
          {isPlatformAdmin ? <Link to="/admin/users">Администрирование</Link>
            : (isOrganizer || isOperator) ? <Link to="/organizer/events">Мои соревнования</Link>
            : hasRole('judge') ? <Link to="/operator/results">Судейство</Link> : null}
          <button className="fl-button-ghost" type="button" onClick={handleLogout}>Выйти</button>
        </> : <>
          <Link to="/login">Войти</Link>
          <Link to="/register">Регистрация</Link>
        </>}
      </nav>
      <ThemeToggle />
    </header>
    <main id="public-content" className={styles.main}>
      {sessionMessage && <p role="alert">{sessionMessage}</p>}
      <Outlet />
    </main>
    <footer className={styles.footer}>SportGate · sportgate.pro</footer>
  </div>
}
