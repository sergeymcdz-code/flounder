import { ErrorState } from '../../components/ErrorState'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import styles from './OrganizerLayout.module.css'
import { useAuth } from '../../app/providers/AuthProvider'

type NavigationItem = {
  to: string
  label: string
  end?: boolean
}

const navigationItems: NavigationItem[] = [
  {
    to: '/organizer/events',
    label: 'Соревнования',
    end: true,
  },
  {
    to: '/organizer/events/new',
    label: 'Создать соревнование',
  },
  {
    to: '/organizer/regulations/create',
    label: 'Регламенты',
  },
  { to: '/organizer/notification-settings', label: 'Настройки' },
]

export function OrganizerLayout() {
  const { isOrganizer, isPlatformAdmin, isOperator, hasRole, memberships = [], currentOrgId } = useAuth()
  const current = memberships.find(m => m.organizationId === currentOrgId)
  const canCreate = isPlatformAdmin || !!current && ['owner', 'admin', 'organizer'].includes(current.memberRole)
  const canManageOrg = !!current && ['owner', 'admin'].includes(current.memberRole)
  const location = useLocation()
  if (hasRole('judge') && !isOrganizer && !isPlatformAdmin && !isOperator) {
    return /\/organizer\/events\/[^/]+\/results\/?$/.test(location.pathname) ? <Outlet /> : <div><ErrorState title="Нет доступа" message="Судье доступен раздел результатов соревнования." /><Link to="/operator/results">Судейство</Link></div>
  }
  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <p className={styles.eyebrow}>{isPlatformAdmin ? 'Администрирование' : isOrganizer ? 'Кабинет организатора' : 'Кабинет секретаря'}</p>
        </div>

        <nav className={styles.navigation} aria-label="Навигация организатора">
          {[...(isOrganizer || isPlatformAdmin ? navigationItems.filter(item => item.to !== '/organizer/events/new' || canCreate) : navigationItems.slice(0, 1)), ...(canManageOrg ? [{ to: '/organizer/organization', label: 'Организация' }] : []), ...(isOrganizer || isPlatformAdmin ? [{ to: '/organizer/organizations', label: 'Аналитика' }] : [])].map((item) => (
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
          Публичный каталог ↗
        </NavLink>
      </aside>

      <div className={styles.content}>
        <Outlet />
      </div>
    </div>
  )
}