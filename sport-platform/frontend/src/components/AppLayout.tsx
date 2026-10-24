import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'
import { HeaderContext } from './header-context'
import { ProductHeader } from './ProductHeader'
import { adminNavigation } from './CabinetNavigation'
import styles from './AppLayout.module.css'

export function AppLayout() {
  useEffect(() => { document.title = 'SportGate' }, [])
  const { sessionMessage } = useAuth()
  const { pathname } = useLocation()
  const [eventContext, setEventContext] = useState<ReactNode>(null)
  const admin = pathname.startsWith('/admin/')
  return <HeaderContext value={setEventContext}><div className={styles.shell}>
    <ProductHeader eventContext={eventContext} />
    <main className={styles.content}>
      {sessionMessage && <p role="alert">{sessionMessage}</p>}
      {admin ? <div className={styles.adminLayout}><aside className={styles.adminSidebar}><nav aria-label="Навигация администратора">{adminNavigation.map(item => <NavLink key={item.to} to={item.to}>{item.label}</NavLink>)}</nav></aside><div className={styles.outlet}><Outlet /></div></div> : <Outlet />}
    </main>
  </div></HeaderContext>
}
