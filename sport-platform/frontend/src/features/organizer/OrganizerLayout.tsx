import { NavLink, Outlet } from 'react-router'
import styles from './OrganizerLayout.module.css'
import { useAuth } from '../../app/providers/AuthProvider'

type NavigationItem = {
  to: string
  label: string
  end?: boolean
}

const navigationItems: NavigationItem[] = [
  {
    to: '/organizer',
    label: 'Обзор',
    end: true,
  },
  {
    to: '/organizer/events',
    label: 'Мероприятия',
    end: true,
  },
  {
    to: '/organizer/events/new',
    label: 'Создать мероприятие',
  },
  {
    to: '/organizer/regulations/create',
    label: 'Регламенты',
  },
  { to: '/organizer/notification-settings', label: 'Настройки уведомлений' },
]

export function OrganizerLayout() {
  const { isOrganizer, isPlatformAdmin, memberships = [], currentOrgId } = useAuth()
  const current = memberships.find(m => m.organizationId === currentOrgId)
  const canCreate = isPlatformAdmin || !!current && ['owner', 'admin', 'organizer'].includes(current.memberRole)
  const canManageOrg = !!current && ['owner', 'admin'].includes(current.memberRole)
  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <p className={styles.eyebrow}>Панель организатора</p>
          <h2 className={styles.sidebarTitle}>Кабинет организатора</h2>
        </div>

        <nav className={styles.navigation} aria-label="Навигация организатора">
          {[...navigationItems.filter(item => item.to !== '/organizer/events/new' || canCreate), ...(canManageOrg ? [{ to: '/organizer/organization', label: 'Организация' }] : []), ...(isOrganizer || isPlatformAdmin ? [{ to: '/organizer/organizations', label: 'Аналитика организаций' }] : [])].map((item) => (
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
          Перейти в публичный каталог
        </NavLink>
      </aside>

      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}