import { useEffect, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { getInvitations, invitationStatusNames, memberRoleNames, type OrganizationInvitation } from '../../api/invitations.api'

export function OrganizationInvitationList({ organizationId }: { organizationId: string }) {
  return <InvitationList key={organizationId} organizationId={organizationId} />
}

function InvitationList({ organizationId }: { organizationId: string }) {
  const { session } = useAuth()
  const token = session?.accessToken ?? ''
  const [items, setItems] = useState<OrganizationInvitation[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    getInvitations(token, organizationId).then(data => { if (active) setItems(data) })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Не удалось загрузить приглашения') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, organizationId])
  return <section><h2>Приглашения организации</h2>
    {loading ? <p role="status">Загружаем приглашения…</p> : error ? <p role="alert">{error}</p> : items.length ? <ul>{items.map(i =>
      <li key={i.id}>{i.email} — {memberRoleNames[i.memberRole]} — {invitationStatusNames[i.status]}</li>)}</ul> : <p>Приглашений пока нет.</p>}
  </section>
}
