import { useState } from 'react'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { assignBibs } from '../../api/print-forms.api'
import { OrganizerDialog } from './OrganizerDialog'
import { useOrganizerEvent } from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'

export function HeatPrintControls() {
  const { event, sessions, accessToken, busy, run } = useOrganizerEvent()
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState('BY_CLUB_THEN_NAME')
  const [startFrom, setStartFrom] = useState(1)
  const [reassign, setReassign] = useState(false)
  const [message, setMessage] = useState('')
  const [discipline, setDiscipline] = useState('')
  const [session, setSession] = useState('')
  const query = new URLSearchParams()
  if (discipline) query.set('disciplineId', discipline)
  if (session) query.set('sessionId', session)
  const archived = event.status === 'ARCHIVED'
  return <div className={styles.toolbar}>
    <button disabled={busy || archived} onClick={() => setOpen(true)}>Стартовые номера…</button>
    {message && <span role="status">{message}</span>}
    <details><summary>Печать</summary><div className={styles.stack}>
      <label>Сессия<select value={session} onChange={e => setSession(e.target.value)}><option value="">Все сессии</option>{sessions.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
      <label>Вид<select value={discipline} onChange={e => setDiscipline(e.target.value)}><option value="">Все виды</option>{event.disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      {(['A5', 'A4x2'] as const).map(format => <PdfDownloadButton key={format} path={`/api/v1/events/${event.id}/documents/heat-cards.pdf?${query}&format=${format}`} filename="Карточки-заплывов.pdf" accessToken={accessToken} label={`Карточки заплывов (${format === 'A5' ? 'A5' : '2 на A4'})`} />)}
    </div></details>
    {open && <OrganizerDialog title="Стартовые номера" onClose={() => setOpen(false)}><form className={styles.stack} onSubmit={e => {
      e.preventDefault()
      void run(async () => { const result = await assignBibs(event.id, { mode, startFrom, reassign }, accessToken); setMessage(`Назначено ${result.assigned}, сохранено ${result.kept}, всего ${result.total}`); setOpen(false) })
    }}>
      <label>Порядок<select value={mode} onChange={e => setMode(e.target.value)}><option value="BY_CLUB_THEN_NAME">По клубу, затем по фамилии</option><option value="BY_NAME">По фамилии</option></select></label>
      <label>Начать с<input type="number" min="1" max="2147483647" required value={startFrom} onChange={e => setStartFrom(Number(e.target.value))} /></label>
      <label><input type="checkbox" checked={reassign} onChange={e => setReassign(e.target.checked)} />Перенумеровать всех</label>
      <p>После начала стартов перенумерация недоступна.</p>
      <button type="submit" disabled={busy || archived}>Назначить номера</button>
    </form></OrganizerDialog>}
  </div>
}

export function AwardsPrintControls() {
  const { event, accessToken } = useOrganizerEvent()
  const [scope, setScope] = useState('AGE_GROUP')
  const [places, setPlaces] = useState(3)
  const [discipline, setDiscipline] = useState('')
  const [group, setGroup] = useState('')
  const query = new URLSearchParams({ scope, places: String(places) })
  if (discipline) query.set('disciplineId', discipline)
  const groups = event.disciplines.filter(d => !discipline || d.id === discipline).flatMap(d => d.ageGroups ?? []).filter((g, i, all) => all.findIndex(x => x.code === g.code) === i)
  const diplomaQuery = new URLSearchParams(query)
  if (group && scope === 'AGE_GROUP') diplomaQuery.set('ageGroupCode', group)
  return <div className={styles.toolbar}>
    <label>Область награждения<select value={scope} onChange={e => setScope(e.target.value)}><option value="AGE_GROUP">По возрастным группам</option><option value="ABSOLUTE">Абсолют</option></select></label>
    <label>Число мест<input type="number" min="1" max="500" value={places} onChange={e => setPlaces(Number(e.target.value))} /></label>
    <label>Вид награждения<select value={discipline} onChange={e => { setDiscipline(e.target.value); setGroup('') }}><option value="">Все виды</option>{event.disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
    <label>Группа для грамот<select disabled={scope === 'ABSOLUTE'} value={group} onChange={e => setGroup(e.target.value)}><option value="">Все группы</option>{groups.map(g => <option key={g.code} value={g.code}>{g.name}</option>)}</select></label>
    <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/awards.pdf?${query}`} filename="Лист-награждения.pdf" accessToken={accessToken} label="Лист награждения (PDF)" />
    <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/diplomas.pdf?${diplomaQuery}`} filename="Грамоты.pdf" accessToken={accessToken} label="Грамоты (PDF)" />
    <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/diplomas.pdf?sample=true`} filename="Пробная-грамота.pdf" accessToken={accessToken} label="Пробная грамота" />
  </div>
}
