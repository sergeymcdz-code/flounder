import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getNotifications, templateLabels, statusLabels, type NotificationPage } from '../../api/notifications.api'
import { useOrganizerEvent } from './organizer-event-context'
import styles from './Notifications.module.css'

export function OrganizerNotificationsPage() {
  const { eventId = '' } = useParams()
  const { accessToken } = useOrganizerEvent()
  return <Journal key={`${eventId}:${accessToken}`} token={accessToken} eventId={eventId} />
}
function Journal({ token, eventId }: { token: string; eventId: string }) {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [data, setData] = useState<NotificationPage | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    getNotifications(token, eventId, status, page).then(rows => { if (active) setData(rows) })
      .catch(cause => { if (active) { setData(null); setError(cause instanceof Error ? cause.message : 'Не удалось загрузить журнал.') } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, eventId, status, page, refresh])
  return <section className={styles.page}>
    <h2>Уведомления</h2>
    <div className={styles.toolbar}><label>Статус <select value={status} onChange={e => { setLoading(true); setError(''); setStatus(e.target.value); setPage(0) }}>
      <option value="">Все</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select></label><button onClick={() => { setLoading(true); setError(''); setRefresh(n => n + 1) }} disabled={loading}>Обновить</button></div>
    {loading && <p role="status">Загружаем журнал…</p>}
    {error && <p role="alert">{error}</p>}
    {!loading && data && <>
      {data.items.length === 0 ? <p>Уведомлений пока нет.</p> : <div className={styles.tableWrap}><table>
        <thead><tr><th>Создано</th><th>Адресат</th><th>Письмо</th><th>Статус</th><th>Попытки</th><th>Отправлено</th><th>Ошибка</th></tr></thead>
        <tbody>{data.items.map(row => <tr key={row.id}><td>{new Date(row.createdAt).toLocaleString('ru-RU')}</td>
          <td>{row.recipientEmail}</td><td>{templateLabels[row.templateCode] ?? row.templateCode}</td>
          <td>{statusLabels[row.status]}</td><td>{row.attempts}</td><td>{row.sentAt ? new Date(row.sentAt).toLocaleString('ru-RU') : '—'}</td>
          <td>{row.lastError ?? '—'}</td></tr>)}</tbody>
      </table></div>}
      <div className={styles.toolbar}><button disabled={page === 0} onClick={() => { setLoading(true); setError(''); setPage(n => n - 1) }}>Назад</button>
        <span>Страница {page + 1} · Всего: {data.totalElements}</span>
        <button disabled={page + 1 >= data.totalPages} onClick={() => { setLoading(true); setError(''); setPage(n => n + 1) }}>Далее</button></div>
    </>}
  </section>
}
