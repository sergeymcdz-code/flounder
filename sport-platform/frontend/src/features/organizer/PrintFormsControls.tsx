import { useId, useState, useSyncExternalStore } from 'react'
import { OverflowMenu } from '../../components/ui/OverflowMenu'
import { Button } from '../../components/ui/Button'
import { ActionBar } from '../../components/ui/ActionBar'
import { Notice } from '../../components/ui/Notice'
import { downloadProtocol } from '../../api/protocols.api'
import resultsStyles from './ResultsTab.module.css'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { assignBibs } from '../../api/print-forms.api'
import { OrganizerDialog } from './OrganizerDialog'
import { disabledReason, useOrganizerEvent } from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'

export function HeatPrintControls() {
  const { event, accessToken, busy, run } = useOrganizerEvent()
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState('BY_CLUB_THEN_NAME')
  const [startFrom, setStartFrom] = useState(1)
  const [reassign, setReassign] = useState(false)
  const [message, setMessage] = useState('')
  const archived = event.status === 'ARCHIVED'
  return <div className={styles.toolbar}>
    <button title={disabledReason({ busy, archived }) || undefined} className="fl-btn fl-btn-secondary" disabled={busy || archived} onClick={() => setOpen(true)}>Стартовые номера…</button>
    {message && <span role="status">{message}</span>}
    {open && <OrganizerDialog title="Стартовые номера" onClose={() => setOpen(false)}><form className={styles.stack} onSubmit={e => {
      e.preventDefault()
      void run(async () => { const result = await assignBibs(event.id, { mode, startFrom, reassign }, accessToken); setMessage(`Назначено ${result.assigned}, сохранено ${result.kept}, всего ${result.total}`); setOpen(false) })
    }}>
      <label>Порядок<select value={mode} onChange={e => setMode(e.target.value)}><option value="BY_CLUB_THEN_NAME">По клубу, затем по фамилии</option><option value="BY_NAME">По фамилии</option></select></label>
      <label>Начать с<input type="number" min="1" max="2147483647" required value={startFrom} onChange={e => setStartFrom(Number(e.target.value))} /></label>
      <label><input type="checkbox" checked={reassign} onChange={e => setReassign(e.target.checked)} />Перенумеровать всех</label>
      <p>После начала стартов перенумерация недоступна.</p>
      <button title={disabledReason({ busy, archived }) || undefined} className="fl-btn fl-btn-primary" type="submit" disabled={busy || archived}>Назначить номера</button>
    </form></OrganizerDialog>}
  </div>
}

