import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/http'
import { getRegistrations } from '../../api/registrations.api'
import { bulkPayment, money } from '../../api/registration-payments.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { PageHeader } from '../../components/ui/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { participantName, eventLabel } from '../registrations/registration-display'
import type { Registration } from '../registrations/registration.types'
import styles from './OperatorPaymentsPage.module.css'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.status === 403
    ? 'У вашей учётной записи нет прав на просмотр этого раздела.' : error.message
  return error instanceof TypeError ? 'Нет связи. Проверьте подключение и повторите попытку.' : 'Не удалось загрузить список заявок.'
}

export function OperatorPaymentsPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [registrations, setRegistrations] = useState<Registration[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [query, setQuery] = useState('')
  const [eventId, setEventId] = useState('')
  const [paymentStatus, setPaymentStatus] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [mutationError, setMutationError] = useState<string>()
  const [result, setResult] = useState<string[]>([])
  useEffect(() => { document.title = 'Заявки и платежи · SportGate' }, [])
  useEffect(() => {
    if (!accessToken) { setIsLoading(false); return }
    let active = true
    setIsLoading(true); setErrorMessage(null)
    getRegistrations(accessToken).then(rows => { if (active) setRegistrations(rows) })
      .catch(error => { if (active) setErrorMessage(getErrorMessage(error)) })
      .finally(() => { if (active) setIsLoading(false) })
    return () => { active = false }
  }, [accessToken, reload])

  const events = [...new Map(registrations.map(row => [row.eventId, row])).values()]
  const visible = registrations.filter(row => (!eventId || row.eventId === eventId)
    && (!paymentStatus || row.paymentStatus === paymentStatus)
    && `${participantName(row)} ${row.eventTitle || ''}`.toLocaleLowerCase('ru-RU').includes(query.trim().toLocaleLowerCase('ru-RU')))
  const eligible = visible.filter(row => row.paymentStatus === 'PENDING')
  const chosen = visible.filter(row => selected.includes(row.id))
  const bulkReason = busy ? 'Дождитесь сохранения оплаты' : !eventId ? 'Выберите одно соревнование для массовой оплаты' : !eligible.length ? 'Нет заявок с ожидающей оплатой' : !chosen.length ? 'Выберите заявки с ожидающей оплатой' : undefined
  function resetSelection() { setSelected([]); setResult([]) }
  async function markPaid() {
    if (!accessToken || bulkReason || busy) return
    setBusy(true); setMutationError(undefined)
    try {
      const response = await bulkPayment(eventId, chosen.map(row => row.id), { method: 'CLUB_INVOICE' }, accessToken)
      const skipped = new Set(response.skipped.map(item => item.id))
      setRegistrations(rows => rows.map(row => chosen.some(item => item.id === row.id) && !skipped.has(row.id) ? { ...row, paymentStatus: 'PAID', paidAmount: row.feeAmount } : row))
      setResult([`Обновлено заявок: ${response.updated}. Пропущено: ${response.skipped.length}.`, ...response.skipped.map(item => `${participantName(chosen.find(row => row.id === item.id) ?? chosen[0])}: ${item.reason}`)])
      setSelected(chosen.filter(row => skipped.has(row.id)).map(row => row.id))
      setConfirming(false)
    } catch (error) { setMutationError(error instanceof ApiError ? error.message : 'Нет связи. Не удалось сохранить оплату. Повторите попытку.') }
    finally { setBusy(false) }
  }

  return <section className={styles.page}>
    <p className="fl-eyebrow">Панель оператора</p>
    <PageHeader title="Заявки и платежи" meta="Проверьте данные участника, статус и сумму оплаты." />
    {isLoading && <LoadingState message="Загружаем список заявок…" />}
    {errorMessage && <ErrorState message={errorMessage} onRetry={() => setReload(value => value + 1)} />}
    {(!isLoading || registrations.length > 0) && <>
      <form className={styles.filters} role="search" onSubmit={event => event.preventDefault()}>
        <label className="fl-field">Поиск по ФИО или соревнованию<input type="search" value={query} disabled={busy} title={busy ? bulkReason : undefined} onChange={event => { setQuery(event.target.value); resetSelection() }} /></label>
        <label className="fl-field">Соревнование<select value={eventId} disabled={busy} title={busy ? bulkReason : undefined} onChange={event => { setEventId(event.target.value); resetSelection() }}><option value="">Все соревнования</option>{events.map(row => <option key={row.eventId} value={row.eventId}>{eventLabel(row)}</option>)}</select></label>
        <label className="fl-field">Статус оплаты<select value={paymentStatus} disabled={busy} title={busy ? bulkReason : undefined} onChange={event => { setPaymentStatus(event.target.value); resetSelection() }}><option value="">Все статусы</option><option value="PENDING">Ожидает оплаты</option><option value="PAID">Оплачено</option><option value="WAIVED">Освобождён</option><option value="NOT_REQUIRED">Без оплаты</option><option value="REFUNDED">Возврат</option></select></label>
      </form>
      <p role="status">Найдено заявок: {visible.length}</p>
      {!visible.length && !errorMessage ? <EmptyState title={registrations.length ? 'Ничего не найдено' : 'Заявок пока нет.'} action={<Button onClick={() => { setQuery(''); setEventId(''); setPaymentStatus(''); resetSelection(); setReload(value => value + 1) }}>{registrations.length ? 'Сбросить фильтры' : 'Обновить список'}</Button>}><p>{registrations.length ? 'Измените поиск или соревнование.' : 'Заявки появятся после регистрации участников.'}</p></EmptyState> : visible.length > 0 && <div className={styles.tableWrap}>
        <DataTable cards minWidth="calc(var(--space-16) * 12)" caption="Заявки участников" rows={visible} rowKey={row => row.id} columns={[
          { key: 'name', header: 'Участник', rowHeader: true, render: row => <div className={styles.identity}>
            {eventId && row.paymentStatus === 'PENDING' && <label className={styles.check}><input type="checkbox" aria-label={`Выбрать оплату: ${participantName(row)}`} checked={selected.includes(row.id)} disabled={busy} title={busy ? bulkReason : undefined} onChange={event => setSelected(ids => event.target.checked ? [...ids, row.id] : ids.filter(id => id !== row.id))} /></label>}
            <div><strong>{participantName(row)}</strong><span className={styles.meta}>{row.participantBirthYear ?? row.participantSnapshot.birthDate?.slice(0, 4) ?? '—'} г. р. · {row.participantClubName || row.participantSnapshot.clubName || 'Клуб не указан'}</span></div>
          </div> },
          { key: 'payment', header: 'Оплата', render: row => <StatusBadge kind="payment" code={row.paymentStatus || 'NOT_REQUIRED'} /> },
          { key: 'event', header: 'Соревнование', render: row => <div>{eventLabel(row)}<span className={styles.meta}>{row.disciplineName || 'Вид программы'}</span></div> },
          { key: 'status', header: 'Статус заявки', render: row => <StatusBadge kind="registration" code={row.status} /> },
          { key: 'amount', header: 'Начислено', numeric: true, render: row => row.feeAmount == null ? <span aria-label="Взнос не назначен" title="Взнос не назначен">—</span> : money(row.feeAmount, row.feeCurrency || 'RUB') },
          { key: 'actions', header: 'Действия', render: row => <Button as={Link} to={`/operator/registrations/${row.id}`} aria-label={`Открыть заявку: ${participantName(row)}, ${eventLabel(row)}`}>Открыть</Button> },
        ]} />
      </div>}
      {result.length > 0 && <Notice tone="info"><div role="status">{result.map((line, index) => <p key={index}>{line}</p>)}</div></Notice>}
      {registrations.length > 0 && !eventId && <p className={styles.meta}>Для массовой оплаты выберите одно соревнование.</p>}
      {registrations.length > 0 && eventId && <div className={styles.bulk} aria-label="Массовая оплата">
        <div><strong>Выбрано: <span className="fl-numeric">{chosen.length}</span></strong>{bulkReason && <small>{bulkReason}</small>}</div>
        {eventId && <Button disabled={busy || !eligible.length} title={busy ? bulkReason : !eligible.length ? 'Нет заявок с ожидающей оплатой' : undefined} onClick={() => setSelected(chosen.length === eligible.length ? [] : eligible.map(row => row.id))}>{chosen.length === eligible.length && eligible.length ? 'Снять выбор' : 'Выбрать все'}</Button>}
        <Button variant="primary" disabled={!!bulkReason} title={bulkReason} onClick={() => { setMutationError(undefined); setConfirming(true) }}>Отметить оплаченными</Button>
      </div>}
    </>}
    {confirming && <ConfirmDialog title="Отметить оплату выбранных?" description={`Заявок: ${chosen.length}. Соревнование: ${events.find(row => row.eventId === eventId)?.eventTitle || 'выбранное'}. Будет записана полная сумма взноса по каждой заявке, способ — счёт клуба.`} confirmLabel="Подтвердить" busy={busy} error={mutationError} onClose={() => setConfirming(false)} onConfirm={() => void markPaid()} />}
  </section>
}
