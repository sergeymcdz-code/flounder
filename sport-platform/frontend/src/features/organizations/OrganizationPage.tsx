import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { apiFetch } from '../../api/http'
import { getOrganization, getMembers, updateMember, deleteMember, type OrganizationResponse, type OrganizationMember } from '../../api/organizations.api'
import { getInvitations, createInvitation, revokeInvitation, memberRoleNames, invitationStatusNames, type OrganizationInvitation } from '../../api/invitations.api'
import { AdminDialog } from '../admin/AdminDialog'
import styles from './Organization.module.css'

const editableRoles = Object.entries(memberRoleNames).filter(([code]) => code !== 'owner')
export function OrganizationPage() {
  const { currentOrgId } = useAuth()
  return <OrganizationEditor key={currentOrgId} />
}

function OrganizationEditor() {
  const { session, memberships = [], currentOrgId, userId, refreshProfile } = useAuth()
  const token = session?.accessToken ?? ''
  const current = memberships.find(m => m.organizationId === currentOrgId)
  const allowed = !!current && ['owner', 'admin'].includes(current.memberRole)
  const [org, setOrg] = useState<OrganizationResponse | null>(null)
  const [members, setMembers] = useState<OrganizationMember[]>([])
  const [invitations, setInvitations] = useState<OrganizationInvitation[]>([])
  const [reload, setReload] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [removing, setRemoving] = useState<OrganizationMember | null>(null)
  useEffect(() => {
    if (!allowed) return
    let active = true
    Promise.all([getOrganization(token, currentOrgId), getMembers(token, currentOrgId), getInvitations(token, currentOrgId)])
      .then(([organization, people, pending]) => { if (active) { setOrg(organization); setMembers(people); setInvitations(pending) } })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Не удалось загрузить организацию') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, currentOrgId, allowed, reload])
  async function mutate(action: () => Promise<unknown>, message: string) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await action(); setNotice(message); setRemoving(null); setLoading(true); setReload(v => v + 1) }
    catch (err) { setError(err instanceof Error ? err.message : 'Не удалось сохранить изменения') }
    finally { setBusy(false) }
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const body = Object.fromEntries(values)
    void mutate(async () => {
      await apiFetch(`/api/v1/organizations/${currentOrgId}`, { accessToken: token, method: 'PATCH', body: JSON.stringify(body) })
      await refreshProfile()
    }, 'Реквизиты сохранены')
  }
  function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    void mutate(async () => {
      await createInvitation(token, currentOrgId, String(values.get('email')).trim(), String(values.get('memberRole')))
      form.reset()
    }, 'Приглашение отправлено')
  }
  if (!allowed) return <p role="alert">Управлять организацией могут только её владелец и администратор.</p>
  return <section className={styles.page}>
    <h1>Организация: {current.organizationName}</h1>
    {error && <p role="alert">{error} <button onClick={() => { setLoading(true); setError(''); setReload(v => v + 1) }}>Повторить</button></p>}
    {notice && <p role="status">{notice}</p>}
    {loading ? <p role="status">Загружаем организацию…</p> : org && <>
      <h2>Реквизиты</h2>
      <form className={styles.form} key={`${org.id}-${reload}`} onSubmit={save}>
        <fieldset disabled={busy} className={styles.form}>
          <label>Название<input name="name" defaultValue={org.name} maxLength={255} required /></label>
          <label>Тип организации<select name="type" defaultValue={org.type}><option value="club">Клуб</option><option value="school">Школа</option><option value="federation">Федерация</option><option value="other">Другое</option></select></label>
          <label>Город<input name="city" defaultValue={org.city ?? ''} maxLength={128} /></label>
          <label>Юридическое название<input name="legalName" defaultValue={org.legalName ?? ''} maxLength={255} /></label>
          <label>ИНН<input name="inn" defaultValue={org.inn ?? ''} maxLength={32} /></label>
          <label>Контактный email<input name="contactEmail" type="email" defaultValue={org.contactEmail ?? ''} maxLength={255} /></label>
          <label>Контактный телефон<input name="contactPhone" defaultValue={org.contactPhone ?? ''} maxLength={32} /></label>
          <button type="submit">Сохранить реквизиты</button>
        </fieldset>
      </form>
      <h2>Сотрудники</h2>
      <ul className={styles.list}>{members.map(member => {
        const protectedMember = member.userId === userId || member.memberRole === 'owner'
        return <li className={styles.item} key={member.id}>
          <strong>{member.lastName} {member.firstName}</strong><span>{member.email}</span>
          {protectedMember ? <span>{memberRoleNames[member.memberRole]}</span> : <div className={styles.actions}>
            <label>Роль: <select aria-label={`Роль ${member.email}`} disabled={busy} value={member.memberRole}
              onChange={e => void mutate(() => updateMember(token, currentOrgId, member.userId, e.target.value), 'Роль изменена')}>
              {editableRoles.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
            </select></label><button disabled={busy} onClick={() => setRemoving(member)}>Удалить</button>
          </div>}
        </li>
      })}</ul>
      <h2>Пригласить по e-mail</h2>
      <form className={styles.form} onSubmit={invite}><fieldset disabled={busy} className={styles.form}>
        <label>E-mail<input name="email" type="email" maxLength={255} required /></label>
        <label>Роль<select name="memberRole" defaultValue="operator">{editableRoles.map(([code, name]) => <option key={code} value={code}>{name}</option>)}</select></label>
        <button type="submit">Отправить приглашение</button>
      </fieldset></form>
      <h2>Приглашения</h2>
      {!invitations.length && <p>Приглашений пока нет.</p>}
      <ul className={styles.list}>{invitations.map(invitation => <li className={styles.item} key={invitation.id}>
        <span>{invitation.email} — {memberRoleNames[invitation.memberRole]}</span><span>{invitationStatusNames[invitation.status]}</span>
        {invitation.status === 'PENDING' && <button disabled={busy} onClick={() => void mutate(() => revokeInvitation(token, currentOrgId, invitation.id), 'Приглашение отозвано')}>Отозвать</button>}
      </li>)}</ul>
    </>}
    {removing && <AdminDialog title="Удалить сотрудника" busy={busy} onClose={() => setRemoving(null)}>
      <p>Удалить {removing.email} из организации?</p>
      <button disabled={busy} onClick={() => void mutate(() => deleteMember(token, currentOrgId, removing.userId), 'Сотрудник удалён')}>Подтвердить удаление</button>
    </AdminDialog>}
  </section>
}
