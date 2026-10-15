import { useEffect, useState } from 'react'
import { OrganizerDialog } from '../organizer/OrganizerDialog'
import {
  paymentHistory,
  money,
  type PaymentLog,
} from '../../api/registration-payments.api'
export function PaymentHistoryDialog({
  id,
  token,
  onClose,
}: {
  id: string
  token: string
  onClose: () => void
}) {
  const [rows, setRows] = useState<PaymentLog[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    paymentHistory(id, token)
      .then((r) => {
        if (active) setRows(r)
      })
      .catch((e) => {
        if (active)
          setError(
            e instanceof Error ? e.message : 'Не удалось загрузить историю',
          )
      })
    return () => {
      active = false
    }
  }, [id, token])
  const labels: Record<string, string> = {
    MARK_PAID: 'Оплата',
    WAIVE: 'Освобождение',
    REFUND: 'Возврат',
    RESET: 'Сброс',
  }
  return (
    <OrganizerDialog title="История оплаты" onClose={onClose}>
      {error && <p role="alert">{error}</p>}
      {!rows.length && !error && <p>Операций пока нет</p>}
      {rows.map((r) => (
        <p key={r.id}>
          {new Date(r.performedAt).toLocaleString('ru-RU')} · {labels[r.action]}{' '}
          · {money(r.amount, r.currency ?? 'RUB')} · {r.reference} · {r.comment}
        </p>
      ))}
    </OrganizerDialog>
  )
}
