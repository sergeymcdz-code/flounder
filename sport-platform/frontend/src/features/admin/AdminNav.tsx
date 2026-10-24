import { Tabs } from '../../components/ui/Tabs'
import styles from './Admin.module.css'

export function AdminNav() {
  return <div className={styles.navigation}><Tabs label="Разделы администрирования" items={[
    { to: '/admin/users', label: 'Пользователи' },
    { to: '/admin/organizations', label: 'Организации' },
    { to: '/admin/registration-requests', label: 'Заявки организаций' },
    { to: '/admin/sports', label: 'Виды спорта' },
    { to: '/admin/registrations/review', label: 'Проверка заявок' },
  ]} /></div>
}
