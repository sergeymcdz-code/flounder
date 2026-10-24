import { useEffect, useId, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'
import { apiFetch } from '../api/http'
import { roleHomeTarget } from '../utils/authReturn'
import { OrganizationSwitcher } from './OrganizationSwitcher'
import { ThemeToggle } from './ThemeToggle'
import { Button } from './ui/Button'
import { MenuIcon } from './ui/icons'
import { adminNavigation, useCabinetNavigation } from './CabinetNavigation'
import styles from './AppLayout.module.css'

const subscribeResize = (listener: () => void) => { window.addEventListener('resize', listener); return () => window.removeEventListener('resize', listener) }
const desktopSnapshot = () => window.innerWidth >= 768

export function ProductHeader({ publicPage = false, eventContext }: { publicPage?: boolean; eventContext?: ReactNode }) {
  const { isAuthenticated, isPlatformAdmin, isOrganizer, isOperator, isParticipant, hasRole, logout, session, roles } = useAuth()
  const desktop = useSyncExternalStore(subscribeResize, desktopSnapshot)
  const [name, setName] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const menu = useRef<HTMLDetailsElement>(null)
  const drawer = useRef<HTMLDialogElement>(null)
  const burger = useRef<HTMLButtonElement>(null)
  const drawerId = useId()
  const cabinetNavigation = useCabinetNavigation()
  const canManage = isPlatformAdmin || isOrganizer || isOperator
  const judge = hasRole('judge')
  const role = isPlatformAdmin ? 'Администратор' : isOrganizer ? 'Организатор' : isOperator ? 'Секретарь' : judge ? 'Судья' : 'Участник'
  const sections = [
    { to: '/', label: 'Соревнования', end: true },
    ...(isParticipant ? [{ to: '/my/registrations', label: 'Мои заявки' }] : []),
    ...(canManage ? [{ to: '/organizer/events', label: 'Мои соревнования' }] : []),
    ...(judge ? [{ to: '/operator/results', label: 'Судейство' }] : []),
    ...(isPlatformAdmin ? [{ to: '/admin/users', label: 'Администрирование' }] : []),
  ]
  const cabinet = !publicPage && isAuthenticated && (canManage || judge)
  const sidebar = location.pathname.startsWith('/admin/') ? adminNavigation : cabinetNavigation
  useEffect(() => {
    if (!session) return
    let active = true
    apiFetch<{ email: string; profile?: { firstName?: string } }>('/api/v1/auth/me', { accessToken: session.accessToken })
      .then(user => { if (active) setName(user.profile?.firstName || user.email.split('@')[0]) }).catch(() => {})
    return () => { active = false }
  }, [session])
  function closeDrawer() { drawer.current?.close(); setDrawerOpen(false); burger.current?.focus() }
  useEffect(() => { menu.current?.removeAttribute('open'); drawer.current?.close() }, [location.pathname, location.search])
  useEffect(() => {
    function closeMenu(event: PointerEvent | KeyboardEvent) {
      if (!menu.current?.open) return
      if (event instanceof KeyboardEvent) {
        if (event.key !== 'Escape') return
      } else if (menu.current.contains(event.target as Node)) return
      menu.current.removeAttribute('open')
      menu.current.querySelector('summary')?.focus()
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeMenu)
    const media = window.matchMedia?.('(min-width: 768px)')
    const resize = () => { if (media?.matches) drawer.current?.close() }
    media?.addEventListener('change', resize)
    return () => { document.removeEventListener('pointerdown', closeMenu); document.removeEventListener('keydown', closeMenu); media?.removeEventListener('change', resize) }
  }, [])
  useEffect(() => {
    if (!drawerOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous }
  }, [drawerOpen])
  function handleLogout() { closeDrawer(); void logout().catch(() => {}); navigate('/', { replace: true }) }
  const navClass = ({ isActive }: { isActive: boolean }) => `${styles.navLink}${isActive ? ` ${styles.navLinkActive}` : ''}`
  const links = sections.map(item => <NavLink key={item.to} className={navClass} to={item.to} end={item.end}>{item.label}</NavLink>)
  return <>
    <header className={styles.header}>
      <NavLink className={styles.brand} to={roleHomeTarget(roles, isAuthenticated)} aria-label="На главную"><span className={styles.logo} aria-hidden="true">S</span>SportGate</NavLink>
      {eventContext && desktop && <div className={styles.headerContext}>{eventContext}</div>}
      <nav className={`${styles.navigation} fl-scroll-x`} aria-label="Основная навигация">{links}</nav>
      <div className={`${styles.userMenu} ${cabinet ? styles.cabinetUser : ''}`}>
        {!publicPage && <OrganizationSwitcher />}
        {isAuthenticated ? <details ref={menu} className={styles.profileMenu} onToggle={event => setMenuOpen(event.currentTarget.open)}>
          <summary className={styles.navLink}>{name || 'Профиль'} <small>{role}</small><span aria-hidden="true">▾</span></summary>
          {menuOpen && <div className={styles.profileLinks}>
            {links}
            <NavLink className={navClass} to="/account/password">Сменить пароль</NavLink>
            {!publicPage && <ThemeToggle />}
            <Button variant="ghost" onClick={handleLogout}>Выйти</Button>
          </div>}
        </details> : <Button as={Link} to="/login" variant="ghost">Войти</Button>}
      </div>
      {publicPage && <ThemeToggle compact />}
      {cabinet && <Button ref={burger} className={styles.burger} variant="ghost" aria-label="Открыть меню" aria-expanded={drawerOpen} aria-controls={drawerId} onClick={() => { drawer.current?.showModal(); setDrawerOpen(true) }}><MenuIcon /></Button>}
    </header>
    {eventContext && !desktop && <div className={styles.mobileContext}>{eventContext}</div>}
    {cabinet && <dialog ref={drawer} id={drawerId} className={styles.drawer} aria-label="Меню кабинета" onClose={() => { setDrawerOpen(false); burger.current?.focus() }} onCancel={closeDrawer} onClick={event => { if (event.target === drawer.current) { const bounds = drawer.current.getBoundingClientRect(); if (event.clientX < bounds.left) closeDrawer() } }}>
      {drawerOpen && <><div className={styles.drawerHeading}><strong>Меню</strong><Button variant="ghost" aria-label="Закрыть меню" onClick={closeDrawer}>×</Button></div>
      <nav className={styles.drawerGroup} aria-label="Разделы"><p>Разделы</p>{links}</nav>
      <nav className={styles.drawerGroup} aria-label="Навигация кабинета"><p>Кабинет</p>{sidebar.map(item => <NavLink key={item.to} className={navClass} to={item.to} end={item.end}>{item.label}</NavLink>)}</nav>
      <div className={styles.drawerGroup}><p>{name || 'Профиль'} · {role}</p><OrganizationSwitcher /><NavLink className={navClass} to="/account/password">Сменить пароль</NavLink><ThemeToggle /><Button variant="ghost" onClick={handleLogout}>Выйти</Button></div>
    </>}</dialog>}
  </>
}
