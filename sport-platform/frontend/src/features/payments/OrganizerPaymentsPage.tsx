import { useEffect, useState } from 'react'
import { useOrganizerEvent } from '../organizer/organizer-event-context'
import { PaymentMutationDialog } from './PaymentMutationDialog'
import {
  paymentSummary,
  savePaymentSettings,
  recalculateFees,
  downloadPaymentSummary,
  defaultPaymentSettings,
  money,
  type PaymentSettings,
  type PaymentSummary,
} from '../../api/registration-payments.api'
import styles from '../organizer/OrganizerEventPage.module.css'
export function OrganizerPaymentsPage() {
  const { event, registrations, accessToken, busy, run } = useOrganizerEvent()
  const [settings, setSettings] = useState<PaymentSettings>(
    event.payment ?? defaultPaymentSettings,
  )
  const [summary, setSummary] = useState<PaymentSummary | null>(null)
  const [error, setError] = useState('')
  const [clubIds, setClubIds] = useState<string[] | null>(null)
  const [recalculated, setRecalculated] = useState<number | null>(null)
  const disabled = busy || event.status === 'ARCHIVED'
  useEffect(() => {
    let active = true
    paymentSummary(event.id, accessToken)
      .then((s) => {
        if (active) {
          setSummary(s)
          setError('')
        }
      })
      .catch((e) => {
        if (active)
          setError(
            e instanceof Error ? e.message : 'Не удалось загрузить сводку',
          )
      })
    return () => {
      active = false
    }
  }, [event.id, accessToken, registrations])
  return (
    <>
      <article className={styles.card}>
        <h2>Настройки оплаты</h2>
        <p>Организатор принимает взносы и подтверждает их получение.</p>
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault()
            void run(() => savePaymentSettings(event.id, settings, accessToken))
          }}
        >
          <label>
            <input
              disabled={disabled}
              type="checkbox"
              checked={settings.enabled}
              onChange={(e) =>
                setSettings({ ...settings, enabled: e.target.checked })
              }
            />{' '}
            Взносы включены
          </label>
          <label className={styles.field}>
            Режим
            <select
              disabled={disabled}
              value={settings.feeMode}
              onChange={(e) =>
                setSettings({
                  ...settings,
                  feeMode: e.target.value as PaymentSettings['feeMode'],
                })
              }
            >
              <option value="PER_DISCIPLINE">За дистанцию</option>
              <option value="PER_EVENT">За событие</option>
            </select>
          </label>
          {settings.feeMode === 'PER_EVENT' ? (
            <label className={styles.field}>
              Сумма за событие
              <input
                disabled={disabled}
                type="number"
                required
                min="0"
                step="0.01"
                value={settings.eventFeeAmount ?? ''}
                onChange={(e) =>
                  setSettings({
                    ...settings,
                    eventFeeAmount:
                      e.target.value === '' ? null : Number(e.target.value),
                  })
                }
              />
            </label>
          ) : (
            <p>Взнос задаётся в настройках каждого вида программы.</p>
          )}
          <label className={styles.field}>
            Валюта
            <input
              disabled={disabled}
              required
              pattern="[A-Z]{3}"
              maxLength={3}
              value={settings.currency}
              onChange={(e) =>
                setSettings({
                  ...settings,
                  currency: e.target.value.toUpperCase(),
                })
              }
            />
          </label>
          <label className={styles.field}>
            Реквизиты и назначение платежа
            <textarea
              disabled={disabled}
              maxLength={4000}
              value={settings.instructions}
              onChange={(e) =>
                setSettings({ ...settings, instructions: e.target.value })
              }
            />
          </label>
          <label className={styles.field}>
            Срок оплаты, дней
            <input
              disabled={disabled}
              type="number"
              required
              min="0"
              max="365"
              value={settings.dueDays}
              onChange={(e) =>
                setSettings({ ...settings, dueDays: Number(e.target.value) })
              }
            />
          </label>
          <button className={styles.primary} disabled={disabled}>
            Сохранить настройки
          </button>
        </form>
        <p>
          Изменения применяются к новым подтверждениям. Для старых заявок без
          зафиксированного взноса используйте пересчёт.
        </p>
        <button
          disabled={disabled}
          onClick={() => {
            void run(async () => {
              const result = await recalculateFees(event.id, accessToken)
              setRecalculated(result.updated)
            }, 'registrations')
          }}
        >
          Заполнить взносы старых заявок
        </button>
        {recalculated != null && <p role="status">Обновлено: {recalculated}</p>}
      </article>
      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Оплата по клубам</h2>
          <button
            disabled={busy || !summary}
            onClick={() => {
              void run(
                () => downloadPaymentSummary(event.id, accessToken),
                'registrations',
              )
            }}
          >
            Экспорт XLSX
          </button>
        </div>
        {error && <p role="alert">{error}</p>}
        {summary && (
          <>
            <dl>
              {Object.entries({
                due: 'Начислено',
                paid: 'Оплачено',
                pending: 'Ожидается',
                waived: 'Освобождено',
                refunded: 'Возвращено',
              }).map(([key, label]) => (
                <div key={key}>
                  <dt>{label}</dt>
                  <dd>
                    {money(
                      summary.totals[key as keyof PaymentSummary['totals']],
                      summary.currency,
                    )}
                  </dd>
                </div>
              ))}
            </dl>
            <div className={styles.table}>
              <table>
                <thead>
                  <tr>
                    {[
                      'Клуб',
                      'Участники',
                      'Заявки',
                      'Начислено',
                      'Оплачено',
                      'Долг',
                      'Действия',
                    ].map((h) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {summary.byClub.map((c) => (
                    <tr key={c.club}>
                      <td>{c.club}</td>
                      <td>{c.participants}</td>
                      <td>{c.registrations}</td>
                      <td>{money(c.due, summary.currency)}</td>
                      <td>{money(c.paid, summary.currency)}</td>
                      <td>{money(c.pending, summary.currency)}</td>
                      <td>
                        <button
                          disabled={
                            disabled || !c.pendingRegistrationIds.length
                          }
                          onClick={() => setClubIds(c.pendingRegistrationIds)}
                        >
                          Отметить оплаченными все ожидающие клуба
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </article>
      {clubIds && (
        <PaymentMutationDialog
          ids={clubIds}
          bulk
          onClose={() => setClubIds(null)}
        />
      )}
    </>
  )
}
