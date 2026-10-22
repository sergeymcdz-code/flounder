import { useAuth } from '../app/providers/AuthProvider'
import styles from './OrganizationSwitcher.module.css'

export function OrganizationSwitcher() {
  const { memberships = [], currentOrgId, selectOrg } = useAuth()
  if (!memberships.length) return null
  if (memberships.length === 1) return <span className={styles.switcher}>{memberships[0].organizationName}</span>
  return <label className={styles.switcher}>Организация:
    <select aria-label="Организация" value={currentOrgId} onChange={e => selectOrg(e.target.value)}>
      {memberships.map(m => <option key={m.organizationId} value={m.organizationId}>{m.organizationName}</option>)}
    </select>
  </label>
}
