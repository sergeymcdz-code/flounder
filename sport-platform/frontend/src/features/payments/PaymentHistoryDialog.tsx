import { useEffect, useState } from 'react'
import { OrganizerDialog } from '../organizer/OrganizerDialog'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { ApiError } from '../../api/http'
import { paymentHistory, money, type PaymentLog } from '../../api/registration-payments.api'
import styles from './OrganizerPayments.module.css'

export function PaymentHistoryDialog({ id, token, onClose }: { id: string; token: string; onClose: () => void }) {
  const [rows, setRows] = useState<PaymentLog[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    paymentHistory(id, token).then(value => { if (active) setRows(value) }).catch(err => {
      if (active) setError(err instanceof TypeError ? 'Нет связи. Проверьте интернет и повторите.' : err instanceof ApiError ? `Сервер ответил ошибкой (${err.status}). ${err.message}` : 'Не удалось загрузить историю оплаты.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, token, reload])
  const labels: Record<string, string> = { MARK_PAID: 'Оплата', WAIVE: 'Освобождение', REFUND: 'Возврат', RESET: 'Сброс' }
  return <OrganizerDialog title="История оплаты" onClose={onClose}>
    {error && <Notice tone="danger">{error} <Button onClick={() => setReload(value => value + 1)}>Повторить</Button></Notice>}
    {loading && <div role="status"><p>Загружаем историю оплаты…</p><div className="fl-skeleton" aria-hidden="true"><div /><div /><div /></div></div>}
    {!loading && !rows.length && !error && <EmptyState title="Операций пока нет" action={<Button onClick={onClose}>Закрыть</Button>}><p>Здесь появятся отметки оплаты, возвраты и освобождения от взноса.</p></EmptyState>}
    {!!rows.length && <ol className={styles.history}>{rows.map(row => <li key={row.id}>
      <strong>{labels[row.action] || 'Операция оплаты'}</strong> · <span className="fl-numeric">{money(row.amount, row.currency || 'RUB')}</span>
      <p><time dateTime={row.performedAt}>{new Date(row.performedAt).toLocaleString('ru-RU')}</time> · Исполнитель: {row.performedByName || 'Не указан'}</p>
      {row.reference && <p>Документ: {row.reference}</p>}{row.comment && <p>{row.comment}</p>}
    </li>)}</ol>}
  </OrganizerDialog>
}
