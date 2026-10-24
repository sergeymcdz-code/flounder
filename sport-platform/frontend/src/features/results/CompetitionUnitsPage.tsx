import { PageHeader } from '../../components/ui/PageHeader'
import { Notice } from '../../components/ui/Notice'
import { EmptyState } from '../../components/ui/EmptyState'
import { Button } from '../../components/ui/Button'
import { JudgingHeatList } from './JudgingHeatList'
import { getEventDisciplines } from '../../api/organizer.api'
import { getSessions } from '../../api/sessions.api'
import type { Session, SwimDiscipline } from '../../api/organizer.types'
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
  const [reload, setReload] = useState(0)
  useEffect(() => {
    if (!token || !eventId) return
    let active = true
    Promise.all([getSessions(eventId, token), getEventDisciplines(eventId, token)]).then(async ([sessions, disciplines]) => {
      const units = (await Promise.all(disciplines.map(d => getCompetitionUnitsByDiscipline(d.id, token)))).flat().sort((a,b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))
      if (active) { setList({ eventId, sessions, disciplines, units }); setListError('') }
    }).catch(cause => { if (active) setListError(resultErrorMessage(cause, 'Не удалось загрузить список заплывов. Выберите вид программы ниже.')) })
    return () => { active = false }
  }, [token, eventId, reload])
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [unitId, setUnitId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(Boolean(token))

  useEffect(() => {
    if (!token) return
    let active = true
    Promise.resolve().then(() => { if (active) { setLoading(true); setError('') }; return getMyEvents(token, isOrganizer || isPlatformAdmin) }).then(data => { if (active) setEvents(data) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить соревнования. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, isOrganizer, isPlatformAdmin, reload])

  useEffect(() => {
    if (!token || !eventId) return
    let active = true
    Promise.resolve().then(() => { if (active) { setLoading(true); setError('') }; return getEvent(eventId, token) }).then(data => { if (active) setDisciplines(data.disciplines ?? []) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить виды программы. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, eventId, reload])

  useEffect(() => {
    if (!token || !disciplineId) return
    let active = true
    Promise.resolve().then(() => { if (active) { setLoading(true); setError('') }; return getCompetitionUnitsByDiscipline(disciplineId, token) }).then(data => {
      if (active) setUnits([...data].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0)))
    }).catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить заплывы. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, disciplineId, reload])

  const todayEvents = events.filter(event => {
    if (!['LIVE', 'REGISTRATION_CLOSED', 'REGISTRATIONCLOSED'].includes(event.status)) return false
    const day = new Intl.DateTimeFormat('sv-SE', { timeZone: event.venue?.timezone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' })
    const current = day.format(new Date(today))
    return day.format(new Date(event.eventStartAt)) <= current && current <= day.format(new Date(event.eventEndAt))
  })
  return <section className={styles.page}>
    <PageHeader title="Судейство" meta="Выберите соревнование и откройте заплыв для ввода результатов." />
    <CurrentHeatButton eventId={eventId} slug={events.find(event => event.id === eventId)?.publicSlug} />
    {todayEvents.length > 0 && <section><h2>Сегодня</h2><div className="fl-actionbar">{todayEvents.map(event => <button className="fl-btn fl-btn-secondary" key={event.id} onClick={() => { setEventId(event.id); localStorage.setItem('sportgate.judge.eventId', event.id); setDisciplineId(''); setUnitId(''); setUnits([]); localStorage.removeItem('sportgate.judge.disciplineId') }}>{event.title}</button>)}</div></section>}
    {list?.eventId === eventId && <JudgingHeatList eventId={eventId} sessions={list.sessions} disciplines={list.disciplines} units={list.units} timeZone={events.find(event => event.id === eventId)?.venue?.timezone} />}
    {listError && <Notice tone="danger">{listError}<Button onClick={() => setReload(value => value + 1)}>Повторить</Button></Notice>}
    <details className={styles.selection} open={!list || list.eventId !== eventId}><summary className="fl-btn fl-btn-ghost">Выбрать соревнование и заплыв с клавиатуры</summary><form className={styles.searchForm} onSubmit={e => {
      e.preventDefault()
      if (unitId) navigate(`/operator/competition-units/${unitId}?eventId=${encodeURIComponent(eventId)}`)
    }}>
      <label className="fl-field">Соревнование<select value={eventId} disabled={loading} title={loading ? 'Загружаем соревнования…' : undefined} onChange={e => {
        setEventId(e.target.value); localStorage.setItem('sportgate.judge.eventId', e.target.value); localStorage.removeItem('sportgate.judge.disciplineId'); setDisciplineId(''); setUnitId(''); setDisciplines([]); setUnits([])
      }}><option value="">Выберите соревнование</option>{events.map(event => <option key={event.id} value={event.id}>{event.title}</option>)}</select></label>
      <label className="fl-field">Вид программы<select value={disciplineId} disabled={!eventId || loading} title={!eventId ? 'Сначала выберите соревнование.' : loading ? 'Загружаем виды программы…' : undefined} aria-describedby="judge-selection-hint" onChange={e => {
        setDisciplineId(e.target.value); localStorage.setItem('sportgate.judge.disciplineId', e.target.value); setUnitId(''); setUnits([])
      }}><option value="">Выберите вид</option>{disciplines.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label className="fl-field">Заплыв<select value={unitId} disabled={!disciplineId || loading} title={!disciplineId ? 'Сначала выберите вид программы.' : loading ? 'Загружаем заплывы…' : undefined} aria-describedby="judge-selection-hint" onChange={e => setUnitId(e.target.value)}>
        <option value="">Выберите заплыв</option>{units.map(unit => <option key={unit.id} value={unit.id}>{unit.label} — {heatStatusLabel(unit.status)}</option>)}
      </select></label>
      <button className="fl-btn fl-btn-secondary" disabled={!unitId || loading} title={loading ? 'Загружаем данные…' : !unitId ? 'Сначала выберите заплыв.' : undefined} aria-describedby="judge-selection-hint">Открыть выбранный заплыв</button>
      <p id="judge-selection-hint" role="status">{loading ? 'Загружаем данные…' : !eventId ? 'Выберите соревнование, затем вид программы и заплыв.' : !disciplineId ? 'Выберите вид программы, затем заплыв.' : !unitId ? 'Выберите заплыв.' : 'Откроется выбранный заплыв.'}</p>
    </form></details>
    {units.length > 0 && (!list || list.eventId !== eventId) && <JudgingHeatList eventId={eventId} sessions={[]} disciplines={disciplines.filter(d => d.id === disciplineId) as SwimDiscipline[]} units={units} timeZone={events.find(event => event.id === eventId)?.venue?.timezone} />}
    {loading && <div className={styles.loading} role="status">Загружаем данные…{[0, 1, 2].map(index => <div className={styles.skeleton} aria-hidden="true" key={index} />)}</div>}
    {eventId && !loading && !error && disciplines.length === 0 && <EmptyState title="Виды программы пока не добавлены" action={<Button onClick={() => setReload(value => value + 1)}>Обновить список</Button>}><p>После подготовки программы здесь появятся заплывы.</p></EmptyState>}
    {disciplineId && !loading && !error && units.length === 0 && <EmptyState title="Заплывы пока не созданы" action={<Button onClick={() => setReload(value => value + 1)}>Обновить список</Button>}><p>Выберите другой вид программы или дождитесь формирования заплывов.</p></EmptyState>}
    {error && <Notice tone="danger">{error}<Button onClick={() => setReload(value => value + 1)}>Повторить</Button></Notice>}
    {!loading && !error && events.length === 0 && <EmptyState title="Нет доступных соревнований" action={<Button onClick={() => setReload(value => value + 1)}>Обновить список</Button>}><p>Попросите организатора предоставить доступ к соревнованию.</p></EmptyState>}
  </section>
}
