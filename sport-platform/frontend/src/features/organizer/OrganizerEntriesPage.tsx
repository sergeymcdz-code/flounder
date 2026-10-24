import { PaymentMutationDialog } from '../payments/PaymentMutationDialog'
import { PaymentHistoryDialog } from '../payments/PaymentHistoryDialog'
import { money, paymentLabels, type PaymentAction } from '../../api/registration-payments.api'
import { useState, type FormEvent } from 'react'
import { EntryRowMenu } from './EntryRowMenu'
import { DataTable } from '../../components/ui/DataTable'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { DocumentsPanel } from '../documents/DocumentsPanel'
import { documentTypes, documentStatusLabels, type DocumentType } from '../../api/documents.api'
import { Link, useSearchParams } from 'react-router'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { statusLabel } from '../../components/ui/status-dictionary'
import {
  reviewRegistration,
  type RegistrationReviewDecision,
} from '../../api/registrations.api'
import { registrationAction, updateSeedTime } from '../../api/organizer.api'
import {
  downloadImportTemplate,
  importRegistrations,
} from '../../api/imports.api'
import type {
  OrganizerRegistration,
  ImportReport,
} from '../../api/organizer.types'
import { OrganizerDialog } from './OrganizerDialog'
import {
  formatSeed,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './Entries.module.css'
export function OrganizerEntriesPage() {
  const {
    event,
    registrations,
    heats,
    generationResults,
    accessToken,
    busy, error, clearError,
    run, refresh,
  } = useOrganizerEvent()
  const [paymentFilter, setPaymentFilter] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [paymentDialog, setPaymentDialog] = useState<{ ids: string[]; action?: PaymentAction; amount?: number | null; bulk?: boolean } | null>(null)
  const [historyId, setHistoryId] = useState<string | null>(null)
  const [bulkAction, setBulkAction] = useState<'CONFIRMED' | 'REJECTED' | null>(null)
  const [bulkBusy, setBulkBusy] = useState(false)
  const [progress, setProgress] = useState('')
  const [bulkErrors, setBulkErrors] = useState<string[]>([])
  function eligible(action: 'CONFIRMED' | 'REJECTED', r: OrganizerRegistration) { return ['SUBMITTED', 'UNDER_REVIEW', ...(action === 'CONFIRMED' ? ['WAITLISTED'] : [])].includes(r.status) }
  async function applyBulk(reason: string) {
    if (!bulkAction) return
    const action = bulkAction
    const rows = registrations.filter(r => selected.includes(r.id) && eligible(action, r))
    setBulkBusy(true); setBulkErrors([])
    const failures: string[] = []; const completed: string[] = []
    for (const [index, row] of rows.entries()) {
      setProgress(`${index + 1} из ${rows.length}`)
      try {
        await reviewRegistration(row.id, { decision: action, reviewNote: reason }, accessToken)
        completed.push(row.id)
      } catch (cause) { failures.push(`${row.participantSnapshot.lastName} ${row.participantSnapshot.firstName}: ${cause instanceof Error ? cause.message : 'Не удалось выполнить действие'}`) }
    }
    setSelected(previous => previous.filter(id => !completed.includes(id)))
    try { await refresh() } catch (cause) { failures.push(cause instanceof Error ? cause.message : 'Не удалось обновить список. Обновите страницу.') }
    setBulkErrors(failures); setBulkAction(null); setBulkBusy(false)
  }
  const [withdrawNotice, setWithdrawNotice] = useState(false)
  function admission(r: OrganizerRegistration) {
    const result = generationResults[r.eventDisciplineId]
    if (!result)
      return {
        excluded: false,
        label: '—',
        title: 'Сформируйте заплывы, чтобы проверить допуск.',
      }
    const violation = result.violations.find(
      (v) =>
        v.registrationId === r.id &&
        [
          'GENDER_MISMATCH',
          'AGE_GROUP_MISMATCH',
          'DUPLICATE_ENTRY',
          'ATHLETE_NOT_FOUND',
        ].includes(v.code),
    )
    const excluded =
      result.excludedRegistrationIds.includes(r.id) || Boolean(violation)
    const reasons: Record<string, string> = {
      GENDER_MISMATCH: 'Пол участника не соответствует виду программы.',
      AGE_GROUP_MISMATCH: 'Год рождения не соответствует возрастным группам.',
      DUPLICATE_ENTRY: 'Участник уже заявлен на этот вид программы.',
      ATHLETE_NOT_FOUND: 'Спортсмен не найден в справочнике.',
    }
    return {
      excluded,
      label: excluded ? (violation?.code ?? 'Не допущена') : '✓',
      title: excluded
        ? (reasons[violation?.code ?? ''] ??
          'Заявка исключена из последнего формирования.')
        : 'Заявка допущена по последнему результату формирования.',
    }
  }
  const [missingMedical, setMissingMedical] = useState(false)
  const [documentProfile, setDocumentProfile] = useState<OrganizerRegistration | null>(null)
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const setStatus = (value: string) => setParams(previous => { const next = new URLSearchParams(previous); if (value) next.set('status', value); else next.delete('status'); return next })
  const [discipline, setDiscipline] = useState('')
  const [search, setSearch] = useState('')
  const [club, setClub] = useState('')
  const clubs = [...new Set(registrations.map(r => r.participantSnapshot.clubName).filter((name): name is string => Boolean(name)))]
  const [filtersOpen, setFiltersOpen] = useState(() => window.innerWidth >= 1024)
  const [importOpen, setImportOpen] = useState(false)
  const [review, setReview] = useState<{
    id: string
    decision: RegistrationReviewDecision
  } | null>(null)
  const [withdraw, setWithdraw] = useState<OrganizerRegistration | null>(null)
  const visible = registrations.filter(
    (r) =>
      (!status ||
        (status === 'EXCLUDED'
          ? admission(r).excluded
          : r.status === status || status === 'SUBMITTED' && r.status === 'UNDER_REVIEW')) &&
      (!paymentFilter || r.paymentStatus === paymentFilter) &&
      (!discipline || r.eventDisciplineId === discipline) &&
      (!club || r.participantSnapshot.clubName === club) &&
      (!params.get('payment') || r.paymentStatus === params.get('payment')) &&
      (!missingMedical || !r.documents?.MEDICAL_CERT || r.documents.MEDICAL_CERT === 'NOT_UPLOADED') &&
      `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName} ${r.participantSnapshot.middleName ?? ''} ${r.participantSnapshot.clubName ?? ''}`
        .toLocaleLowerCase('ru')
        .includes(search.toLocaleLowerCase('ru')),
  )
  function openReview(id: string, decision: RegistrationReviewDecision) {
    clearError()
    setReview({ id, decision })
  }
  const disabledReason = event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined
  function resetFilters() {
    setSearch(''); setDiscipline(''); setClub(''); setPaymentFilter(''); setMissingMedical(false)
    const next = new URLSearchParams(params); next.delete('status'); next.delete('payment'); setParams(next)
  }
  function exportEntries() {
    const csv = [['ФИО', 'Вид', 'Клуб', 'Год', 'Заявочное время', 'Статус', 'Оплата'], ...visible.map(r => [
      `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName} ${r.participantSnapshot.middleName ?? ''}`.trim(),
      event.disciplines.find(d => d.id === r.eventDisciplineId)?.name ?? '', r.participantSnapshot.clubName ?? '', r.participantSnapshot.birthDate?.slice(0, 4) ?? '',
      formatSeed(r.seedTimeMs), statusLabel('registration', r.status), paymentLabels[r.paymentStatus],
    ])].map(row => row.map(value => `"${String(value ?? '').replace(/^[=+@-]/, "'$&").replace(/"/g, '""')}"`).join(';')).join('\r\n')
    const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a'); link.href = url; link.download = 'sportgate-entries.csv'; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return (
    <article className={`${styles.card} fl-entries`}>
      <header className={styles.toolbar}>
        <h2>Заявки · <span className={styles.numeric}>{registrations.length}</span></h2>
        <div className={styles.actions}>
          <button className={`${styles.importButton} fl-btn fl-btn-secondary`} disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setImportOpen(true)}>Импорт из Excel</button>
          <EntryRowMenu label="Ещё действия" items={[{ label: 'Экспорт списка (CSV)', onSelect: exportEntries }]} />
        </div>
      </header>
      {disabledReason && <small className={styles.muted}>{disabledReason}</small>}
      {error && <Notice tone="danger"><p>{error}</p><button className="fl-btn fl-btn-secondary" disabled={busy} title={busy ? disabledReason : undefined} onClick={() => { void run(refresh, 'none') }}>Повторить</button></Notice>}
      {busy && !registrations.length && !error && <div className={styles.loading} role="status" aria-label="Загружаем заявки"><p>Загружаем заявки…</p>{[0, 1, 2].map(row => <div key={row} />)}</div>}
      {withdrawNotice && (
        <p role="status">
          Пересчитайте заплывы ·{' '}
          <Link
            className={styles.link}
            to={`/organizer/events/${event.id}/heats`}
          >
            Заплывы
          </Link>
        </p>
      )}
      {paymentDialog && <PaymentMutationDialog {...paymentDialog} onClose={() => { setPaymentDialog(null); setSelected([]) }} />}
      {historyId && <PaymentHistoryDialog id={historyId} token={accessToken} onClose={() => setHistoryId(null)} />}
      <div className={`${styles.chips} fl-filter-segments fl-scroll-x`} role="group" aria-label="Быстрые фильтры">
        {[
          { label: 'Все', value: '', active: !status && !missingMedical && !params.get('payment') },
          { label: `Новые ${registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW'].includes(r.status)).length}`, value: 'SUBMITTED', active: status === 'SUBMITTED' },
          { label: 'Не допущены', value: 'EXCLUDED', active: status === 'EXCLUDED' },
          { label: 'Без справки', value: 'medical', active: missingMedical },
          { label: 'Не оплачены', value: 'payment', active: params.get('payment') === 'PENDING' },
        ].map(filter => <button key={filter.label} className={`fl-btn fl-btn-sm ${filter.active ? 'fl-btn-secondary' : 'fl-btn-ghost'}`} aria-pressed={filter.active} onClick={() => {
          const next = new URLSearchParams(params)
          next.delete('status'); next.delete('payment')
          if (filter.value === 'payment') next.set('payment', 'PENDING')
          else if (filter.value && filter.value !== 'medical') next.set('status', filter.value)
          setMissingMedical(filter.value === 'medical'); setParams(next)
        }}>{filter.label}</button>)}
      </div>
      {bulkErrors.length > 0 && <Notice tone="danger"><p>Не все действия выполнены:</p><ul>{bulkErrors.map((message, index) => <li key={index}>{message}</li>)}</ul></Notice>}
      <details className={styles.filterPanel} open={filtersOpen} onToggle={e => setFiltersOpen(e.currentTarget.open)}>
        <summary className="fl-btn fl-btn-ghost">Фильтры ▾</summary>
        <div className={styles.filters}>
        <label className="fl-field">Статус оплаты<select value={paymentFilter} onChange={e => setPaymentFilter(e.target.value)}><option value="">Все</option>{Object.entries(paymentLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label className="fl-field">
          Поиск
          <input
            placeholder="ФИО или клуб"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <label className="fl-field">
          Статус
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Все статусы</option>
            <option value="EXCLUDED">Не допущены</option>
            {[
              'WAITLISTED',
              'SUBMITTED',
              'CONFIRMED',
              'PAID',
              'ADMITTED',
              'NEEDS_CORRECTION',
              'REJECTED',
              'WITHDRAWN',
              'CANCELLED',
            ].map((s) => (
              <option key={s} value={s}>
                {statusLabel('registration', s)}
              </option>
            ))}
          </select>
        </label>
        <label className="fl-field">
          Вид программы
          <select
            value={discipline}
            onChange={(e) => setDiscipline(e.target.value)}
          >
            <option value="">Все виды</option>
            {event.disciplines.map((d) => (
              <option value={d.id} key={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </label>
        <label className={`${styles.selection} fl-field`}>
          <span><input type="checkbox" checked={missingMedical} onChange={(e) => setMissingMedical(e.target.checked)} /> Нет справки</span>
        </label>
        </div>
      {clubs.length > 0 && <div className={`${styles.chips} fl-scroll-x`} role="group" aria-label="Фильтр по клубу">
        <button className="fl-btn fl-btn-ghost" aria-pressed={!club} onClick={() => setClub('')}>Все клубы</button>
        {clubs.map(name => <button key={name} className="fl-btn fl-btn-ghost" aria-pressed={club === name} onClick={() => setClub(name)}>{name}</button>)}
      </div>}
      </details>
      {documentProfile && <OrganizerDialog title={`Документы · ${documentProfile.participantSnapshot.lastName}`} onClose={() => setDocumentProfile(null)}>
        <DocumentsPanel profileId={documentProfile.participantProfileId} eventId={event.id} accessToken={accessToken} manager
          onChanged={() => { void run(async () => undefined, 'registrations') }} />
      </OrganizerDialog>}
      <div className={styles.meta}>
        <p className={styles.numeric}>Найдено: {visible.length}</p>
        {visible.length > 0 && <label className={styles.selection}><input type="checkbox" aria-label="Выбрать все видимые заявки" disabled={bulkBusy || busy || event.status === 'ARCHIVED'} title={disabledReason || (bulkBusy ? 'Дождитесь завершения обработки заявок' : undefined)} checked={visible.length > 0 && visible.every(r => selected.includes(r.id))} onChange={e => setSelected(e.target.checked ? [...new Set([...selected, ...visible.map(r => r.id)])] : selected.filter(id => !visible.some(r => r.id === id)))} /><span className={styles.selectAllLabel}>Выбрать все</span></label>}
      </div>
      {visible.length > 0 && <div className={styles.tableFrame}>
        <div className={styles.table} ref={node => { node?.firstElementChild?.classList.add('fl-table-scroll') }}>
          <DataTable caption="Заявки участников" cards minWidth="var(--content-max)" maxHeight="min(60vh, calc(var(--space-16) * 10))"
            rows={visible.map((r, index) => ({ ...r, rowNumber: index + 1,
              name: `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName} ${r.participantSnapshot.middleName ?? ''}`.trim(),
              canPay: ['CONFIRMED', 'ADMITTED'].includes(r.status) && !['PAID', 'WAIVED', 'REFUNDED'].includes(r.paymentStatus),
              canReview: ['SUBMITTED', 'UNDER_REVIEW'].includes(r.status),
            }))} rowKey={r => r.id} columns={[
            { key: 'number', header: <span className={styles.number}>№</span>, label: '', render: r => <div className={styles.number}><label className={styles.selection}><input type="checkbox" aria-label={`Выбрать заявку ${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName}`} checked={selected.includes(r.id)} disabled={bulkBusy || busy || event.status === 'ARCHIVED'} title={disabledReason || (bulkBusy ? 'Дождитесь завершения обработки заявок' : undefined)} onChange={e => setSelected(e.target.checked ? [...selected, r.id] : selected.filter(id => id !== r.id))} /><span>{r.rowNumber}</span></label></div> },
            { key: 'name', header: <span className={styles.name}>ФИО</span>, label: '', rowHeader: true, render: r => <div className={styles.name}>
                  {r.name}
                  {r.admissionWarnings?.map(warning => <small key={warning} className={styles.error}>{warning}</small>)}
                </div> },
            { key: 'mobileMeta', header: <span className={styles.mobileMeta}></span>, label: '', render: r => <div className={styles.mobileMeta}>{event.disciplines.find(d => d.id === r.eventDisciplineId)?.name ?? '—'} · {r.participantSnapshot.birthDate?.slice(0, 4) ?? '—'} · {r.participantSnapshot.clubName || '—'}</div> },
            { key: 'year', header: <span className={styles.year}>Год</span>, label: '', numeric: true, render: r => <div className={styles.year}>{r.participantSnapshot.birthDate?.slice(0, 4) ?? '—'}</div> },
            { key: 'club', header: <span className={styles.club}>Клуб</span>, label: '', render: r => <div className={styles.club}>{r.participantSnapshot.clubName || '—'}</div> },
            { key: 'discipline', header: <span className={styles.discipline}>Вид</span>, label: '', render: r => <div className={styles.discipline}>{event.disciplines.find(d => d.id === r.eventDisciplineId)?.name ?? '—'}</div> },
            { key: 'admission', header: <span className={styles.admission}>Допуск</span>, label: '', render: r => <div className={styles.admission}><span title={admission(r).title}>{admission(r).excluded ? 'Не допущена' : admission(r).label}</span></div> },
            { key: 'documents', header: <span className={styles.documents} title="✓ принят · ◷ на проверке · ✕ отклонён · — нет">Документы</span>, label: '', render: r => <div className={styles.documents}><span title={(Object.keys(documentTypes) as DocumentType[]).map(type => `${documentTypes[type]}: ${documentStatusLabels[r.documents?.[type] ?? 'NOT_UPLOADED']}`).join(' · ')}>{(Object.keys(documentTypes) as DocumentType[]).map(type => {
                  const state = r.documents?.[type] ?? 'NOT_UPLOADED'
                  return <span key={type} title={`${documentTypes[type]}: ${documentStatusLabels[state]}`}>{state === 'ACCEPTED' ? '✓' : state === 'PENDING' ? '◷' : state === 'REJECTED' ? '✕' : '—'} </span>
                })}</span></div> },
            { key: 'seed', header: <span className={styles.seed}>Заявочное время</span>, label: '', numeric: true, render: r => <div className={styles.seed}><span className={styles.mobileLabel}>Заявочное время</span><SeedTimeEditor registration={r} /></div> },
            { key: 'status', header: <span className={styles.status}>Статус</span>, label: '', render: r => <div className={styles.status}><StatusBadge kind="registration" code={r.status} />{r.waitlistPosition != null ? ` · № ${r.waitlistPosition}` : ''}</div> },
            { key: 'payment', header: <span className={styles.payment}>Оплата</span>, label: '', render: r => <div className={styles.payment}><span className={styles.mobileLabel}>Оплата</span><span className={styles.paymentContent}>{r.feeAmount != null && <span className={styles.numeric}>{money(r.feeAmount, r.feeCurrency ?? 'RUB')}</span>}<span title="Статус оплаты"><StatusBadge kind="payment" code={r.paymentStatus} /></span></span></div> },
            { key: 'rowActions', header: <span className={styles.rowActions}>Действия</span>, label: '', render: r => <div className={styles.rowActions}><div className={styles.actions}>
                  {r.canReview ? <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => openReview(r.id, 'CONFIRMED')}>Подтвердить</button>
                    : r.canPay ? <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setPaymentDialog({ ids: [r.id], amount: r.feeAmount })}>Отметить оплату…</button> : null}
                  <EntryRowMenu>
                    {r.status === 'WAITLISTED' && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => openReview(r.id, 'CONFIRMED')}>Подтвердить вне лимита</button>}
                    {r.canReview && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => openReview(r.id, 'NEEDS_CORRECTION')}>Вернуть</button>}
                    {['SUBMITTED', 'UNDER_REVIEW', 'WAITLISTED', 'CONFIRMED', 'NEEDS_CORRECTION'].includes(r.status) && <button className="fl-btn fl-btn-danger" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => openReview(r.id, 'REJECTED')}>Отклонить</button>}
                    {!['WITHDRAWN', 'CANCELLED', 'REJECTED'].includes(r.status) && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => { clearError(); setWithdraw(r) }}>Снять</button>}
                    {r.canPay && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setPaymentDialog({ ids: [r.id], action: 'waive-payment' })}>Освободить от взноса</button>}
                    {r.paymentStatus === 'PAID' && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setPaymentDialog({ ids: [r.id], action: 'refund-payment', amount: r.paidAmount })}>Вернуть оплату</button>}
                    {['PAID', 'WAIVED', 'REFUNDED'].includes(r.paymentStatus) && <button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setPaymentDialog({ ids: [r.id], action: 'reset-payment' })}>Сбросить оплату</button>}
                    <button className="fl-btn fl-btn-ghost" onClick={() => setHistoryId(r.id)}>История оплаты</button>
                    <button className="fl-btn fl-btn-ghost" onClick={() => setDocumentProfile(r)}>Документы</button>
                    <p className={styles.muted}>Год: {r.participantSnapshot.birthDate?.slice(0, 4) ?? '—'} · Клуб: {r.participantSnapshot.clubName || '—'} · Пол: {r.participantSnapshot.gender === 'MALE' ? 'Мужской' : r.participantSnapshot.gender === 'FEMALE' ? 'Женский' : '—'} · Источник: {r.source === 'IMPORT' ? 'Импорт' : 'Форма'}</p>
                    <p className={styles.muted}>{admission(r).title}</p>
                  </EntryRowMenu>
                </div></div> }
          ]} />
        </div>
      </div>}
      {!visible.length && !busy && !error && <EmptyState title={registrations.length ? 'Ничего не найдено' : 'Заявок пока нет'} action={registrations.length
        ? <button className="fl-btn fl-btn-secondary" onClick={resetFilters}>Сбросить фильтры</button>
        : <button className="fl-btn fl-btn-secondary" disabled={event.status === 'ARCHIVED'} title={disabledReason} onClick={() => setImportOpen(true)}>Импорт из Excel</button>}>
        <p>{registrations.length ? 'Измените фильтры или начните поиск заново.' : 'Импортируйте список участников из Excel.'}</p>
      </EmptyState>}
      {selected.length > 0 && <div className={`${styles.bulkBar} fl-bulk-bar fl-actionbar`}><strong>Выбрано {selected.length}</strong><button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : bulkBusy ? 'Дождитесь завершения обработки заявок' : undefined} className="fl-btn fl-btn-secondary fl-btn-sm" disabled={bulkBusy || busy || event.status === 'ARCHIVED'} onClick={() => setPaymentDialog({ ids: selected, bulk: true })}>Отметить оплату выбранных ({selected.length})</button>{(['CONFIRMED','REJECTED'] as const).map(action => <button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : bulkBusy ? 'Дождитесь завершения обработки заявок' : (bulkBusy || busy || event.status === 'ARCHIVED' || !registrations.some(r => selected.includes(r.id) && eligible(action, r))) ? 'Среди выбранных нет заявок для этого действия' : undefined} className={`fl-btn fl-btn-sm ${action === 'REJECTED' ? 'fl-btn-danger' : 'fl-btn-secondary'}`} key={action} disabled={bulkBusy || busy || event.status === 'ARCHIVED' || !registrations.some(r => selected.includes(r.id) && eligible(action, r))} onClick={() => { clearError(); setBulkAction(action) }}>{action === 'CONFIRMED' ? 'Подтвердить' : 'Отклонить'}</button>)}<button title={bulkBusy ? 'Дождитесь завершения обработки заявок' : undefined} className="fl-btn fl-btn-ghost fl-btn-sm" disabled={bulkBusy} onClick={() => setSelected([])}>Снять выбор</button></div>}
      {bulkAction && <ConfirmDialog title={`${bulkAction === 'CONFIRMED' ? 'Подтвердить заявки' : 'Отклонить заявки'}?`} description={`Подходящих заявок: ${registrations.filter(r => selected.includes(r.id) && eligible(bulkAction, r)).length} из ${selected.length}. Действия выполняются последовательно; участники увидят новый статус.`} confirmLabel={bulkBusy ? progress : 'Применить'} tone={bulkAction === 'REJECTED' ? 'danger' : 'primary'} reason={bulkAction === 'REJECTED'} busy={bulkBusy} busyLabel={progress} onClose={() => setBulkAction(null)} onConfirm={reason => void applyBulk(reason)} />}
      {review && <ConfirmDialog title={review.decision === 'CONFIRMED' ? 'Подтвердить заявку?' : review.decision === 'REJECTED' ? 'Отклонить заявку?' : 'Вернуть на исправление?'}
        description={review.decision === 'CONFIRMED' ? 'Участник получит подтверждение и сможет оплатить участие.' : review.decision === 'REJECTED' ? 'Участник получит причину отклонения. Оплата станет недоступна.' : 'Участник получит комментарий и сможет исправить данные.'}
        confirmLabel={review.decision === 'CONFIRMED' ? 'Подтвердить' : review.decision === 'REJECTED' ? 'Отклонить' : 'Вернуть'} tone={review.decision === 'REJECTED' ? 'danger' : 'primary'} reason reasonRequired={review.decision !== 'CONFIRMED'} busy={busy} error={error}
        onClose={() => setReview(null)} onConfirm={note => { void run(() => reviewRegistration(review.id, { decision: review.decision, reviewNote: note }, accessToken), 'registrations').then(ok => { if (ok) setReview(null) }) }} />}
      {withdraw && (
        <ConfirmDialog
          title="Снять заявку?"
          confirmLabel="Снять" tone="danger" busy={busy} error={error}
          description={`${withdraw.participantSnapshot.lastName} ${withdraw.participantSnapshot.firstName} не будет участвовать в этом виде.`}
          onClose={() => setWithdraw(null)}
          onConfirm={() => {
            void run(
              () => registrationAction(withdraw.id, 'withdraw', accessToken),
              'registrations',
            ).then((ok) => {
              if (ok) {
                setWithdrawNotice(
                  heats.some(
                    (h) => h.eventDisciplineId === withdraw.eventDisciplineId,
                  ),
                )
                setWithdraw(null)
              }
            })
          }}
        />
      )}
      {importOpen && <ImportDialog onClose={() => setImportOpen(false)} />}
    </article>
  )
}
function SeedTimeEditor({
  registration,
}: {
  registration: OrganizerRegistration
}) {
  const { event, accessToken, busy, run } = useOrganizerEvent()
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [error, setError] = useState('')
  async function submit(e: FormEvent) {
    e.preventDefault()
    const match = /^(?:(\d+):)?(\d+)\.(\d{2})$/.exec(value.trim())
    if (
      value.trim() &&
      (!match || (match[1] != null && Number(match[2]) >= 60))
    ) {
      setError('Введите сс.сс или м:сс.сс либо оставьте пустым.')
      return
    }
    const seed = match
      ? Number(match[1] ?? 0) * 60000 +
        Number(match[2]) * 1000 +
        Number(match[3]) * 10
      : null
    const ok = await run(
      () => updateSeedTime(registration.id, seed, accessToken),
      'registrations',
    )
    if (ok) setEditing(false)
  }
  return editing ? (
    <form className={styles.seedForm} onSubmit={submit}>
      <input
        aria-label={`Заявочное время ${registration.participantSnapshot.lastName}`}
        value={value}
        placeholder="00:32.15"
        onChange={(e) => {
          setValue(e.target.value)
          setError('')
        }}
      />
      <div className={styles.actions}>
        <button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'}>Сохранить</button>
        <button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-secondary" type="button" disabled={busy || event.status === 'ARCHIVED'} onClick={() => setEditing(false)}>
          Отмена
        </button>
      </div>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </form>
  ) : (
    <button
      className={`${styles.seedButton} fl-btn fl-btn-ghost`} disabled={busy || event.status === 'ARCHIVED'}
      title={(busy || event.status === 'ARCHIVED') ? (event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined) : 'Изменить заявочное время'}
      aria-label="Изменить заявочное время"
      onClick={() => {
        setValue(
          registration.seedTimeMs == null
            ? ''
            : formatSeed(registration.seedTimeMs),
        )
        setError('')
        setEditing(true)
      }}
    >
      {formatSeed(registration.seedTimeMs)} ✎
    </button>
  )
}
function ImportDialog({ onClose }: { onClose: () => void }) {
  const { event, accessToken, run, busy } = useOrganizerEvent()
  const [file, setFile] = useState<File | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  async function download() {
    await run(async () => {
      const blob = await downloadImportTemplate(event.id, accessToken)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'sportgate-registrations.xlsx'
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    }, 'none')
  }
  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      setError('Выберите файл .xlsx.')
      return
    }
    setUploading(true)
    setError('')
    await run(async () => {
      const result = await importRegistrations(event.id, file, accessToken)
      setReport(result)
    }, 'registrations')
    setUploading(false)
  }
  return (
    <OrganizerDialog
      title="Импорт заявок из Excel"
      onClose={() => {
        if (!uploading) onClose()
      }}
    >
      <div className={styles.stack}>
        <button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined}
          disabled={busy || event.status === 'ARCHIVED'}
          className={styles.link}
          onClick={() => {
            void download()
          }}
        >
          Скачать шаблон
        </button>
        <form className={`${styles.form} fl-form`} onSubmit={submit}>
          <label className="fl-field">
            Файл .xlsx
            <input
              type="file"
              required
              accept=".xlsx"
              disabled={busy || event.status === 'ARCHIVED'}
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null)
                setReport(null)
              }}
            />
          </label>
          {error && <p className={styles.error}>{error}</p>}
          <button title={busy ? 'Дождитесь завершения текущего действия' : (busy || !file) ? 'Выберите файл .xlsx для импорта' : undefined} className="fl-btn fl-btn-primary" disabled={busy || !file}>
            Импортировать
          </button>
        </form>
        {uploading && (
          <div role="status">
            <progress aria-label="Импортируем заявки" />
            <p>Импортируем заявки…</p>
          </div>
        )}
        {report && (
          <div aria-live="polite">
            <h2>Отчёт импорта</h2>
            <p>
              Всего: {report.total} · Создано: {report.created} · Обновлено:{' '}
              {report.updated} · Пропущено: {report.skipped} · Новых профилей:{' '}
              {report.createdProfiles}
            </p>
            <p>Ошибок: {report.errors.length}</p>
            {report.errors.length > 0 && (
              <div className={`${styles.table} fl-table-scroll`}>
                <table className="fl-table">
                  <thead>
                    <tr>
                      <th>Строка</th>
                      <th>Колонка</th>
                      <th>Сообщение</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.errors.map((err, i) => (
                      <tr key={i}>
                        <td>{err.row}</td>
                        <td>{err.column}</td>
                        <td className={styles.error}>{err.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
        <button title={event.status === 'ARCHIVED' ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : undefined}
          className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'}
          onClick={() => {
            onClose()
          }}
        >
          Закрыть
        </button>
      </div>
    </OrganizerDialog>
  )
}
