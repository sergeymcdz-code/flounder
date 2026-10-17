import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'
import { apiFetch } from '../api/http'
import styles from './AppLayout.module.css'
import { roleHomeTarget } from '../utils/authReturn'

export function AppLayout() {
  useEffect(() => { document.title = 'SportGate' }, [])
  const { isAuthenticated, isPlatformAdmin, isOrganizer, isOperator, isParticipant, hasRole, logout, sessionMessage, session, roles } = useAuth()
  const [name, setName] = useState('')
  useEffect(() => {
    if (!session) return
    let active = true
    apiFetch<{ email: string; profile?: { firstName?: string } }>('/api/v1/auth/me', { accessToken: session.accessToken })
      .then(user => { if (active) setName(user.profile?.firstName || user.email.split('@')[0]) }).catch(() => {})
    return () => { active = false }
  }, [session])
  const navigate = useNavigate()
  function handleLogout() {
    void logout().catch(() => {})
    navigate('/', { replace: true })
  }
  const navLinkClassName = ({ isActive }: { isActive: boolean }) => isActive ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink
  const canManageEvents = isPlatformAdmin || isOrganizer || isOperator
  const isJudge = hasRole('judge')
  const roleLabel = isPlatformAdmin ? 'Администратор' : isOrganizer ? 'Организатор' : isOperator ? 'Секретарь' : isJudge ? 'Судья' : 'Участник'
  return <div className={styles.shell}>
    <header className={styles.header}>
      <NavLink className={styles.brand} to={roleHomeTarget(roles, isAuthenticated)} aria-label="На главную">SportGate</NavLink>
      <nav className={styles.navigation} aria-label="Основная навигация">
        <NavLink className={navLinkClassName} to="/" end>Соревнования</NavLink>
        {canManageEvents ? <NavLink className={navLinkClassName} to="/organizer/events">Мои соревнования</NavLink>
          : isJudge ? <NavLink className={navLinkClassName} to="/operator/results">Судейство</NavLink>
          : isAuthenticated && isParticipant ? <NavLink className={navLinkClassName} to="/my/registrations">Мои заявки</NavLink> : null}
        {isPlatformAdmin && <details className={styles.adminMenu}>
          <summary className={styles.navLink}>Администрирование ▾</summary>
          <div className={styles.adminLinks}>
            <NavLink className={navLinkClassName} to="/admin/users">Пользователи</NavLink>
            <NavLink className={navLinkClassName} to="/admin/organizations">Организации</NavLink>
            <NavLink className={navLinkClassName} to="/admin/registration-requests">Заявки организаций</NavLink>
            <NavLink className={navLinkClassName} to="/admin/sports">Виды спорта</NavLink>
            <NavLink className={navLinkClassName} to="/admin/registrations/review">Проверка заявок</NavLink>
          </div>
        </details>}
        {isAuthenticated ? <details className={styles.adminMenu}>
          <summary className={styles.navLink}>{name || 'Профиль'} <small>{roleLabel}</small></summary>
          <div className={styles.adminLinks}>
            <NavLink className={navLinkClassName} to="/account/password">Сменить пароль</NavLink>
            <button className={`${styles.navLink} ${styles.navButton}`} type="button" onClick={handleLogout}>Выйти</button>
          </div>
        </details> : <NavLink className={navLinkClassName} to="/login">Войти</NavLink>}
      </nav>
    </header>
    <main className={styles.content}>{sessionMessage && <p role="alert">{sessionMessage}</p>}<Outlet /></main>
  </div>
}
