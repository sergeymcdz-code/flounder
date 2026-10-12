import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getEvent, getMyEvents, type Event, type EventDiscipline } from '../../api/events.api'
import { getCompetitionUnitsByDiscipline, type CompetitionUnitResponse } from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { heatStatusLabel, resultErrorMessage } from './results-ui'
import styles from './CompetitionUnitsPage.module.css'

export function CompetitionUnitsPage() {
  const { session } = useAuth()
  const token = session?.accessToken
  const navigate = useNavigate()
  const [events, setEvents] = useState<Event[]>([])
  const [eventId, setEventId] = useState('')
  const [disciplines, setDisciplines] = useState<EventDiscipline[]>([])
  const [disciplineId, setDisciplineId] = useState('')
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [unitId, setUnitId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!token) return
    let active = true
    Promise.resolve().then(() => { if (active) setLoading(true); return getMyEvents(token) }).then(data => { if (active) setEvents(data) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить события. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token])

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
    <p className={styles.eyebrow}>Оператор</p><h1 className={styles.title}>Результаты</h1>
    <form className={styles.searchForm} onSubmit={e => {
      e.preventDefault()
      if (unitId) navigate(`/operator/competition-units/${unitId}?eventId=${encodeURIComponent(eventId)}`)
    }}>
      <label className={styles.field}>Событие<select value={eventId} disabled={loading} onChange={e => {
        setEventId(e.target.value); setDisciplineId(''); setUnitId(''); setDisciplines([]); setUnits([])
      }}><option value="">Выберите событие</option>{events.map(event => <option key={event.id} value={event.id}>{event.title}</option>)}</select></label>
      <label className={styles.field}>Вид программы<select value={disciplineId} disabled={!eventId || loading} onChange={e => {
        setDisciplineId(e.target.value); setUnitId(''); setUnits([])
      }}><option value="">Выберите вид</option>{disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label className={styles.field}>Заплыв<select value={unitId} disabled={!disciplineId || loading} onChange={e => setUnitId(e.target.value)}>
        <option value="">Выберите заплыв</option>{units.map(unit => <option key={unit.id} value={unit.id}>{unit.label} — {heatStatusLabel(unit.status)}</option>)}
      </select></label>
      <button className={styles.submitButton} disabled={!unitId || loading}>Открыть</button>
    </form>
    {loading && <p role="status">Загрузка…</p>}
    {eventId && !loading && !error && disciplines.length === 0 && <p>Виды программы пока не добавлены.</p>}
    {disciplineId && !loading && !error && units.length === 0 && <p>Заплывы пока не созданы.</p>}
    {error && <p className={styles.errorMessage} role="alert">{error}</p>}
  </section>
}
