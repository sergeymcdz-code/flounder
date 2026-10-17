import { JudgingHeatList } from './JudgingHeatList'
import { getEventDisciplines } from '../../api/organizer.api'
import { getSessions } from '../../api/sessions.api'
import type { Session, SwimDiscipline } from '../../api/organizer.types'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../utils/formatDateTime'
import { CurrentHeatButton } from './CurrentHeatButton'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getEvent, getMyEvents, type Event, type EventDiscipline } from '../../api/events.api'
import { getCompetitionUnitsByDiscipline, type CompetitionUnitResponse } from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { heatStatusLabel, resultErrorMessage } from './results-ui'
import styles from './CompetitionUnitsPage.module.css'

export function CompetitionUnitsPage() {
  const { session, isOrganizer, isPlatformAdmin } = useAuth()
  const token = session?.accessToken
  const navigate = useNavigate()
  const [today] = useState(() => new Date().toISOString())
  const [events, setEvents] = useState<Event[]>([])
  const [eventId, setEventId] = useState(() => localStorage.getItem('sportgate.judge.eventId') ?? '')
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [disciplineId, setDisciplineId] = useState(() => localStorage.getItem('sportgate.judge.disciplineId') ?? '')
  const [list, setList] = useState<{ eventId: string; sessions: Session[]; disciplines: SwimDiscipline[]; units: CompetitionUnitResponse[] } | null>(null)
  const [listError, setListError] = useState('')
  useEffect(() => {
    if (!token || !eventId) return
    let active = true
    Promise.all([getSessions(eventId, token), getEventDisciplines(eventId, token)]).then(async ([sessions, disciplines]) => {
      const units = (await Promise.all(disciplines.map(d => getCompetitionUnitsByDiscipline(d.id, token)))).flat().sort((a,b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))
      if (active) { setList({ eventId, sessions, disciplines, units }); setListError('') }
    }).catch(cause => { if (active) setListError(resultErrorMessage(cause, 'Не удалось загрузить список заплывов. Выберите вид программы ниже.')) })
    return () => { active = false }
  }, [token, eventId])
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [unitId, setUnitId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!token) return
    let active = true
    Promise.resolve().then(() => { if (active) setLoading(true); return getMyEvents(token, isOrganizer || isPlatformAdmin) }).then(data => { if (active) setEvents(data) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить соревнования. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, isOrganizer, isPlatformAdmin])

  useEffect(() => {
    if (!token || !eventId) return
    let active = true
    Promise.resolve().then(() => { if (active) { setLoading(true); setError('') }; return getEvent(eventId, token) }).then(data => { if (active) setDisciplines(data.disciplines ?? []) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить виды программы. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, eventId])

  useEffect(() => {
    if (!token || !disciplineId) return
    let active = true
    Promise.resolve().then(() => { if (active) { setLoading(true); setError('') }; return getCompetitionUnitsByDiscipline(disciplineId, token) }).then(data => {
      if (active) setUnits([...data].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0)))
    }).catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить заплывы. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, disciplineId])

  const todayEvents = events.filter(event => {
    if (!['LIVE', 'REGISTRATION_CLOSED', 'REGISTRATIONCLOSED'].includes(event.status)) return false
    const day = new Intl.DateTimeFormat('sv-SE', { timeZone: event.venue?.timezone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' })
    const current = day.format(new Date(today))
    return day.format(new Date(event.eventStartAt)) <= current && current <= day.format(new Date(event.eventEndAt))
  })
  return <section className={styles.page}>
    <h1 className={styles.title}>Судейство</h1>
    <CurrentHeatButton eventId={eventId} slug={events.find(event => event.id === eventId)?.publicSlug} />
    {todayEvents.length > 0 && <section><h2>Сегодня</h2><div className="fl-actionbar">{todayEvents.map(event => <button className="fl-btn fl-btn-secondary" key={event.id} onClick={() => { setEventId(event.id); localStorage.setItem('sportgate.judge.eventId', event.id); setDisciplineId(''); setUnitId(''); setUnits([]); localStorage.removeItem('sportgate.judge.disciplineId') }}>{event.title}</button>)}</div></section>}
    {list?.eventId === eventId && <JudgingHeatList eventId={eventId} sessions={list.sessions} disciplines={list.disciplines} units={list.units} timeZone={events.find(event => event.id === eventId)?.venue?.timezone} />}
    {listError && <p role="alert">{listError}</p>}
    <details open={!list || list.eventId !== eventId}><summary>Выбрать соревнование и заплыв с клавиатуры</summary><form className={styles.searchForm} onSubmit={e => {
      e.preventDefault()
      if (unitId) navigate(`/operator/competition-units/${unitId}?eventId=${encodeURIComponent(eventId)}`)
    }}>
      <label className="fl-field">Соревнование<select value={eventId} disabled={loading} onChange={e => {
        setEventId(e.target.value); localStorage.setItem('sportgate.judge.eventId', e.target.value); localStorage.removeItem('sportgate.judge.disciplineId'); setDisciplineId(''); setUnitId(''); setDisciplines([]); setUnits([])
      }}><option value="">Выберите соревнование</option>{events.map(event => <option key={event.id} value={event.id}>{event.title}</option>)}</select></label>
      <label className="fl-field">Вид программы<select value={disciplineId} disabled={!eventId || loading} onChange={e => {
        setDisciplineId(e.target.value); localStorage.setItem('sportgate.judge.disciplineId', e.target.value); setUnitId(''); setUnits([])
      }}><option value="">Выберите вид</option>{disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label className="fl-field">Заплыв<select value={unitId} disabled={!disciplineId || loading} onChange={e => setUnitId(e.target.value)}>
        <option value="">Выберите заплыв</option>{units.map(unit => <option key={unit.id} value={unit.id}>{unit.label} — {heatStatusLabel(unit.status)}</option>)}
      </select></label>
      <button className="fl-btn fl-btn-primary" disabled={!unitId || loading}>Открыть</button>
    </form></details>
    {units.length > 0 && (!list || list.eventId !== eventId) && <div style={{ overflowX: 'auto', maxWidth: '100%' }}><table className="fl-table"><thead><tr><th>№</th><th>Время</th><th>Статус</th><th>Действия</th></tr></thead><tbody>{units.map(unit => <tr key={unit.id}><td>{unit.sequenceNumber ?? unit.label}</td><td>{formatDateTime(unit.scheduledAt, events.find(event => event.id === eventId)?.venue?.timezone)}</td><td><StatusBadge kind="heat" code={unit.status} /></td><td><button className="fl-btn fl-btn-secondary" onClick={() => navigate(`/operator/competition-units/${unit.id}?eventId=${encodeURIComponent(eventId)}`)}>Открыть →</button></td></tr>)}</tbody></table></div>}
    {loading && <p role="status">Загрузка…</p>}
    {eventId && !loading && !error && disciplines.length === 0 && <p>Виды программы пока не добавлены.</p>}
    {disciplineId && !loading && !error && units.length === 0 && <p>Заплывы пока не созданы.</p>}
    {error && <p className={styles.errorMessage} role="alert">{error}</p>}
  </section>
}
