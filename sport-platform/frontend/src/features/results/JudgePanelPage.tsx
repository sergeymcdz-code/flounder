import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { getEvent } from '../../api/events.api'
import {
  changeCompetitionUnitPublication, changeHeatState, getCompetitionUnitDetails,
  getCompetitionUnitsByDiscipline, saveHeatResults, setLiveHeat,
  type CompetitionUnitDetailsResponse, type HeatResultRequest,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { maskTime, normalizeTime } from './time-input'
import { heatStatusLabel, resultErrorMessage } from './results-ui'
import styles from './JudgePanelPage.module.css'

type Draft = { time: string; status: HeatResultRequest['status'] }
const specialStatuses = ['DNS', 'DNF', 'DSQ'] as const
const statusMap: Record<string, Draft['status']> = {
  DID_NOT_START: 'DNS', DID_NOT_FINISH: 'DNF', DISQUALIFIED: 'DSQ', DNS: 'DNS', DNF: 'DNF', DSQ: 'DSQ',
}

export function JudgePanelPage() {
  const { unitId } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { session, hasAnyRole } = useAuth()
  const token = session?.accessToken
  const [unit, setUnit] = useState<CompetitionUnitDetailsResponse | null>(null)
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [savedAt, setSavedAt] = useState('')
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [nextId, setNextId] = useState<string | null>(null)
  const [nextError, setNextError] = useState('')
  const inputs = useRef<Record<string, HTMLInputElement | null>>({})
  const saveButton = useRef<HTMLButtonElement | null>(null)
  const eventId = unit?.eventId ?? params.get('eventId')
  const published = unit?.status === 'PUBLISHED'
  const canEdit = !published || hasAnyRole(['operator', 'organizer', 'platform_admin', 'platformadmin'])
  const loadedUnitId = unit?.id
  const disciplineId = unit?.eventDisciplineId
  const navigationWarning = (location.state as { liveWarning?: string } | null)?.liveWarning
  const statusButtons = useRef<Record<string, HTMLButtonElement | null>>({})
  const entries = [...(unit?.entries ?? [])].sort((a, b) =>
    (a.laneOrPosition ?? Infinity) - (b.laneOrPosition ?? Infinity))

  function applyUnit(data: CompetitionUnitDetailsResponse) {
    setUnit(data)
    setDrafts(Object.fromEntries(data.entries.map(e => [e.entryId, {
      time: e.display ?? e.rawValue ?? '', status: statusMap[e.resultStatus ?? ''] ?? 'VALID',
    }])))
    setDirty(false)
    setErrors({})
  }

  useEffect(() => {
    if (!token || !unitId) return
    let active = true
    Promise.resolve().then(() => {
      if (active) { setLoading(true); setError(''); setNotice(''); setSavedAt(''); setUnit(null) }
      return getCompetitionUnitDetails(unitId, token)
    }).then(data => { if (active) applyUnit(data) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить заплыв. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, unitId])

  useEffect(() => {
    if (!token || !disciplineId || loadedUnitId !== unitId) return
    const resolvedDisciplineId = disciplineId
    let active = true
    async function findNext() {
      await Promise.resolve()
      if (!active) return
      setNextId(null); setNextError('')
      const local = await getCompetitionUnitsByDiscipline(resolvedDisciplineId, token!)
      const currentSummary = local.find(u => u.id === loadedUnitId)
      let next = local.filter(u => (u.sequenceNumber ?? 0) > (currentSummary?.sequenceNumber ?? Infinity))
        .sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))[0]
      if (eventId && currentSummary?.scheduledAt) {
        const event = await getEvent(eventId, token)
        const lists = await Promise.all((event.disciplines ?? []).map(d => d.id === resolvedDisciplineId ? Promise.resolve(local) : getCompetitionUnitsByDiscipline(d.id, token!)))
        next = lists.flat().filter(u => u.scheduledAt && Date.parse(u.scheduledAt) > Date.parse(currentSummary.scheduledAt!))
          .sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))[0]
      }
      if (active) setNextId(next?.id ?? null)
    }
    void findNext().catch(error => { if (active) setNextError(resultErrorMessage(error, 'Не удалось определить следующий заплыв.')) })
    return () => { active = false }
  }, [token, loadedUnitId, disciplineId, unitId, eventId])

  function update(id: string, change: Partial<Draft>) {
    setDrafts(current => ({ ...current, [id]: { ...current[id], ...change } }))
    setErrors(current => ({ ...current, [id]: '' }))
    setDirty(true); setNotice('')
  }

  function validate(id: string) {
    const draft = drafts[id]
    if (draft?.status !== 'VALID' || !draft.time.trim()) {
      setErrors(current => ({ ...current, [id]: '' }))
      return true
    }
    const normalized = normalizeTime(draft.time)
    setErrors(current => ({ ...current, [id]: normalized ? '' : 'Введите время мм:сс.сс (секунды 00–59).' }))
    if (normalized) setDrafts(current => ({ ...current, [id]: { ...current[id], time: normalized } }))
    return Boolean(normalized)
  }

  async function run(action: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await action() } catch (e) {
      setError(resultErrorMessage(e, 'Не удалось выполнить действие. Повторите попытку.'))
    } finally { setBusy(false) }
  }

  function save() {
    if (!unit || !token || !canEdit) return
    const invalid = entries.filter(e => !validate(e.entryId))
    if (invalid.length) { inputs.current[invalid[0].entryId]?.focus(); return }
    void run(async () => {
      const data = await saveHeatResults(unit.id, entries.map(e => ({
        competitionUnitEntryId: e.entryId,
        status: drafts[e.entryId].status,
        time: drafts[e.entryId].status === 'VALID' ? normalizeTime(drafts[e.entryId].time) : null,
      })), token)
      applyUnit(data)
      setSavedAt(new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' }).format(new Date()))
    })
  }

  return <section className={styles.page}>
    <Link className={styles.backLink} to="/operator/results">← К списку заплывов</Link>
    <div className={styles.titleRow}><div><p className={styles.eyebrow}>Судейская панель</p>
      <h1 className={styles.title}>{unit?.label ?? 'Результаты'}</h1></div>
      {unit && <span className={styles.unitStatus}>{heatStatusLabel(unit.status)}</span>}
    </div>
    <p className={styles.description}>Введите цифры ммсссс или время 32.15. Enter / Tab — следующая дорожка, Esc — очистить. Допускается ч:мм:сс.сс и 32.1. Alt+D — DNS, Alt+N — DNF, Alt+Q — DSQ из поля времени. Пустые дорожки можно сохранить позже.</p>
    {loading && <p role="status">Загрузка…</p>}
    {error && <p className={styles.errorMessage} role="alert">{error}</p>}
    {navigationWarning && <p className={styles.recalculateHint} role="alert">{navigationWarning}</p>}
    {unit && !loading && <>
      <div className={styles.toolbarControls}>
        <button className={styles.saveButton} ref={saveButton} disabled={busy || !canEdit || !entries.length} onClick={save}>{published ? 'Сохранить исправление' : 'Сохранить и пересчитать'}</button>
        <button className={styles.publishButton} disabled={busy || (!published && (dirty || entries.some(e => !e.rawValue && !e.display && !statusMap[e.resultStatus ?? ''])))} onClick={() => void run(async () => {
          applyUnit(await changeCompetitionUnitPublication(unit.id, !published, token!))
          setNotice(published ? 'Публикация снята.' : 'Результаты опубликованы.')
        })}>{published ? 'Снять с публикации' : 'Опубликовать'}</button>
        {(['start', 'finish'] as const).map(action => <button key={action} className={styles.unpublishButton} disabled={busy || dirty} onClick={() => void run(async () => {
          await changeHeatState(unit.id, action, token!)
          const data = await getCompetitionUnitDetails(unit.id, token!)
          applyUnit(data); setNotice(action === 'start' ? 'Заплыв начат.' : 'Заплыв завершён.')
        })}>{action === 'start' ? 'Старт' : 'Финиш'}</button>)}
        <button className={styles.unpublishButton} disabled={busy || dirty || !nextId} onClick={() => void run(async () => {
          let liveWarning: string | undefined
          if (eventId) {
            try { await setLiveHeat(eventId, nextId!, token!) } catch (error) {
              liveWarning = `Следующий заплыв открыт, но табло не обновлено. ${resultErrorMessage(error, 'Не удалось обновить live. Повторите обновление табло.')}`
            }
          }
          navigate(`/operator/competition-units/${nextId}${eventId ? `?eventId=${encodeURIComponent(eventId)}` : ''}`, { state: { liveWarning } })
        })}>Следующий заплыв</button>
      </div>
      <p role="status">{dirty ? 'Есть несохранённые изменения' : savedAt ? `Сохранено ${savedAt}` : ''}</p>
      {notice && <p className={styles.successMessage} role="status">{notice}</p>}
      {nextError && <p role="status">{nextError}</p>}
      <div className={styles.tableWrap}><table className={styles.resultsTable}>
        <thead><tr><th>Дорожка</th><th>Участник</th><th>Заявочное время</th><th>Время</th><th>Статус</th><th>Место</th><th>В группе</th></tr></thead>
        <tbody>{entries.map((entry, index) => {
          const draft = drafts[entry.entryId] ?? { time: '', status: 'VALID' }
          return <tr key={entry.entryId}>
            <td className={styles.laneCell}>{entry.laneOrPosition ?? '—'}</td>
            <td><span className={styles.participantName}>{entry.participantName}</span></td><td>{entry.entryTime ?? '—'}</td>
            <td><input className={styles.resultInput} aria-label={`Время, дорожка ${entry.laneOrPosition}`} aria-invalid={Boolean(errors[entry.entryId])}
              aria-describedby={errors[entry.entryId] ? `error-${entry.entryId}` : undefined}
              ref={node => { inputs.current[entry.entryId] = node }} value={draft.time} inputMode="decimal" placeholder="00:32.15"
              disabled={busy || !canEdit || draft.status !== 'VALID'} onChange={e => update(entry.entryId, { time: maskTime(e.target.value) })}
              onBlur={e => {
                if ((e.relatedTarget as HTMLElement | null)?.dataset.entryId !== entry.entryId) validate(entry.entryId)
              }} onKeyDown={e => {
                const shortcut = e.altKey ? ({ KeyD: 'DNS', KeyN: 'DNF', KeyQ: 'DSQ' } as const)[e.code as 'KeyD' | 'KeyN' | 'KeyQ'] : undefined
                if (shortcut) {
                  e.preventDefault()
                  update(entry.entryId, { status: shortcut })
                  statusButtons.current[`${entry.entryId}-${shortcut}`]?.focus()
                  return
                }
                if (e.key === 'Escape') { e.preventDefault(); update(entry.entryId, { time: '' }); return }
                if (e.key === 'Enter' || (e.key === 'Tab' && !e.shiftKey)) {
                  e.preventDefault(); validate(entry.entryId)
                  const next = entries.slice(index + 1).find(row => drafts[row.entryId]?.status === 'VALID')
                  if (next) inputs.current[next.entryId]?.focus(); else saveButton.current?.focus()
                }
              }} />
              {errors[entry.entryId] && <p id={`error-${entry.entryId}`} className={styles.fieldError} role="alert">{errors[entry.entryId]}</p>}
            </td>
            <td><div className={styles.statusButtons}>{specialStatuses.map(status => <button key={status} type="button" data-entry-id={entry.entryId} aria-pressed={draft.status === status} disabled={busy || !canEdit}
              ref={node => { statusButtons.current[`${entry.entryId}-${status}`] = node }}
              onClick={() => update(entry.entryId, { status: draft.status === status ? 'VALID' : status })}>{status}</button>)}</div>{entry.resultStatus === 'CORRECTED' && <span className={`${styles.resultStatus} ${styles.resultStatusCorrected}`}>исправлено</span>}</td>
            <td className={styles.placeCell}>{entry.finalPlace ?? '—'}</td><td className={styles.placeCell}>{entry.placeInAgeGroup ?? '—'}</td>
          </tr>
        })}</tbody>
      </table></div>
      {!entries.length && <p>В заплыве пока нет участников.</p>}
    </>}
  </section>
}
