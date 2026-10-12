import { NavLink, Outlet } from 'react-router'
import styles from './OrganizerLayout.module.css'

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
]

export function OrganizerLayout() {
  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <p className={styles.eyebrow}>Organizer panel</p>
          <h2 className={styles.sidebarTitle}>Кабинет организатора</h2>
        </div>

        <nav className={styles.navigation} aria-label="Навигация организатора">
          {navigationItems.map((item) => (
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