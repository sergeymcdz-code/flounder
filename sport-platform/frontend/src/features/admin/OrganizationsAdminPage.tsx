import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { apiFetch } from '../../api/http'
import { addMember, createOrganization, deleteMember, getMembers, getOrganization, getOrganizations, updateMember,
  type OrganizationMember, type OrganizationResponse } from '../../api/organizations.api'
import type { Event } from '../../api/events.api'
import { AdminDialog } from './AdminDialog'
import { adminError } from './admin-utils'
import styles from './Admin.module.css'

const memberRoles = [ ['owner', 'Владелец'], ['admin', 'Администратор'], ['member', 'Участник'],
  ['organizer', 'Организатор'], ['operator', 'Оператор'], ['judge', 'Судья'] ]
function MemberRoleSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label>Роль в организации<select aria-label="Роль в организации" value={value} onChange={event => onChange(event.target.value)}>
    {memberRoles.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
  </select></label>
}

export function OrganizationsAdminPage() {
  const { session } = useAuth()
  const token = session?.accessToken ?? ''
  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    getOrganizations(token).then(result => { if (active) setOrganizations(result) })
      .catch(err => { if (active) setError(adminError(err)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, reload])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const text = (name: string) => String(values.get(name) ?? '').trim()
    setBusy(true); setFormError('')
    try {
      await createOrganization(token, { type: text('type'), name: text('name'), city: text('city') || undefined, legalName: text('legalName') || undefined,
        inn: text('inn') || undefined, contactEmail: text('contactEmail') || undefined, contactPhone: text('contactPhone') || undefined })
      setOpen(false); setReload(value => value + 1)
    } catch (err) { setFormError(adminError(err)) } finally { setBusy(false) }
  }
  return <section className={styles.page}>
    <div className={styles.heading}><h1>Организации</h1><button onClick={() => { setOpen(true); setFormError('') }}>Создать организацию</button></div>
    {loading ? <p role="status">Загружаем организации…</p> : error ? <p role="alert" className={styles.error}>{error} <button onClick={() => setReload(value => value + 1)}>Повторить</button></p>
      : !organizations.length ? <p>Организаций пока нет — создайте первую.</p> : <table className={styles.table}>
        <thead><tr><th>Название</th><th>Город</th><th>Владелец</th><th>Членов / события</th></tr></thead>
        <tbody>{organizations.map(org => <tr key={org.id}><td><Link to={`/admin/organizations/${org.id}`}>{org.name}</Link></td>
          <td>{org.city || '—'}</td><td>{org.owner || 'Не назначен'}</td><td>{org.memberCount ?? '—'}<br /><Link to={`/admin/organizations/${org.id}#events`}>События</Link></td></tr>)}</tbody>
      </table>}
    {open && <AdminDialog title="Создать организацию" busy={busy} onClose={() => setOpen(false)}>
      <form className={styles.form} onSubmit={submit}><fieldset className={styles.form} disabled={busy}>
        <label>Название<input name="name" maxLength={255} required /></label>
        <label>Тип организации<select name="type" aria-label="Тип организации"><option value="club">Клуб</option><option value="school">Школа</option><option value="federation">Федерация</option><option value="other">Другое</option></select></label>
        <label>Город<input name="city" maxLength={128} /></label>
        <label>Юридическое название<input name="legalName" maxLength={255} /></label>
        <label>ИНН<input name="inn" maxLength={32} /></label>
        <label>Контактный email<input name="contactEmail" type="email" /></label>
        <label>Контактный телефон<input name="contactPhone" maxLength={32} /></label>
      </fieldset>{formError && <p className={styles.error} role="alert">{formError}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Создаём…' : 'Создать'}</button>
      </form>
    </AdminDialog>}
  </section>
}

type MemberAction = { type: 'add' } | { type: 'edit' | 'delete'; member: OrganizationMember }
export function OrganizationAdminPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const token = session?.accessToken ?? ''
  const [organization, setOrganization] = useState<OrganizationResponse | null>(null)
  const [members, setMembers] = useState<OrganizationMember[]>([])
  const [events, setEvents] = useState<Event[]>([])
  const [eventError, setEventError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [action, setAction] = useState<MemberAction | null>(null)
  const [role, setRole] = useState('member')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    Promise.all([getOrganization(token, id), getMembers(token, id)]).then(([org, people]) => {
      if (active) { setOrganization(org); setMembers(people) }
    }).catch(err => { if (active) setError(adminError(err)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, id, reload])
  useEffect(() => {
    let active = true
    setEventError('')
    apiFetch<Event[]>('/api/v1/events', { accessToken: token }).then(result => {
      if (active) setEvents(result.filter(event => event.organizationId === id))
    }).catch(err => { if (active) setEventError(adminError(err)) })
    return () => { active = false }
  }, [token, id, reload])
  function open(next: MemberAction) { setAction(next); setRole(next.type === 'edit' ? next.member.memberRole : 'member'); setFormError('') }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!action || busy) return
    const email = String(new FormData(event.currentTarget).get('email') ?? '').trim()
    setBusy(true); setFormError('')
    try {
      if (action.type === 'add') await addMember(token, id, email, role)
      else if (action.type === 'edit') await updateMember(token, id, action.member.userId, role)
      else await deleteMember(token, id, action.member.userId)
      setAction(null); setReload(value => value + 1)
    } catch (err) { setFormError(adminError(err)) } finally { setBusy(false) }
  }
  return <section className={styles.page}><Link to="/admin/organizations">← Организации</Link>
    <div className={styles.heading}><h1>{organization?.name ?? 'Организация'}</h1><button disabled={loading || !!error} onClick={() => open({ type: 'add' })}>Добавить участника</button></div>
    {loading ? <p role="status">Загружаем участников…</p> : error ? <p role="alert" className={styles.error}>{error} <button onClick={() => setReload(value => value + 1)}>Повторить</button></p>
      : !members.length ? <p>Участников пока нет — добавьте пользователя по email.</p> : <table className={styles.table}>
        <thead><tr><th>Email</th><th>ФИО</th><th>Роль в организации</th><th>Действия</th></tr></thead>
        <tbody>{members.map(member => <tr key={member.userId}><td>{member.email}</td><td>{member.lastName} {member.firstName}</td>
          <td>{memberRoles.find(([code]) => code === member.memberRole)?.[1] ?? member.memberRole}</td><td className={styles.actions}>
            <button onClick={() => open({ type: 'edit', member })}>Изменить роль</button><button onClick={() => open({ type: 'delete', member })}>Удалить</button></td></tr>)}</tbody>
      </table>}
    <h2 id="events">События организации</h2>
    {eventError ? <p role="alert" className={styles.error}>{eventError}</p> : events.length ? <ul>{events.map(event =>
      <li key={event.id}><Link to={`/organizer/events/${event.id}`}>{event.title}</Link></li>)}</ul> : <p>Событий пока нет.</p>}
    {action && <AdminDialog title={action.type === 'add' ? 'Добавить участника' : action.type === 'edit' ? 'Изменить роль' : 'Удалить участника'} busy={busy} onClose={() => setAction(null)}>
      <form className={styles.form} onSubmit={submit}><fieldset disabled={busy} className={styles.form}>
        {action.type === 'add' ? <label>Email пользователя<input name="email" type="email" required /></label> : <p>{action.member.email}</p>}
        {action.type === 'delete' ? <p>Удалить пользователя из организации?</p> : <MemberRoleSelect value={role} onChange={setRole} />}
      </fieldset>{formError && <p className={styles.error} role="alert">{formError}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : action.type === 'delete' ? 'Удалить' : 'Сохранить'}</button>
      </form>
    </AdminDialog>}
  </section>
}
