import { useEffect, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { getOrganizations, type OrganizationResponse } from '../../api/organizations.api'
import { getNotificationSettings, putNotificationSettings, templateLabels, type NotificationSetting } from '../../api/notifications.api'
import styles from './Notifications.module.css'

export function NotificationSettingsPage() {
  const token = useAuth().session?.accessToken ?? ''
  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([])
  const [organizationId, setOrganizationId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    getOrganizations(token).then(rows => {
      if (active) { setOrganizations(rows); setOrganizationId(rows[0]?.id ?? '') }
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Не удалось загрузить организации.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token])
  return <section className={styles.page}>
    <h1>Настройки уведомлений</h1>
    <p>Выберите письма участникам. Настройки доступны владельцу и администратору организации.</p>
    {loading && <p role="status">Загружаем организации…</p>}
    {error && <p role="alert">{error}</p>}
    {!loading && !error && organizations.length === 0 && <p>У вас пока нет организаций.</p>}
    {organizations.length > 0 && <label>Организация <select value={organizationId} onChange={e => setOrganizationId(e.target.value)}>
      {organizations.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
    </select></label>}
    {organizationId && <SettingsForm key={`${token}:${organizationId}`} token={token} organizationId={organizationId} />}
  </section>
}

function SettingsForm({ token, organizationId }: { token: string; organizationId: string }) {
  const [settings, setSettings] = useState<NotificationSetting[] | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => {
    let active = true
    getNotificationSettings(token, organizationId).then(rows => { if (active) setSettings(rows) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Не удалось загрузить настройки.') })
    return () => { active = false }
  }, [token, organizationId])
  async function save() {
    if (!settings) return
    setSaving(true); setError(''); setMessage('')
    try { setSettings(await putNotificationSettings(token, organizationId, settings)); setMessage('Настройки сохранены.') }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось сохранить настройки.') }
    finally { setSaving(false) }
  }
  return <div className={styles.page}>
    {error && <p role="alert">{error}</p>}
    {!settings && !error && <p role="status">Загружаем настройки…</p>}
    {settings && <form onSubmit={e => { e.preventDefault(); void save() }}>
      <fieldset disabled={saving} className={styles.options}><legend>Письма участникам</legend>
        {settings.map(s => <label key={s.templateCode}><input type="checkbox" checked={s.enabled}
          onChange={e => { setMessage(''); setSettings(rows => rows?.map(row => row.templateCode === s.templateCode ? { ...row, enabled: e.target.checked } : row) ?? null) }} />
          {templateLabels[s.templateCode] ?? s.templateCode}</label>)}
      </fieldset>
      <button className="fl-btn fl-btn-secondary" type="submit" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить'}</button>
    </form>}
    {message && <p role="status">{message}</p>}
  </div>
}
