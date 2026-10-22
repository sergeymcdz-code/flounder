import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { acceptInvitation, getInvitation, memberRoleNames, type Invitation } from '../../api/invitations.api'
import styles from './Organization.module.css'

export function InvitationPage() {
  const { token = '' } = useParams()
  return <InvitationDetails key={token} token={token} />
}

function InvitationDetails({ token }: { token: string }) {
  const { session, isAuthenticated, refreshProfile, selectOrg } = useAuth()
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()
  useEffect(() => {
    let active = true
    getInvitation(token).then(data => { if (active) setInvitation(data) })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Не удалось загрузить приглашение') })
    return () => { active = false }
  }, [token])
  const next = encodeURIComponent(`/invitations/${token}`)
  async function accept() {
    if (!session || busy) return
    setBusy(true); setError('')
    try {
      const member = await acceptInvitation(session.accessToken, token)
      // Persist before refreshing so the newly added membership becomes the selected organization.
      localStorage.setItem('flounder.current-org', member.organizationId)
      await refreshProfile()
      selectOrg(member.organizationId)
      navigate(member.memberRole === 'member' ? '/my/registrations' : '/organizer/events', { replace: true })
    } catch (err) { setError(err instanceof Error ? err.message : 'Не удалось принять приглашение') }
    finally { setBusy(false) }
  }
  return <section className={styles.page}>
    <h1>Приглашение в организацию</h1>
    {error && <p role="alert">{error}</p>}
    {!invitation && !error && <p role="status">Загружаем приглашение…</p>}
    {invitation && <>
      <h2>{invitation.organizationName}</h2><p>Роль: {memberRoleNames[invitation.memberRole]}</p>
      <p>Приглашение для: {invitation.email}</p><p>Действует до {new Date(invitation.expiresAt).toLocaleDateString('ru-RU')}</p>
      {isAuthenticated ? <button type="button" disabled={busy} onClick={() => void accept()}>{busy ? 'Принимаем…' : 'Принять приглашение'}</button>
        : <div className={styles.actions}><Link to={`/login?next=${next}`}>Войти</Link><Link to={`/register?next=${next}`}>Зарегистрироваться</Link></div>}
    </>}
  </section>
}
