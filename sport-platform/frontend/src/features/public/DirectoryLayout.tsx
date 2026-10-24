import { Outlet } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ProductHeader } from '../../components/ProductHeader'
import styles from './Calendar.module.css'
export function DirectoryLayout() {
  const { sessionMessage } = useAuth()
  return <div className={styles.root}>
    <a className={styles.skip} href="#public-content">К содержимому</a>
    <ProductHeader publicPage />
    <main id="public-content" className={styles.main}>{sessionMessage && <p role="alert">{sessionMessage}</p>}<Outlet /></main>
    <footer className={styles.footer}>SportGate · sportgate.pro</footer>
  </div>
}
