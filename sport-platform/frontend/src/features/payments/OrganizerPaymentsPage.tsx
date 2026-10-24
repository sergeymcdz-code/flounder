import { Component, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'
import { useOrganizerEvent, type EventContext } from '../organizer/organizer-event-context'
import { PaymentMutationDialog } from './PaymentMutationDialog'
import { PaymentHistoryDialog } from './PaymentHistoryDialog'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError } from '../../api/http'
import {
  paymentSummary, savePaymentSettings, recalculateFees, downloadPaymentSummary,
  defaultPaymentSettings, money, type PaymentSettings, type PaymentSummary, type PaymentAction,
} from '../../api/registration-payments.api'
import type { OrganizerRegistration } from '../../api/organizer.types'
import styles from './OrganizerPayments.module.css'

function paymentError(error: unknown) {
  if (error instanceof TypeError) return 'Нет связи. Проверьте интернет и повторите.'
  if (error instanceof ApiError) return error.status === 404 ? 'Данные не найдены. Повторите загрузку.' : `Сервер ответил ошибкой (${error.status}). ${error.message}`
  return error instanceof Error ? error.message : 'Не удалось загрузить данные. Повторите попытку.'
}

// Local fallback until the shared OverflowMenu from D04-2a is available.
function PaymentMenu({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    panel.current?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus()
    const close = (event: KeyboardEvent | PointerEvent) => {
      if (event instanceof KeyboardEvent && event.key !== 'Escape') return
      if (event instanceof PointerEvent && (panel.current?.contains(event.target as Node) || trigger.current?.contains(event.target as Node))) return
      setOpen(false)
      if (event instanceof KeyboardEvent) trigger.current?.focus()
    }
    document.addEventListener('keydown', close)
    document.addEventListener('pointerdown', close)
    return () => { document.removeEventListener('keydown', close); document.removeEventListener('pointerdown', close) }
  }, [open])
  return <><button ref={trigger} type="button" className="fl-btn fl-btn-ghost" aria-label={label} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? id : undefined} onClick={() => setOpen(!open)}>⋯</button>
    {open && createPortal(<div ref={panel} id={id} className={styles.menu} role="dialog" aria-label={label} onClick={event => {
      if ((event.target as HTMLElement).closest('button:not(:disabled)')) { setOpen(false); trigger.current?.focus() }
    }}>{children}</div>, document.body)}</>
}

class PaymentBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    return this.state.failed ? <Notice tone="danger">Не удалось показать оплату. <Button onClick={() => this.setState({ failed: false })}>Повторить</Button></Notice> : this.props.children
  }
}

export function OrganizerPaymentsPage() {
  const context = useOrganizerEvent()
  if (!context?.event) return <Notice tone="danger">Не удалось загрузить соревнование. <Button onClick={() => window.location.reload()}>Повторить</Button></Notice>
  return <PaymentBoundary key={context.event.id}><PaymentsContent context={context} /></PaymentBoundary>
}

