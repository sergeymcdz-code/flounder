import { Link, Outlet } from 'react-router'
import { ThemeToggle } from '../../components/ThemeToggle'
import styles from './Calendar.module.css'
export function DirectoryLayout() {
  return <div className={styles.root}>
    <a className={styles.skip} href="#public-content">К содержимому</a>
    <header className={styles.header}>
      <Link className={styles.brand} to="/">SportGate</Link>
      <nav aria-label="Основная навигация"><Link to="/">Календарь</Link><Link to="/organizer">Кабинет</Link></nav>
      <ThemeToggle />
    </header>
    <main id="public-content" className={styles.main}><Outlet /></main>
    <footer className={styles.footer}>SportGate · sportgate.pro</footer>
  </div>
}