export function HeatCardPrintOptions() {
  const { event, sessions, accessToken } = useOrganizerEvent()
  const [discipline, setDiscipline] = useState('')
  const [session, setSession] = useState('')
  function cardPath(format: 'A5' | 'A4x2') {
    const query = new URLSearchParams({ format })
    if (discipline) query.set('disciplineId', discipline)
    if (session) query.set('sessionId', session)
    return `/api/v1/events/${event.id}/documents/heat-cards.pdf?${query}`
  }
  return <div className={styles.stack}>
      <label>Сессия<select value={session} onChange={e => setSession(e.target.value)}><option value="">Все сессии</option>{sessions.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
      <label>Вид<select value={discipline} onChange={e => setDiscipline(e.target.value)}><option value="">Все виды</option>{event.disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      {(['A5', 'A4x2'] as const).map(format => <PdfDownloadButton key={format} path={cardPath(format)} filename="Карточки-заплывов.pdf" accessToken={accessToken} label={`Карточки заплывов (${format === 'A5' ? 'A5' : '2 на A4'})`} />)}
  </div>

}

export function AwardsPrintControls() {
  const { event, accessToken } = useOrganizerEvent()
  const [scope, setScope] = useState('AGE_GROUP')
  const [places, setPlaces] = useState('3')
  const validPlaces = places.trim() !== '' && Number.isInteger(Number(places)) && Number(places) >= 1 && Number(places) <= 500
  const [discipline, setDiscipline] = useState('')
  const [group, setGroup] = useState('')
  const query = new URLSearchParams({ scope, places: validPlaces ? String(Number(places)) : '3' })
  if (discipline) query.set('disciplineId', discipline)
  const groups = event.disciplines.filter(d => !discipline || d.id === discipline).flatMap(d => d.ageGroups ?? []).filter((g, i, all) => all.findIndex(x => x.code === g.code) === i)
  const diplomaQuery = new URLSearchParams(query)
  if (group && scope === 'AGE_GROUP') diplomaQuery.set('ageGroupCode', group)
  return <>
    <div className={resultsStyles.awards}>
      <label className="fl-field">Область награждения<select value={scope} onChange={e => setScope(e.target.value)}><option value="AGE_GROUP">По возрастным группам</option><option value="ABSOLUTE">Абсолют</option></select></label>
      <label className="fl-field">Число мест<input type="number" min="1" max="500" value={places} onChange={e => setPlaces(e.target.value)} /></label>
      <label className="fl-field">Вид награждения<select value={discipline} onChange={e => { setDiscipline(e.target.value); setGroup('') }}><option value="">Все виды</option>{event.disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label className="fl-field">Группа для грамот<select title={scope === 'ABSOLUTE' ? 'В абсолютном зачёте возрастные группы не используются' : undefined} disabled={scope === 'ABSOLUTE'} value={group} onChange={e => setGroup(e.target.value)}><option value="">Все группы</option>{groups.map(g => <option key={g.code} value={g.code}>{g.name}</option>)}</select>{scope === 'ABSOLUTE' && <small>В абсолютном зачёте возрастные группы не используются</small>}</label>
    </div>
    <ActionBar className={resultsStyles.printActions}>
      <ResultPdfButton path={`/api/v1/events/${event.id}/documents/awards.pdf?${query}`} filename="Лист-награждения.pdf" disabledReason={!validPlaces ? 'Введите число мест от 1 до 500' : undefined} accessToken={accessToken} label="Лист награждения (PDF)" />
      <ResultPdfButton path={`/api/v1/events/${event.id}/documents/diplomas.pdf?${diplomaQuery}`} filename="Грамоты.pdf" disabledReason={!validPlaces ? 'Введите число мест от 1 до 500' : undefined} accessToken={accessToken} label="Грамоты (PDF)" />
      <ResultPdfButton path={`/api/v1/events/${event.id}/documents/diplomas.pdf?sample=true`} filename="Пробная-грамота.pdf" accessToken={accessToken} label="Пробная грамота" />
    </ActionBar>
  </>
}

const desktopPrintQuery = '(min-width: 768px)'
function subscribePrintViewport(onChange: () => void) {
  const media = window.matchMedia?.(desktopPrintQuery)
  media?.addEventListener('change', onChange)
  return () => media?.removeEventListener('change', onChange)
}
const desktopPrintSnapshot = () => window.matchMedia?.(desktopPrintQuery).matches ?? false

export function ResultsPrintControls() {
  const { event, heats, accessToken, busy } = useOrganizerEvent()
  const [awardsOpen, setAwardsOpen] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState('')
  const desktop = useSyncExternalStore(subscribePrintViewport, desktopPrintSnapshot, () => false)
  const reason = busy || downloading ? 'Дождитесь завершения текущего действия' : !heats.some(h => h.entries.some(e => e.rawValue != null || (e.resultStatus != null && e.resultStatus !== 'PENDING'))) ? 'Сначала введите результаты заплывов' : undefined
  async function download() {
    setDownloading(true)
    setError('')
    try { await downloadProtocol(`/api/v1/events/${event.id}/documents/results.pdf`, 'Итоговый-протокол.pdf', accessToken) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось скачать PDF. Повторите попытку.') }
    finally { setDownloading(false) }
  }
  return <>
    <OverflowMenu label="Печать" trigger="Печать ▾" items={[
      { label: 'Печать итогового протокола', target: '_blank', href: `/e/${encodeURIComponent(event.publicSlug)}/results?print=1` },
      { label: 'Скачать PDF', disabled: !!reason, disabledReason: reason, onSelect: () => void download() },
      { label: 'Параметры награждения', onSelect: () => setAwardsOpen(true) },
    ]} />
    {reason && <small className={resultsStyles.muted}>{reason}</small>}
    {error && <Notice tone="danger">{error} <Button onClick={() => void download()}>Повторить</Button></Notice>}
    <OrganizerDialog open={awardsOpen} title="Печать результатов" onClose={() => setAwardsOpen(false)}>
      <details className={resultsStyles.awardDetails} open={desktop}>
        <summary>Параметры награждения</summary>
        <AwardsPrintControls />
      </details>
    </OrganizerDialog>
  </>
}

function ResultPdfButton({ path, filename, accessToken, label, disabledReason }: {
  path: string; filename: string; accessToken: string; label: string; disabledReason?: string
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const id = useId()
  const reason = busy ? 'Формируем PDF. Дождитесь завершения загрузки' : disabledReason
  async function download() {
    setBusy(true)
    setError('')
    try { await downloadProtocol(path, filename, accessToken) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось скачать PDF. Повторите попытку.') }
    finally { setBusy(false) }
  }
  return <div className={resultsStyles.pdfControl}>
    <Button variant="ghost" disabled={!!reason} loading={busy} title={reason} aria-describedby={reason ? id : undefined} onClick={() => void download()}>{label}</Button>
    {reason && <small id={id} className={resultsStyles.muted}>{reason}</small>}
    {error && <Notice tone="danger">{error} <Button onClick={() => void download()}>Повторить</Button></Notice>}
  </div>
}