function PaymentsContent({ context }: { context: EventContext }) {
  const { event, registrations, accessToken, busy, run } = context
  const [settings, setSettings] = useState<PaymentSettings>(event.payment ?? defaultPaymentSettings)
  const [summary, setSummary] = useState<PaymentSummary | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  const [mutation, setMutation] = useState<{ ids: string[]; action?: PaymentAction; amount?: number | null; bulk?: boolean } | null>(null)
  const [history, setHistory] = useState<string | null>(null)
  const [recalculated, setRecalculated] = useState<number | null>(null)
  const [saved, setSaved] = useState(false)
  const disabled = busy || event.status === 'ARCHIVED'
  const disabledReason = busy ? 'Дождитесь завершения текущего действия' : event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : undefined
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    paymentSummary(event.id, accessToken).then(value => {
      if (active) setSummary(value)
    }).catch(err => { if (active) setError(paymentError(err)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [event.id, accessToken, registrations, reload])
  const retry = () => setReload(value => value + 1)
  const clubColumns: DataTableColumn<PaymentSummary['byClub'][number]>[] = [
    { key: 'club', header: 'Клуб', rowHeader: true, render: c => c.club || 'Без клуба' },
    { key: 'participants', header: 'Участники', numeric: true, render: c => c.participants },
    { key: 'registrations', header: 'Заявки', numeric: true, render: c => c.registrations },
    { key: 'due', header: 'Начислено', numeric: true, render: c => money(c.due, summary?.currency) },
    { key: 'paid', header: 'Оплачено', numeric: true, render: c => money(c.paid, summary?.currency) },
    { key: 'pending', header: 'Долг', numeric: true, render: c => <strong className={c.pending > 0 ? styles.debt : undefined}>{money(c.pending, summary?.currency)}</strong> },
    { key: 'actions', header: 'Действие', render: c => c.pendingRegistrationIds.length ? <Button disabled={disabled} title={disabledReason} onClick={() => setMutation({ ids: c.pendingRegistrationIds, bulk: true })}>Отметить оплаченными</Button> : <span className="fl-meta">Нет ожидающих оплаты</span> },
  ]
  const name = (r: OrganizerRegistration) => [r.participantSnapshot?.lastName, r.participantSnapshot?.firstName, r.participantSnapshot?.middleName].filter(Boolean).join(' ') || 'Участник не указан'
  const entryColumns: DataTableColumn<OrganizerRegistration>[] = [
    { key: 'name', header: 'Участник', rowHeader: true, render: name },
    { key: 'club', header: 'Клуб', render: r => r.participantSnapshot?.clubName || 'Без клуба' },
    { key: 'discipline', header: 'Вид программы', render: r => r.disciplineName || event.disciplines?.find(d => d.id === r.eventDisciplineId)?.name || 'Вид программы не указан' },
    { key: 'fee', header: 'Сумма', numeric: true, render: r => money(r.feeAmount, r.feeCurrency || settings.currency) },
    { key: 'payment', header: 'Оплата', render: r => <StatusBadge kind="payment" code={r.paymentStatus || 'NOT_REQUIRED'} /> },
    { key: 'actions', header: 'Действия', render: r => <div className={styles.actions}>
      <Button disabled={disabled} title={disabledReason} onClick={() => setMutation({ ids: [r.id], amount: r.feeAmount })}>Отметить оплату</Button>
      <PaymentMenu label={`Действия оплаты: ${name(r)}`}>
        {([['waive-payment', 'Освободить от взноса'], ['refund-payment', 'Вернуть оплату'], ['reset-payment', 'Сбросить отметку']] as const).map(([action, label]) => <Button key={action} variant="ghost" disabled={disabled} title={disabledReason} onClick={() => setMutation({ ids: [r.id], action, amount: action === 'refund-payment' ? r.paidAmount : undefined })}>{label}</Button>)}
        <Button variant="ghost" onClick={() => setHistory(r.id)}>Журнал оплаты</Button>
      </PaymentMenu>
    </div> },
  ]
  return <section className={styles.page} aria-label="Оплата">
    <header className={`fl-page-header ${styles.heading}`}><div><h2>Оплата</h2><p className="fl-meta">Взносы участников и долги клубов</p></div>
      <PaymentMenu label="Действия оплаты"><Button variant="ghost" disabled={busy || !summary} title={busy ? disabledReason : !summary ? 'Сводка оплаты ещё не загружена' : undefined} onClick={() => { void run(() => downloadPaymentSummary(event.id, accessToken), 'registrations') }}>Экспорт XLSX</Button>{!summary && <small>Для экспорта дождитесь загрузки сводки.</small>}</PaymentMenu>
    </header>
    {error && <Notice tone="danger">{error} <Button onClick={retry}>Повторить</Button></Notice>}
    {loading && <div role="status"><p>Загружаем сводку оплаты…</p><div className={styles.skeleton} aria-hidden="true"><span /><span /><span /></div></div>}
    {summary && <dl className={styles.summary}>{Object.entries({ due: 'Начислено', paid: 'Оплачено', pending: 'Ожидается', waived: 'Освобождено', refunded: 'Возвращено' }).map(([key, label]) => <div className="fl-summary-cell" key={key}><dt>{label}</dt><dd>{new Intl.NumberFormat('ru-RU', { style: 'currency', currency: summary.currency }).formatToParts(summary.totals[key as keyof PaymentSummary['totals']]).map((part, index) => <span key={index} className={part.type === 'currency' ? styles.currency : undefined}>{part.value}</span>)}</dd></div>)}</dl>}
    <div className={styles.layout}>
      <div className={styles.lists}>
        <article className={styles.card}><h2>Оплата по клубам</h2>
          {summary && (summary.byClub.length ? <div className={`${styles.rowcards} ${styles.clubCards}`}><DataTable caption="Взносы и долги по клубам" columns={clubColumns} rows={summary.byClub} rowKey={c => c.club} minWidth={760} /></div> : <EmptyState title="Платежей пока нет" action={<Button as={Link} to={`/organizer/events/${event.id}/entries`}>Открыть заявки</Button>}><p>Подтверждённые заявки с взносами появятся здесь.</p></EmptyState>)}
          {!summary && !loading && !error && <EmptyState title="Сводка пока недоступна" action={<Button onClick={retry}>Повторить</Button>} />}
        </article>
        <article className={styles.card}><h2>Заявки и оплата</h2>
          {registrations.length ? <div className={`${styles.rowcards} ${styles.entryCards}`}><DataTable caption="Оплата заявок" columns={entryColumns} rows={registrations} rowKey={r => r.id} minWidth={900} /></div> : <EmptyState title="Заявок пока нет" action={<Button as={Link} to={`/organizer/events/${event.id}/entries`}>Открыть заявки</Button>}><p>Здесь можно будет отметить оплату и открыть журнал операций.</p></EmptyState>}
        </article>
      </div>
      <details className={`${styles.card} ${styles.settings}`} open={undefined} ref={element => { if (element && !element.dataset.initialized) { element.open = (window.matchMedia?.('(min-width: 1366px)').matches ?? false); element.dataset.initialized = 'true' } }}>
        <summary><h2>Настройки оплаты</h2></summary>
        <form className="fl-form" onSubmit={e => { e.preventDefault(); setSaved(false); void run(() => savePaymentSettings(event.id, settings, accessToken)).then(ok => setSaved(ok)) }}>
          <fieldset className="fl-fieldset" disabled={disabled}><legend>Взносы</legend>
            <label className={styles.check}><input type="checkbox" checked={settings.enabled} onChange={e => setSettings({ ...settings, enabled: e.target.checked })} />Взносы включены</label>
            <label className="fl-field">Режим<select value={settings.feeMode} onChange={e => setSettings({ ...settings, feeMode: e.target.value as PaymentSettings['feeMode'] })}><option value="PER_DISCIPLINE">За вид программы</option><option value="PER_EVENT">За соревнование</option></select></label>
            {settings.feeMode === 'PER_EVENT' ? <label className="fl-field">Сумма за соревнование<input type="number" required min="0" step="0.01" value={settings.eventFeeAmount ?? ''} onChange={e => setSettings({ ...settings, eventFeeAmount: e.target.value === '' ? null : Number(e.target.value) })} /></label> : <p className="fl-meta">Взнос задаётся в настройках каждого вида программы.</p>}
            <label className="fl-field">Валюта<input required pattern="[A-Z]{3}" maxLength={3} value={settings.currency} onChange={e => setSettings({ ...settings, currency: e.target.value.toUpperCase() })} /></label>
            <label className="fl-field">Реквизиты и назначение платежа<textarea maxLength={4000} value={settings.instructions} onChange={e => setSettings({ ...settings, instructions: e.target.value })} /></label>
            <label className="fl-field">Срок оплаты, дней<input type="number" required min="0" max="365" value={settings.dueDays} onChange={e => setSettings({ ...settings, dueDays: Number(e.target.value) })} /></label>
          </fieldset>
          <ActionBar><Button type="submit" disabled={disabled} title={disabledReason} loading={busy}>Сохранить настройки</Button></ActionBar>
          {saved && <p role="status">Настройки оплаты сохранены.</p>}
        </form>
        <p className="fl-meta">Изменения применяются к новым подтверждениям. Пересчёт заполняет взносы старых заявок без зафиксированной суммы.</p>
        <Button variant="ghost" disabled={disabled} title={disabledReason} onClick={() => { void run(async () => { const result = await recalculateFees(event.id, accessToken); setRecalculated(result.updated) }, 'registrations') }}>Пересчитать взносы старых заявок</Button>
        {recalculated != null && <p role="status">Обновлено: {recalculated}</p>}
      </details>
    </div>
    {disabledReason && <p className="fl-meta">{disabledReason}</p>}
    {mutation && <PaymentMutationDialog {...mutation} onClose={() => setMutation(null)} />}
    {history && <PaymentHistoryDialog id={history} token={accessToken} onClose={() => setHistory(null)} />}
  </section>
}
