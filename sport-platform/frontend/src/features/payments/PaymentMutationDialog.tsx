import { useState } from 'react'
import { OrganizerDialog } from '../organizer/OrganizerDialog'
import { useOrganizerEvent } from '../organizer/organizer-event-context'
import {
  paymentAction,
  bulkPayment,
  methodLabels,
  type PaymentMethod,
  type PaymentAction,
  type BulkResult,
} from '../../api/registration-payments.api'
import styles from '../organizer/OrganizerEventPage.module.css'
export function PaymentMutationDialog({
  ids,
  action = 'mark-paid',
  amount,
  bulk = false,
  onClose,
}: {
  ids: string[]
  action?: PaymentAction
  amount?: number | null
  bulk?: boolean
  onClose: () => void
}) {
  const { event, accessToken, busy, run } = useOrganizerEvent()
  const [value, setValue] = useState(amount == null ? '' : String(amount))
  const [method, setMethod] = useState<PaymentMethod>(
    bulk ? 'CLUB_INVOICE' : 'TRANSFER',
  )
  const [reference, setReference] = useState('')
  const [comment, setComment] = useState('')
  const [paidAt, setPaidAt] = useState('')
  const [result, setResult] = useState<BulkResult | null>(null)
  const marking = action === 'mark-paid'
  const title = bulk
    ? 'Отметить оплату выбранных'
    : marking
      ? 'Отметить оплату'
      : action === 'waive-payment'
        ? 'Освободить от взноса'
        : action === 'refund-payment'
          ? 'Вернуть оплату'
          : 'Сбросить отметку оплаты'
  return (
    <OrganizerDialog title={title} onClose={onClose}>
      {result ? (
        <div role="status">
          <p>Обновлено: {result.updated}</p>
          {result.skipped.map((s) => (
            <p key={s.id}>
              {s.id}: {s.reason}
            </p>
          ))}
          <button onClick={onClose}>Закрыть</button>
        </div>
      ) : (
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault()
            const input = {
              amount: value === '' ? undefined : Number(value),
              method,
              reference,
              comment,
              paidAt: paidAt ? new Date(paidAt).toISOString() : undefined,
            }
            void run(async () => {
              if (bulk) {
                const response = await bulkPayment(
                  event.id,
                  ids,
                  input,
                  accessToken,
                )
                if (response.skipped.length) setResult(response)
              } else await paymentAction(ids[0], action, input, accessToken)
            }, 'registrations').then((ok) => {
              if (ok && !bulk) onClose()
              else if (ok && bulk)
                setResult(
                  (previous) =>
                    previous ?? { updated: ids.length, skipped: [] },
                )
            })
          }}
        >
          <p>
            {bulk
              ? `Заявок: ${ids.length}`
              : 'Подтвердите операцию. Она будет записана в историю оплаты.'}
          </p>
          {!bulk && (marking || action === 'refund-payment') && (
            <label className={styles.field}>
              Сумма
              <input
                type="number"
                min="0"
                step="0.01"
                value={value}
                onChange={(e) => setValue(e.target.value)}
              />
            </label>
          )}
          {marking && (
            <>
              <label className={styles.field}>
                Способ оплаты
                <select
                  value={method}
                  onChange={(e) => setMethod(e.target.value as PaymentMethod)}
                >
                  {Object.entries(methodLabels).map(([key, label]) => (
                    <option key={key} value={key}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.field}>
                Номер документа
                <input
                  maxLength={128}
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                />
              </label>
              {!bulk && (
                <label className={styles.field}>
                  Дата оплаты
                  <input
                    type="datetime-local"
                    value={paidAt}
                    onChange={(e) => setPaidAt(e.target.value)}
                  />
                </label>
              )}
            </>
          )}
          <label className={styles.field}>
            Комментарий
            <textarea
              required={!marking}
              maxLength={500}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </label>
          <button
            className={styles.primary}
            disabled={busy || event.status === 'ARCHIVED'}
          >
            Подтвердить
          </button>
        </form>
      )}
    </OrganizerDialog>
  )
}
