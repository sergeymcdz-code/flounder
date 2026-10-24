import { ErrorState } from '../../components/ErrorState'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import styles from './OrganizerLayout.module.css'
import { useCabinetNavigation } from '../../components/CabinetNavigation'
import { useAuth } from '../../app/providers/AuthProvider'

export function OrganizerLayout() {
  const { isOrganizer, isPlatformAdmin, isOperator, hasRole } = useAuth()
  const items = useCabinetNavigation()
  const location = useLocation()
  if (hasRole('judge') && !isOrganizer && !isPlatformAdmin && !isOperator) {
    return /\/organizer\/events\/[^/]+\/results\/?$/.test(location.pathname) ? <Outlet /> : <div><ErrorState title="Нет доступа" message="Судье доступен раздел результатов соревнования." /><Link to="/operator/results">Судейство</Link></div>
  }
  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <p className={`fl-eyebrow ${styles.eyebrow}`}>{isPlatformAdmin ? 'Администрирование' : isOrganizer ? 'Кабинет организатора' : 'Кабинет секретаря'}</p>
        </div>

        <nav className={styles.navigation} aria-label="Навигация организатора">
          {items.map((item) => (
            <NavLink
              key={item.to}
              className={({ isActive }) =>
                [
                  styles.navigationLink,
                  isActive ? styles.navigationLinkActive : '',
                ]
                  .filter(Boolean)
                  .join(' ')
              }
              end={item.end}
              to={item.to}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <NavLink className={styles.publicLink} to="/">
          Публичный календарь ↗
        </NavLink>
      </aside>

      <div className={styles.content}>
        <Outlet />
      </div>
    </div>
  )
}