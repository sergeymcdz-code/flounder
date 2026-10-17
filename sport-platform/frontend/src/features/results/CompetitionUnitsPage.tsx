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
  const [events, setEvents] = useState<Event[]>([])
  const [eventId, setEventId] = useState(() => localStorage.getItem('sportgate.judge.eventId') ?? '')
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [disciplineId, setDisciplineId] = useState(() => localStorage.getItem('sportgate.judge.disciplineId') ?? '')
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

  return <section className={styles.page}>
    <h1 className={styles.title}>Судейство</h1>
    <CurrentHeatButton eventId={eventId} slug={events.find(event => event.id === eventId)?.publicSlug} />
    <form className={styles.searchForm} onSubmit={e => {
      e.preventDefault()
      if (unitId) navigate(`/operator/competition-units/${unitId}?eventId=${encodeURIComponent(eventId)}`)
    }}>
      <label className={styles.field}>Соревнование<select value={eventId} disabled={loading} onChange={e => {
        setEventId(e.target.value); localStorage.setItem('sportgate.judge.eventId', e.target.value); localStorage.removeItem('sportgate.judge.disciplineId'); setDisciplineId(''); setUnitId(''); setDisciplines([]); setUnits([])
      }}><option value="">Выберите соревнование</option>{events.map(event => <option key={event.id} value={event.id}>{event.title}</option>)}</select></label>
      <label className={styles.field}>Вид программы<select value={disciplineId} disabled={!eventId || loading} onChange={e => {
        setDisciplineId(e.target.value); localStorage.setItem('sportgate.judge.disciplineId', e.target.value); setUnitId(''); setUnits([])
      }}><option value="">Выберите вид</option>{disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label className={styles.field}>Заплыв<select value={unitId} disabled={!disciplineId || loading} onChange={e => setUnitId(e.target.value)}>
        <option value="">Выберите заплыв</option>{units.map(unit => <option key={unit.id} value={unit.id}>{unit.label} — {heatStatusLabel(unit.status)}</option>)}
      </select></label>
      <button className={styles.submitButton} disabled={!unitId || loading}>Открыть</button>
    </form>
    {units.length > 0 && <div style={{ overflowX: 'auto', maxWidth: '100%' }}><table><thead><tr><th>№</th><th>Время</th><th>Статус</th><th>Действия</th></tr></thead><tbody>{units.map(unit => <tr key={unit.id}><td>{unit.sequenceNumber ?? unit.label}</td><td>{formatDateTime(unit.scheduledAt, events.find(event => event.id === eventId)?.venue?.timezone)}</td><td><StatusBadge kind="heat" code={unit.status} /></td><td><button onClick={() => navigate(`/operator/competition-units/${unit.id}?eventId=${encodeURIComponent(eventId)}`)}>Открыть →</button></td></tr>)}</tbody></table></div>}
    {loading && <p role="status">Загрузка…</p>}
    {eventId && !loading && !error && disciplines.length === 0 && <p>Виды программы пока не добавлены.</p>}
    {disciplineId && !loading && !error && units.length === 0 && <p>Заплывы пока не созданы.</p>}
    {error && <p className={styles.errorMessage} role="alert">{error}</p>}
  </section>
}
