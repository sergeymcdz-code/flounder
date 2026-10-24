import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button } from '../../components/ui/Button'
import { ActionBar } from '../../components/ui/ActionBar'
import { Notice } from '../../components/ui/Notice'
import { useOrganizerEvent } from '../organizer/organizer-event-context'
import {
  paymentAction,
  bulkPayment,
  methodLabels,
  type PaymentMethod,
  type PaymentAction,
  type BulkResult,
} from '../../api/registration-payments.api'
import styles from './OrganizerPayments.module.css'
export function PaymentDialog({ title, children, onClose, busy = false }: { title: string; children: ReactNode; onClose: () => void; busy?: boolean }) {
  const context = useOrganizerEvent()
  const ref = useRef<HTMLDialogElement>(null)
  const registerDialog = context?.registerDialog
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const element = ref.current
    element?.showModal()
    const unregister = registerDialog?.()
    return () => { element?.close(); unregister?.(); previous?.focus() }
  }, [registerDialog])
  return <dialog ref={ref} className={styles.dialog} aria-label={title} onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <header className={styles.dialogHeader}><h2>{title}</h2><Button disabled={busy} title={busy ? 'Дождитесь завершения текущего действия' : undefined} onClick={onClose} aria-label="Закрыть">×</Button></header>
    {context?.error && <Notice tone="danger">{context.error}</Notice>}
    {children}
  </dialog>
}

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
  const { event, registrations, accessToken, busy, run } = useOrganizerEvent()
  const participantNames = new Map(registrations.map(r => [r.id,
    [r.participantSnapshot.lastName, r.participantSnapshot.firstName, r.participantSnapshot.middleName].filter(Boolean).join(' ')]))
  const [value, setValue] = useState(amount == null ? '' : String(amount))
  const [method, setMethod] = useState<PaymentMethod>(
    bulk ? 'CLUB_INVOICE' : 'TRANSFER',
  )
  const [reference, setReference] = useState('')
  const [comment, setComment] = useState('')
  const [paidAt, setPaidAt] = useState('')
  const [failed, setFailed] = useState(false)
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
    <PaymentDialog title={title} onClose={onClose} busy={busy}>
      {result ? (
        <div role="status">
          <p>Обновлено: {result.updated}</p>
          {result.skipped.map((s) => (
            <p key={s.id}>
              {participantNames.get(s.id) || 'Заявка недоступна'}: {s.reason}
            </p>
          ))}
          <Button onClick={onClose}>Закрыть</Button>
        </div>
      ) : (
        <form
          className="fl-form"
          onSubmit={(e) => {
            e.preventDefault()
            setFailed(false)
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
              setFailed(!ok)
              if (ok && !bulk) onClose()
              else if (ok && bulk)
                setResult(
                  (previous) =>
                    previous ?? { updated: ids.length, skipped: [] },
                )
            })
          }}
        >
          <fieldset className="fl-fieldset" disabled={busy || event.status === 'ARCHIVED'}><legend>Операция оплаты</legend>
          <p>
            {bulk
              ? `Заявок: ${ids.length}`
              : 'Подтвердите операцию. Она будет записана в историю оплаты.'}
          </p>
          {!bulk && (marking || action === 'refund-payment') && (
            <label className="fl-field">
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
              <label className="fl-field">
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
              <label className="fl-field">
                Номер документа
                <input
                  maxLength={128}
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                />
              </label>
              {!bulk && (
                <label className="fl-field">
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
          <label className="fl-field">
            Комментарий
            <textarea
              required={!marking}
              maxLength={500}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </label>
          </fieldset>
          {failed && <Notice tone="danger">Не удалось сохранить операцию. Проверьте данные и повторите.</Notice>}
          {event.status === 'ARCHIVED' && <Notice>Соревнование в архиве: доступен только просмотр.</Notice>}
          {busy && <p role="status">Сохраняем операцию оплаты…</p>}
          <ActionBar className={styles.dialogActions}>
            <Button disabled={busy} title={busy ? 'Дождитесь завершения текущего действия' : undefined} onClick={onClose}>Отмена</Button>
            <Button type="submit" variant="primary" loading={busy} disabled={busy || event.status === 'ARCHIVED'} title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined}>Подтвердить</Button>
          </ActionBar>
        </form>
      )}
    </PaymentDialog>
  )
}
