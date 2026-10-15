import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { getStandings, getStandingsConfig, saveStandingsConfig, type Standings, type StandingsConfig } from '../../api/standings.api'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { useOrganizerEvent } from './organizer-event-context'
import { StandingsTables } from '../standings/StandingsTables'
import styles from '../standings/Standings.module.css'

export function OrganizerStandingsPage() {
  const { event, accessToken, run, busy } = useOrganizerEvent()
  const [config, setConfig] = useState<StandingsConfig>()
  const [points, setPoints] = useState('')
  const [result, setResult] = useState<Standings>()
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const reload = useCallback(async () => {
    const [nextConfig, nextResult] = await Promise.all([
      getStandingsConfig(event.id, accessToken), getStandings(event.id, accessToken),
    ])
    return { nextConfig, nextResult }
  }, [event.id, accessToken])
  useEffect(() => {
    let active = true
    reload().then(({ nextConfig, nextResult }) => {
      if (!active) return
      setConfig(nextConfig); setPoints(nextConfig.pointsByPlace.join(',')); setResult(nextResult)
    }).catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause.message : 'Не удалось загрузить зачёт') })
    return () => { active = false }
  }, [reload])
  function change(patch: Partial<StandingsConfig>) {
    setSaved(false)
    setConfig(previous => previous ? { ...previous, ...patch } : previous)
  }
  async function save(formEvent: FormEvent) {
    formEvent.preventDefault()
    if (!config) return
    const values = points.split(',').map(value => value.trim())
    if (values.length > 32 || values.some((value, index) => !/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) > 2147483647 || (index > 0 && Number(value) > Number(values[index - 1])))) {
      setError('Укажите от 1 до 32 целых неотрицательных очков по убыванию, через запятую.'); return
    }
    setError(''); setSaved(false)
    const success = await run(async () => {
      const next = await saveStandingsConfig(event.id, { ...config, pointsByPlace: values.map(Number) }, accessToken)
      setConfig(next); setPoints(next.pointsByPlace.join(','))
      setResult(await getStandings(event.id, accessToken))
    }, 'none')
    if (success) setSaved(true)
  }
  return <div className={styles.page}>
    <h2>Зачёт</h2>
    {error && <p role="alert">{error}</p>}
    {!config && !error && <p role="status">Загружаем зачёт…</p>}
    {config && <form className={styles.form} onSubmit={save}>
      <fieldset disabled={busy || event.status === 'ARCHIVED'}>
        <legend>Настройки зачёта</legend>
        <label><input type="checkbox" checked={config.enabled} onChange={e => change({ enabled: e.target.checked })} />Включить зачёт</label>
        <label>Область зачёта<select value={config.scope} onChange={e => change({ scope: e.target.value as StandingsConfig['scope'] })}>
          <option value="AGE_GROUP">По возрастным группам</option><option value="ABSOLUTE">В абсолюте</option>
        </select></label>
        <label>Очки за места<input value={points} onChange={e => { setPoints(e.target.value); setSaved(false) }} required aria-describedby="points-help" /></label>
        <small id="points-help">Через запятую, от 1 до 32 мест. Например: 9,7,6,5,4,3,2,1</small>
        <label>Лимит результатов клуба в виде / группе<input type="number" min="1" step="1" value={config.maxScoredPerClubPerDiscipline ?? ''} onChange={e => change({ maxScoredPerClubPerDiscipline: e.target.value ? Number(e.target.value) : null })} placeholder="Все результаты" /></label>
        <label>Заголовок<input maxLength={255} value={config.title ?? ''} onChange={e => change({ title: e.target.value || null })} /></label>
        <fieldset><legend>Виды программы</legend>
          <label><input type="checkbox" checked={config.disciplineIds === null} onChange={e => change({ disciplineIds: e.target.checked ? null : [] })} />Все виды</label>
          {event.disciplines.map(d => <label key={d.id}><input type="checkbox" checked={config.disciplineIds === null || config.disciplineIds.includes(d.id)} onChange={e => {
            const selected = config.disciplineIds ?? event.disciplines.map(item => item.id)
            change({ disciplineIds: e.target.checked ? [...selected, d.id] : selected.filter(id => id !== d.id) })
          }} />{d.name}</label>)}
        </fieldset>
        <label><input type="checkbox" checked={config.published} onChange={e => change({ published: e.target.checked })} />Показывать публично</label>
        <button className="fl-button-primary" type="submit">Сохранить настройки</button>
      </fieldset>
      {saved && <p role="status">Настройки сохранены, зачёт пересчитан.</p>}
    </form>}
    <div className={styles.actions}>
      <button className="fl-button-ghost" disabled={busy} onClick={() => void run(async () => setResult(await getStandings(event.id, accessToken)), 'none')}>Обновить</button>
      <PdfDownloadButton path={`/api/v1/events/${encodeURIComponent(event.id)}/documents/standings.pdf`} filename="Зачёт.pdf" accessToken={accessToken} disabled={busy || !result} />
    </div>
    {result && <StandingsTables data={result} disciplineNames={Object.fromEntries(event.disciplines.map(d => [d.id, d.name]))} />}
  </div>
}
