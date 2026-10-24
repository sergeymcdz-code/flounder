import { Button } from '../../components/ui/Button'
import { Notice } from '../../components/ui/Notice'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { JudgeActionBar } from './JudgeActionBar'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { useToast } from '../../components/ui/Toast'
import { getSessions } from '../../api/sessions.api'
import { getEvent } from '../../api/events.api'
import { publicApi } from '../../api/public.api'
import { formatDateTime } from '../../utils/formatDateTime'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { getEventDisciplines } from '../../api/organizer.api'
import {
  changeCompetitionUnitPublication, changeHeatState, getCompetitionUnitDetails,
  getCompetitionUnitsByDiscipline, saveHeatResults, setLiveHeat,
  type CompetitionUnitDetailsResponse, type HeatResultRequest,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { maskTime, normalizeTime } from './time-input'
import { ResultStatus } from './ResultStatus'
import { StatusLegend } from './StatusLegend'
import { resultErrorMessage } from './results-ui'
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
  const toast = useToast()
  const [publicationOpen, setPublicationOpen] = useState(false)
  const [context, setContext] = useState({ name: '', number: 0, total: 0, sequence: 0, time: '', session: '', slug: '' })
  const [clubs, setClubs] = useState<Record<string, string | null>>({})
  const [unit, setUnit] = useState<CompetitionUnitDetailsResponse | null>(null)
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [savedAt, setSavedAt] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [reload, setReload] = useState(0)
  const clubCache = useRef(new Map<string, ReturnType<typeof publicApi.startList>>())
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [nextId, setNextId] = useState<string | null>(null)
  const [nextError, setNextError] = useState('')
  const [mobile, setMobile] = useState(() => window.matchMedia?.('(max-width: 767px)').matches ?? false)
  useEffect(() => {
    const query = window.matchMedia?.('(max-width: 767px)')
    if (!query) return
    const change = () => setMobile(query.matches)
    query.addEventListener('change', change)
    return () => query.removeEventListener('change', change)
  }, [])
  const Results = mobile ? 'div' : 'table'
  const Rows = mobile ? 'div' : 'tbody'
  const Row = mobile ? 'article' : 'tr'
  const Cell = mobile ? 'div' : 'td'
  const tableScroll = useRef<HTMLDivElement>(null)
  const [scrollEdges, setScrollEdges] = useState('')
  function measureTableScroll() {
    const node = tableScroll.current
    if (node) setScrollEdges(`${node.scrollLeft > 1 ? 'left' : ''} ${node.scrollLeft + node.clientWidth < node.scrollWidth - 1 ? 'right' : ''}`)
  }
  useEffect(() => {
    measureTableScroll()
    const node = tableScroll.current
    if (!node || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measureTableScroll)
    observer.observe(node)
    return () => observer.disconnect()
  }, [mobile, unit])
  const inputs = useRef<Record<string, HTMLInputElement | null>>({})
  const saveButton = useRef<HTMLButtonElement | null>(null)
  const eventId = unit?.eventId ?? params.get('eventId')
  const resultsPublished = ['FINISHED', 'COMPLETED'].includes(unit?.status ?? '')
  const canCorrect = hasAnyRole(['operator', 'organizer', 'platform_admin', 'platformadmin'])
  const canJudge = hasAnyRole(['judge', 'operator', 'organizer', 'platform_admin', 'platformadmin'])
  const canEdit = canJudge && (!resultsPublished || canCorrect)
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
    setSavedAt('')
    setErrors({})
  }

  useEffect(() => {
    if (!token || !unitId) return
    let active = true
    Promise.resolve().then(() => {
      if (active) { setLoading(true); setError(''); setSaveError(''); setNotice(''); setSavedAt(''); setUnit(null); setContext({ name: '', number: 0, total: 0, sequence: 0, time: '', session: '', slug: '' }) }
      return getCompetitionUnitDetails(unitId, token)
    }).then(data => { if (active) applyUnit(data) })
      .catch(error => { if (active) setError(resultErrorMessage(error, 'Не удалось загрузить заплыв. Повторите попытку.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, unitId, reload])

  useEffect(() => {
    if (!token || !disciplineId || loadedUnitId !== unitId) return
    const resolvedDisciplineId = disciplineId
    let active = true
    async function findNext() {
      await Promise.resolve()
      if (!active) return
      setNextId(null); setNextError('')
      const local = await getCompetitionUnitsByDiscipline(resolvedDisciplineId, token!)
      const ordered = [...local].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))
      const currentSummary = local.find(u => u.id === loadedUnitId)
      if (active) setContext(previous => ({ ...previous, total: local.length, sequence: ordered.findIndex(u => u.id === loadedUnitId) + 1, time: formatDateTime(currentSummary?.scheduledAt) }))
      if (eventId) {
        const [disciplines, sessions, event] = await Promise.all([getEventDisciplines(eventId, token!), getSessions(eventId, token!).catch(() => []), getEvent(eventId, token!).catch(() => null)])
        const discipline = disciplines.find(d => d.id === resolvedDisciplineId)
        const publicEvent = event?.publicSlug ? await publicApi.event(event.publicSlug).catch(() => null) : null
        if (active) setContext(previous => ({ ...previous, name: discipline?.name ?? '', number: discipline?.number ?? disciplines.findIndex(d => d.id === resolvedDisciplineId) + 1, session: sessions.find(s => s.id === discipline?.sessionId)?.name ?? publicEvent?.sessions.find(s => s.id === discipline?.sessionId)?.name ?? '', time: formatDateTime(currentSummary?.scheduledAt, publicEvent?.venue?.timezone), slug: event?.publicSlug ?? '' }))
      }
      let next = local.filter(u => (u.sequenceNumber ?? 0) > (currentSummary?.sequenceNumber ?? Infinity))
        .sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))[0]
      if (eventId && currentSummary?.scheduledAt) {
        const disciplines = await getEventDisciplines(eventId, token!)
        const lists = await Promise.all(disciplines.map(d => d.id === resolvedDisciplineId ? Promise.resolve(local) : getCompetitionUnitsByDiscipline(d.id, token!)))
        next = lists.flat().filter(u => u.scheduledAt && Date.parse(u.scheduledAt) > Date.parse(currentSummary.scheduledAt!))
          .sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))[0]
      }
      if (active) setNextId(next?.id ?? null)
    }
    void findNext().catch(error => { if (active) setNextError(resultErrorMessage(error, 'Не удалось определить следующий заплыв.')) })
    return () => { active = false }
  }, [token, loadedUnitId, disciplineId, unitId, eventId])

  useEffect(() => {
    if (!mobile || !context.slug || !loadedUnitId) return
    let active = true
    // Cache the promise too: viewport changes and heat navigation share one request per slug.
    let request = clubCache.current.get(context.slug)
    if (!request) {
      request = publicApi.startList(context.slug)
      clubCache.current.set(context.slug, request)
    }
    void request.then(list => {
      if (!active) return
      const heat = list.sessions.flatMap(s => s.disciplines).flatMap(d => d.heats).find(h => h.id === loadedUnitId)
      setClubs(Object.fromEntries((heat?.lanes ?? []).flatMap(lane => lane.empty ? [] : [[lane.registrationId, lane.athlete.club]])))
    }).catch(() => { clubCache.current.delete(context.slug); if (active) setClubs({}) })
    return () => { active = false }
  }, [mobile, context.slug, loadedUnitId])

  function update(id: string, change: Partial<Draft>) {
    setDrafts(current => ({ ...current, [id]: { ...current[id], ...change } }))
    setErrors(current => ({ ...current, [id]: '' }))
    setDirty(true); setNotice(''); setSaveError('')
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
      setSaving(true); setSaveError('')
      try {
        const data = await saveHeatResults(unit.id, entries.map(e => ({
          competitionUnitEntryId: e.entryId,
          status: drafts[e.entryId].status,
          time: drafts[e.entryId].status === 'VALID' ? normalizeTime(drafts[e.entryId].time) : null,
        })), token)
        applyUnit(data)
        setSavedAt(new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' }).format(new Date()))
      } catch (cause) {
        setSaveError(resultErrorMessage(cause, 'Проверьте подключение и повторите попытку.'))
        toast({ text: 'Не удалось сохранить — повторить', tone: 'danger' })
      } finally { setSaving(false) }
    })
  }

  const saveStatus = saving ? 'Сохраняем…' : dirty ? 'Есть несохранённые изменения' : savedAt ? `Сохранено ${savedAt}` : ''
  const actionHint = busy ? 'Дождитесь завершения действия.' : dirty ? 'Сначала сохраните изменения.' : ''

  const controls = unit && !loading ? (!canJudge ? <Notice>Нет прав для ввода результатов.</Notice> : <JudgeActionBar mobile={mobile} status={saveStatus} feedback={saveError && <Notice tone="danger"><strong>Не удалось сохранить — повторить</strong><p>{saveError}</p><Button disabled={busy} title={busy ? 'Дождитесь завершения действия.' : undefined} onClick={save}>Повторить</Button></Notice>}>
        <button className={unit.status === 'IN_PROGRESS' && (!savedAt || dirty) ? 'fl-btn fl-btn-primary fl-btn-lg' : 'fl-btn fl-btn-ghost fl-btn-sm'} ref={saveButton} title={busy ? 'Дождитесь завершения действия.' : !canEdit ? 'Опубликованные результаты может исправить секретарь или организатор.' : !entries.length ? 'В заплыве нет участников.' : undefined} aria-busy={saving || undefined} disabled={busy || !canEdit || !entries.length} onClick={save}>{resultsPublished ? 'Сохранить исправление' : 'Сохранить и пересчитать'}</button>
        {(!resultsPublished || canCorrect) && <button className={unit.status === 'IN_PROGRESS' && savedAt && !dirty ? 'fl-btn fl-btn-primary fl-btn-lg' : 'fl-btn fl-btn-ghost fl-btn-sm'} title={actionHint || 'Публикация доступна после сохранения результатов всех дорожек.'} disabled={busy || dirty || (resultsPublished
          ? unit.status !== 'FINISHED'
          : !['PUBLISHED', 'IN_PROGRESS'].includes(unit.status) || !entries.length || entries.some(e => !e.rawValue && !e.display && !statusMap[e.resultStatus ?? '']))} onClick={() => setPublicationOpen(true)}>{resultsPublished ? 'Снять результаты с публикации' : 'Опубликовать результаты'}</button>}
        {(['start', 'finish'] as const).map(action => <button key={action} title={actionHint || (action === 'start' ? 'Старт доступен до начала заплыва.' : 'Финиш доступен после старта заплыва.')} className={action === 'start' && ['DRAFT', 'SCHEDULED', 'PUBLISHED'].includes(unit.status) ? 'fl-btn fl-btn-primary fl-btn-lg' : 'fl-btn fl-btn-ghost fl-btn-sm'} disabled={busy || dirty || !(action === 'start' ? ['DRAFT', 'SCHEDULED', 'PUBLISHED'] : ['IN_PROGRESS', 'FINISHED']).includes(unit.status)} onClick={() => void run(async () => {
          await changeHeatState(unit.id, action, token!)
          const data = await getCompetitionUnitDetails(unit.id, token!)
          applyUnit(data); setNotice(action === 'start' ? 'Заплыв начат.' : 'Заплыв завершён.')
        })}>{action === 'start' ? 'Старт' : 'Финиш'}</button>)}
        <button className={resultsPublished ? 'fl-btn fl-btn-primary fl-btn-lg' : 'fl-btn fl-btn-ghost fl-btn-sm'} title={actionHint || (nextError ? 'Не удалось определить следующий заплыв.' : !nextId ? 'Это последний заплыв' : undefined)} disabled={busy || dirty || !nextId} onClick={() => void run(async () => {
          let liveWarning: string | undefined
          if (eventId) {
            try { await setLiveHeat(eventId, nextId!, token!) } catch (error) {
              liveWarning = `Следующий заплыв открыт, но табло не обновлено. ${resultErrorMessage(error, 'Не удалось обновить live. Повторите обновление табло.')}`
            }
          }
          navigate(`/operator/competition-units/${nextId}${eventId ? `?eventId=${encodeURIComponent(eventId)}` : ''}`, { state: { liveWarning } })
        })}>Следующий заплыв →</button>
      </JudgeActionBar>) : null

  return <section className={styles.page}>
    <Link className={`${styles.backLink} fl-btn fl-btn-ghost fl-btn-sm`} to={eventId ? `/organizer/events/${eventId}/results` : "/operator/results"}>{eventId ? '← К соревнованию' : '← Судейство'}</Link>
    <PageHeader title={context.sequence ? `Заплыв ${context.sequence} из ${context.total}` : unit?.label ?? 'Заплыв'}
      badge={unit && <StatusBadge kind="heat" code={unit.status} />}
      meta={<><span className={styles.eyebrow}>Судейская панель{context.name && <> · <span>Вид № {context.number} · {context.name}</span></>}</span>{(context.time || context.session) && <span className={styles.context}>{[context.time, context.session].filter(Boolean).join(' · ')}</span>}</>}
      actions={controls} />
    <details className={styles.help}><summary className="fl-btn fl-btn-ghost fl-btn-sm">Как вводить</summary><p className={styles.description}>Введите цифры ммсссс или время 32.15. Enter / Tab — следующая дорожка, Esc — очистить. Допускается ч:мм:сс.сс и 32.1. Alt+D — <ResultStatus status="DNS" />, Alt+N — <ResultStatus status="DNF" />, Alt+Q — <ResultStatus status="DSQ" /> из поля времени. Пустые дорожки можно сохранить позже.</p></details>
    {loading && <div className={styles.loading} role="status" aria-label="Загружаем заплыв…"><span>Загружаем заплыв…</span>{Array.from({ length: 8 }, (_, index) => <div className={styles.skeleton} aria-hidden="true" key={index} />)}</div>}
    {error && <Notice tone="danger">{error} {!unit && <Button onClick={() => setReload(value => value + 1)}>Повторить</Button>}</Notice>}
    {navigationWarning && <Notice tone="warning">{navigationWarning}</Notice>}
    {unit && !loading && <>
      {notice && <p role="status">{notice}</p>}
      {nextError && <Notice tone="warning">{nextError}</Notice>}
      {publicationOpen && <ConfirmDialog title={resultsPublished ? 'Снять результаты с публикации?' : `Опубликовать результаты «${unit.label}»?`} description={resultsPublished ? 'Результаты перестанут быть видны зрителям.' : 'Места и времена появятся на публичной странице результатов как неофициальные.'} confirmLabel={resultsPublished ? 'Снять' : 'Опубликовать'} tone={resultsPublished ? 'danger' : 'primary'} busy={busy} error={error} onClose={() => setPublicationOpen(false)} onConfirm={() => void run(async () => {
        applyUnit(await changeCompetitionUnitPublication(unit.id, !resultsPublished, token!)); setPublicationOpen(false)
        setNotice(resultsPublished ? 'Публикация результатов снята.' : 'Результаты опубликованы.')
        toast({ text: resultsPublished ? 'Публикация снята' : 'Опубликовано', href: context.slug ? `/e/${context.slug}/results` : undefined })
      })} />}
      {entries.length > 0 && <div className={styles.tableFrame} data-scroll-edges={mobile ? '' : scrollEdges}><div ref={tableScroll} onScroll={measureTableScroll} className={mobile ? undefined : styles.tableWrap}><Results className={mobile ? styles.laneCards : `${styles.resultsTable} fl-table fl-judge-table`} aria-label="Дорожки заплыва">
        {!mobile && <><caption className="fl-sr-only">Дорожки заплыва</caption><thead><tr>{['Дорожка', 'Участник', 'Заявочное время', 'Время', 'Статус', 'Место', 'В группе'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead></>}
        <Rows>{entries.map((entry, index) => {
          const draft = drafts[entry.entryId] ?? { time: '', status: 'VALID' }
          return <Row className={mobile ? styles.laneCard : undefined} aria-label={mobile ? `Дорожка ${entry.laneOrPosition}` : undefined} key={entry.entryId}>
            <Cell className={styles.laneCell}>{mobile && 'Дорожка '}{entry.laneOrPosition ?? '—'}</Cell>
            <Cell><span className={styles.participantName}>{entry.participantName}</span>{mobile && clubs[entry.registrationId] && <span className={styles.clubName}>{clubs[entry.registrationId]}</span>}</Cell>{!mobile && <Cell className={styles.entryTime}>{entry.entryTime ?? '—'}</Cell>}
            <Cell className={styles.timeCell}>{mobile && <label htmlFor={`time-${entry.entryId}`}>Время</label>}<input id={`time-${entry.entryId}`} className={styles.resultInput} aria-label={`Время, дорожка ${entry.laneOrPosition}`} aria-invalid={Boolean(errors[entry.entryId])}
              aria-describedby={errors[entry.entryId] ? `error-${entry.entryId}` : undefined}
              ref={node => { inputs.current[entry.entryId] = node }} value={draft.time} inputMode="decimal" placeholder="00:00.00" title={!canEdit ? 'Нет прав для изменения опубликованных результатов.' : draft.status !== 'VALID' ? 'Снимите особый статус, чтобы ввести время.' : undefined}
              disabled={busy || !canEdit || draft.status !== 'VALID'} onChange={e => update(entry.entryId, { time: maskTime(e.target.value) })}
              onFocus={e => e.currentTarget.scrollIntoView({ block: 'nearest', inline: 'nearest' })}
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
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                  e.preventDefault(); validate(entry.entryId)
                  const candidates = e.key === 'ArrowUp' ? entries.slice(0, index).reverse() : entries.slice(index + 1)
                  const next = candidates.find(row => drafts[row.entryId]?.status === 'VALID')
                  if (next) inputs.current[next.entryId]?.focus()
                  return
                }
                if (e.key === 'Enter' || (e.key === 'Tab' && !e.shiftKey)) {
                  e.preventDefault(); validate(entry.entryId)
                  const next = entries.slice(index + 1).find(row => drafts[row.entryId]?.status === 'VALID')
                  if (next) inputs.current[next.entryId]?.focus(); else {
                    const menu = saveButton.current?.closest('details')
                    if (menu) menu.open = true
                    saveButton.current?.focus()
                  }
                }
              }} />
              {errors[entry.entryId] && <p id={`error-${entry.entryId}`} className={styles.fieldError} role="alert">{errors[entry.entryId]}</p>}
            </Cell>
            <Cell>{canJudge && <div className={styles.statusButtons}>{specialStatuses.map(status => <ResultStatus key={status} status={status} buttonProps={{
              className: `fl-btn fl-btn-sm ${status === 'DSQ' ? 'fl-btn-danger' : 'fl-btn-ghost'}`, type: 'button',
              title: busy ? 'Дождитесь завершения действия.' : !canEdit ? 'Нет прав для изменения опубликованных результатов.' : undefined,
              'data-entry-id': entry.entryId, 'aria-pressed': draft.status === status, disabled: busy || !canEdit,
              ref: node => { statusButtons.current[`${entry.entryId}-${status}`] = node },
              onClick: () => update(entry.entryId, { status: draft.status === status ? 'VALID' : status }),
            }} />)}</div>}{entry.resultStatus === 'CORRECTED' && <span><ResultStatus status="CORRECTED">исправлено</ResultStatus></span>}</Cell>
            {!mobile && <><Cell className={styles.placeCell}>{entry.finalPlace ?? '—'}</Cell><Cell className={styles.placeCell}>{entry.placeInAgeGroup ?? '—'}</Cell></>}
          </Row>
        })}</Rows>
      </Results></div></div>}
      <StatusLegend statuses={[...(canJudge && entries.length ? specialStatuses : []), ...entries.flatMap(entry => [drafts[entry.entryId]?.status, entry.resultStatus])]} />
      {!entries.length && <article aria-label="Пустой заплыв"><EmptyState title="В заплыве пока нет участников." action={<Button as={Link} to="/operator/results">К списку заплывов</Button>}><p>Выберите другой заплыв в списке соревнования.</p></EmptyState></article>}
    </>}
  </section>
}
