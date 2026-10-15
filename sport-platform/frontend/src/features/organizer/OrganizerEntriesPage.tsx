import { useState, type FormEvent } from 'react'
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
import styles from './OrganizerEventPage.module.css'
export function OrganizerEntriesPage() {
  const {
    event,
    registrations,
    heats,
    generationResults,
    accessToken,
    busy, error,
    run,
  } = useOrganizerEvent()
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
      (!discipline || r.eventDisciplineId === discipline) &&
      (!params.get('payment') || r.paymentStatus === params.get('payment')) &&
      (!missingMedical || !r.documents?.MEDICAL_CERT || r.documents.MEDICAL_CERT === 'NOT_UPLOADED') &&
      `${r.participantSnapshot.lastName} ${r.participantSnapshot.firstName} ${r.participantSnapshot.middleName ?? ''} ${r.participantSnapshot.clubName ?? ''}`
        .toLocaleLowerCase('ru')
        .includes(search.toLocaleLowerCase('ru')),
  )
  function openReview(id: string, decision: RegistrationReviewDecision) {
    setReview({ id, decision })
  }
  return (
    <article className={styles.card}>
      <div className={styles.toolbar}>
        <h2>Заявки участников · {registrations.length}</h2>
        <button
          className={styles.primary}
          disabled={busy || event.status === 'ARCHIVED'}
          onClick={() => setImportOpen(true)}
        >
          Импорт из Excel
        </button>
      </div>
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
      <div className={styles.filters}>
        <label className={styles.field}>
          Поиск
          <input
            placeholder="ФИО или клуб"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <label className={styles.field}>
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
        <label className={styles.field}>
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
        <label className={styles.field}>
          <span><input type="checkbox" checked={missingMedical} onChange={(e) => setMissingMedical(e.target.checked)} /> Нет справки</span>
        </label>
      </div>
      <p className={styles.muted}>✓ принят · ◷ на проверке · ✕ отклонён · — нет</p>
      {documentProfile && <OrganizerDialog title={`Документы · ${documentProfile.participantSnapshot.lastName}`} onClose={() => setDocumentProfile(null)}>
        <DocumentsPanel profileId={documentProfile.participantProfileId} eventId={event.id} accessToken={accessToken} manager
          onChanged={() => { void run(async () => undefined, 'registrations') }} />
      </OrganizerDialog>}
      <p className={styles.muted}>Найдено: {visible.length}</p>
      <div className={styles.table}>
        <table>
          <thead>
            <tr>
              {[
                '№',
                'ФИО',
                'Год',
                'Клуб',
                'Вид',
                'Допуск',
                'Документы',
                'Заявочное время',
                'Статус',
                'Оплата',
                'Действия',
              ].map((h) => (
                <th key={h}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((r, index) => (
              <tr key={r.id}>
                <td>{index + 1}</td>
                <td>
                  {r.participantSnapshot.lastName}{' '}
                  {r.participantSnapshot.firstName}{' '}
                  {r.participantSnapshot.middleName}
                  <details><summary>Подробнее</summary><p>Пол: {r.participantSnapshot.gender ?? '—'} · Источник: {r.source === 'IMPORT' ? 'Импорт' : 'Форма'} · Допуск: {admission(r).label}</p></details>
                </td>
                <td>{r.participantSnapshot.birthDate?.slice(0, 4) ?? '—'}</td>

                <td>{r.participantSnapshot.clubName || '—'}</td>
                <td>
                  {event.disciplines.find((d) => d.id === r.eventDisciplineId)
                    ?.name ?? '—'}
                </td>
                <td>
                  <span title={admission(r).title}>{admission(r).label}</span>
                </td>
                <td>
                  <button type="button" onClick={() => setDocumentProfile(r)} aria-label={`Документы ${r.participantSnapshot.lastName}`}>
                    {(Object.keys(documentTypes) as DocumentType[]).map((type) => {
                      const status = r.documents?.[type] ?? 'NOT_UPLOADED'
                      return <span key={type} title={`${documentTypes[type]}: ${documentStatusLabels[status]}`}>
                        {status === 'ACCEPTED' ? '✓' : status === 'PENDING' ? '◷' : status === 'REJECTED' ? '✕' : '—'}{' '}
                      </span>
                    })}
                  </button>
                  {r.admissionWarnings?.map((warning) => <div key={warning} className={styles.error}>{warning}</div>)}
                </td>
                <td>
                  <SeedTimeEditor registration={r} />
                </td>
                <td>
                  <StatusBadge kind="registration" code={r.status} />{r.waitlistPosition != null ? ` · № ${r.waitlistPosition}` : ''}
                </td>
                <td><StatusBadge kind="payment" code={r.paymentStatus} /></td>
                <td>
                  <div className={styles.actions}>
                    {['SUBMITTED', 'UNDER_REVIEW'].includes(r.status) ? <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => openReview(r.id, 'CONFIRMED')}>Подтвердить</button>
                      : ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status) ? r.paymentStatus === 'PENDING' ? <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => void run(() => registrationAction(r.id, 'mark-paid', accessToken), 'registrations')}>Оплачено</button>
                      : <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => setWithdraw(r)}>Снять</button> : null}
                    {!['WITHDRAWN', 'CANCELLED', 'REJECTED'].includes(r.status) && <details><summary>Ещё ▾</summary>
                      {r.status === 'WAITLISTED' && <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => openReview(r.id, 'CONFIRMED')}>Подтвердить вне лимита</button>}
                      {['SUBMITTED', 'UNDER_REVIEW'].includes(r.status) && <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => openReview(r.id, 'NEEDS_CORRECTION')}>Вернуть</button>}
                      {!['PAID', 'ADMITTED'].includes(r.status) && <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => openReview(r.id, 'REJECTED')}>Отклонить</button>}
                      <button disabled={busy || event.status === 'ARCHIVED'} onClick={() => setWithdraw(r)}>Снять</button>
                    </details>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!visible.length && (
        <p className={`${styles.muted} fl-empty`}>
          Заявок не найдено. Измените фильтры или импортируйте заявки.
        </p>
      )}
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
    <form onSubmit={submit}>
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
        <button disabled={busy || event.status === 'ARCHIVED'}>Сохранить</button>
        <button type="button" disabled={busy || event.status === 'ARCHIVED'} onClick={() => setEditing(false)}>
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
      disabled={busy || event.status === 'ARCHIVED'}
      title="Изменить заявочное время"
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
        <button
          disabled={busy || event.status === 'ARCHIVED'}
          className={styles.link}
          onClick={() => {
            void download()
          }}
        >
          Скачать шаблон
        </button>
        <form className={styles.form} onSubmit={submit}>
          <label className={styles.field}>
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
          <button className={styles.primary} disabled={busy || !file}>
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
              <div className={styles.table}>
                <table>
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
        <button
          disabled={busy || event.status === 'ARCHIVED'}
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
