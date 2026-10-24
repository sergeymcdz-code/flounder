import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { HeatCardPrintOptions, HeatPrintControls } from './PrintFormsControls'
import { plural } from '../../utils/plural'
import { ActionBar } from '../../components/ui/ActionBar'
import { OverflowMenu } from '../../components/ui/OverflowMenu'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publicApi } from '../../api/public.api'
import { usePublicData, LIVE_POLL_MS } from '../public/usePublicData'
import { ScheduleShiftForm } from './ScheduleShiftForm'
import { generateHeats, recalculateSchedule, publishStartList, validateHeats, changeLane, shiftSchedule } from '../../api/scheduling.api'
import type { Violation } from '../../api/organizer.types'
import { OrganizerDialog } from './OrganizerDialog'
import { disabledReason, isHeatPublished, normalizeGeneration, useOrganizerEvent } from './organizer-event-context'
import { HeatCard } from './HeatCard'
import styles from './Heats.module.css'

export function OrganizerHeatsPage() {
  const { event, heats, sessions, registrations, accessToken, busy, error, clearError, run, lastGeneration: last, saveGeneration, refresh } = useOrganizerEvent()
  const toast = useToast()
  const [printOpen, setPrintOpen] = useState(false)
  const [publishOpen, setPublishOpen] = useState(false)
  const [showShift, setShowShift] = useState(false)
  const [showValidation, setShowValidation] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [confirm, setConfirm] = useState<{ disciplineId?: string } | null>(null)
  const [violations, setViolations] = useState<Violation[] | null>(null)
  const [highlight, setHighlight] = useState('')
  const hasPublicLive = ['PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'LIVE', 'COMPLETED'].includes(event.status)
  const loadLive = useCallback((signal: AbortSignal) => hasPublicLive ? publicApi.live(event.publicSlug, signal) : Promise.resolve(null), [event.publicSlug, hasPublicLive])
  const live = usePublicData(loadLive, LIVE_POLL_MS)
  const reason = disabledReason({ busy, archived: event.status === 'ARCHIVED' }) || undefined
  const noHeats = !heats.length ? 'Сначала сформируйте заплывы' : undefined
  const published = heats.length > 0 && heats.every(h => isHeatPublished(h.status))
  const issues = violations ?? last?.violations ?? []
  const blocking = issues.some(v => v.severity === 'ERROR')
  const publishReason = reason ?? noHeats ?? (blocking ? 'Исправьте ошибки проверки перед публикацией' : undefined)
  const generateReason = reason ?? (!event.disciplines.length ? 'Сначала добавьте виды программы' : undefined)
  const shiftReason = reason ?? (!heats.some(h => h.scheduledAt && ['DRAFT', 'SCHEDULED', 'PUBLISHED'].includes(h.status)) ? 'Нет заплывов с доступным временем старта для сдвига' : undefined)
  const lanes = event.venue?.lanes ?? (Math.max(0, ...heats.flatMap(h => h.entries.map(e => e.laneOrPosition ?? 0))) || 8)
  const groups = [...sessions.map(s => ({ id: s.id, name: s.name, disciplines: event.disciplines.filter(d => d.sessionId === s.id) })),
    { id: 'unassigned', name: 'Без сессии', disciplines: event.disciplines.filter(d => !sessions.some(s => s.id === d.sessionId)) }].filter(g => g.disciplines.length)

  async function generate(disciplineId?: string, force = false) {
    setGenerating(true)
    try {
      const ok = await run(async () => {
        if (disciplineId) {
          const result = await generateHeats(disciplineId, 'event-disciplines', force, accessToken)
          saveGeneration([{ eventDisciplineId: disciplineId, result }], result.durationMs)
        } else {
          const result = await generateHeats(event.id, 'events', force, accessToken)
          saveGeneration(normalizeGeneration(result), result.durationMs, result.violations)
        }
        setViolations(null)
      })
      if (ok) setConfirm(null)
    } finally { setGenerating(false) }
  }
  function requestGenerate(disciplineId?: string) {
    if (heats.some(h => (!disciplineId || h.eventDisciplineId === disciplineId) && isHeatPublished(h.status))) setConfirm({ disciplineId })
    else void generate(disciplineId)
  }
  async function validate(forPublication = false) {
    let checked: Violation[] = []
    const ok = await run(async () => {
      const results = await Promise.all(event.disciplines.map(async d => ({ id: d.id, ...await validateHeats(d.id, accessToken) })))
      checked = results.flatMap(d => d.violations.map(v => ({ ...v, eventDisciplineId: v.eventDisciplineId ?? d.id })))
      setViolations(checked)
    }, 'none')
    if (forPublication && ok && !checked.some(v => v.severity === 'ERROR')) setPublishOpen(true)
    else setShowValidation(true)
  }
  function locate(v: Violation) {
    const id = heats.find(h => h.eventDisciplineId === v.eventDisciplineId && h.sequenceNumber === v.heatNumber)?.id ?? v.unitId ?? v.competitionUnitId
    if (!id) return
    setShowValidation(false)
    setHighlight(id)
    requestAnimationFrame(() => {
      const target = document.getElementById(`heat-${id}`)
      let parent = target?.closest('details')
      while (parent) { parent.open = true; parent = parent.parentElement?.closest('details') ?? null }
      target?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' })
    })
  }
  return <div className={styles.page}>
    <ActionBar className={styles.actions}>
      {!published && <div className={styles.action}><Button variant="primary" disabled={!!(heats.length ? publishReason : generateReason)} title={heats.length ? publishReason : generateReason} loading={busy} loadingLabel={heats.length ? 'Проверяем…' : 'Формируем…'} onClick={() => heats.length ? void validate(true) : requestGenerate()}>{heats.length ? 'Опубликовать протокол' : 'Сформировать заплывы'}</Button>{(heats.length ? publishReason : generateReason) && <small>{heats.length ? publishReason : generateReason}</small>}</div>}
      {heats.length > 0 && <Button variant={published ? 'primary' : 'secondary'} disabled={!!generateReason} title={generateReason} onClick={() => requestGenerate()}>Сформировать заплывы</Button>}
      <OverflowMenu label="По виду" trigger="По виду ▾" items={event.disciplines.map(d => ({ label: d.name, disabled: !!reason, disabledReason: reason, onSelect: () => requestGenerate(d.id) }))} />
      <OverflowMenu label="Расписание" trigger="Расписание ▾" items={[
        { label: 'Пересчитать время стартов', disabled: !!(reason ?? noHeats), disabledReason: reason ?? noHeats, onSelect: () => void run(() => recalculateSchedule(event.id, accessToken)) },
        { label: 'Сдвинуть расписание…', disabled: !!shiftReason, disabledReason: shiftReason, onSelect: () => { clearError(); setShowShift(true) } },
      ]} />
      <OverflowMenu label="Печать" trigger="Печать ▾" items={[
        ...(published ? [{ label: 'Печатная форма', to: `/e/${encodeURIComponent(event.publicSlug)}/start-list?print=1`, target: '_blank' as const }] : []),
        { label: 'Протокол и карточки (PDF)', onSelect: () => setPrintOpen(true) },
      ]} />
      <div className={styles.action}><Button aria-label="Проверить заплывы" variant="ghost" disabled={!!(reason ?? noHeats)} title={reason ?? noHeats} onClick={() => { clearError(); setShowValidation(true); void validate() }}>Проверка</Button>{(reason ?? noHeats) && <small>{reason ?? noHeats}</small>}</div>
    </ActionBar>
    <Notice tone={blocking ? 'danger' : published ? 'info' : 'neutral'} className={styles.notice}>
      <div>Стартовый протокол · <StatusBadge kind="heat" code={published ? 'PUBLISHED' : 'DRAFT'} /></div>
      <p>{blocking ? 'Публикация заблокирована: исправьте ошибки проверки.' : published ? 'Протокол доступен на публичной странице соревнования.' : 'Проверьте заплывы перед публикацией стартового протокола.'}</p>
    </Notice>
    {generating && <Notice tone="info"><p role="status">Формируем заплывы…</p></Notice>}
    {error && <Notice className={styles.notice}><p>Показаны последние загруженные данные. Обновите их и повторите действие.</p><Button disabled={busy} title={busy ? reason : undefined} onClick={() => void run(refresh)}>Повторить</Button></Notice>}
    {last && <Notice tone={last.violations.some(v => v.severity === 'ERROR') ? 'danger' : last.violations.length ? 'warning' : 'info'} className={styles.notice}>
      <h2 className={styles.noticeTitle}>Последнее формирование</h2><p role="status">Сформировано за {(last.durationMs / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} с · Исключено заявок: {last.excludedRegistrationIds.length}</p>
      {last.violations.length > 0 && <details><summary>Замечания формирования · {last.violations.length}</summary>{last.violations.map((v, i) => <p key={i}>{v.severity === 'ERROR' ? 'Ошибка' : 'Предупреждение'}: {v.message}</p>)}</details>}
    </Notice>}
    {event.status === 'LIVE' && <Notice><p role="status">{live.data ? (live.data.delaySec ?? 0) > 0 ? `Отставание: +${Math.ceil((live.data.delaySec ?? 0) / 60)} мин` : 'Идём по расписанию' : live.error ? 'Отставание временно недоступно' : 'Загрузка отставания…'}</p></Notice>}
    {!event.disciplines.length ? <EmptyState title="Сначала добавьте виды программы" action={<Button as={Link} to={`/organizer/events/${event.id}/program`}>Открыть программу</Button>}>После этого можно сформировать заплывы.</EmptyState> : !heats.length && <EmptyState title="Заплывы ещё не сформированы" action={null}>Нажмите «Сформировать заплывы», чтобы распределить участников по дорожкам.</EmptyState>}
    {groups.map(group => <details key={group.id} className={`fl-group ${styles.session}`} open>
      <summary>{group.name} · {heats.filter(h => group.disciplines.some(d => d.id === h.eventDisciplineId)).length} {plural(heats.filter(h => group.disciplines.some(d => d.id === h.eventDisciplineId)).length, ['заплыв', 'заплыва', 'заплывов'])}</summary>
      <div className={styles.stack}>{group.disciplines.map((d, index) => {
        const units = heats.filter(h => h.eventDisciplineId === d.id)
        return <section key={d.id} className={styles.discipline} aria-label={d.name}>
          <header className={styles.disciplineHeader}><div><h2>Вид № {d.number ?? index + 1} · {d.name}</h2><span className={styles.numeric}>{units.length} {plural(units.length, ['заплыв', 'заплыва', 'заплывов'])}</span> · <StatusBadge kind="heat" code={units.length && units.every(h => isHeatPublished(h.status)) ? 'PUBLISHED' : 'DRAFT'} /></div>
            <OverflowMenu label={`Действия вида ${d.name}`} items={[{ label: 'Пересобрать', disabled: !!reason, disabledReason: reason, onSelect: () => requestGenerate(d.id) }]} />
          </header>
          <div className={styles.grid}>{units.map(h => <HeatCard key={h.id} heat={h} discipline={d} registrations={registrations} lanes={lanes} timeZone={event.venue?.timezone} disabledReason={reason} laneHint={!event.venue?.lanes ? 'Число дорожек площадки не указано' : undefined} highlighted={highlight === h.id} onChangeLane={(id, lane) => { void run(async () => { await changeLane(id, lane, accessToken); setViolations(null) }, { heatId: h.id }) }} />)}</div>
          {!units.length && <p className={styles.muted}>Заплывы этого вида ещё не сформированы. Используйте меню «⋯».</p>}
        </section>
      })}</div>
    </details>)}
    {printOpen && <OrganizerDialog title="Печать заплывов" onClose={() => setPrintOpen(false)}><div className={styles.stack}>
      {!published && <p>Публичная печатная форма появится после публикации. Черновик доступен в PDF.</p>}
      <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/start-list.pdf`} filename="Стартовый-протокол.pdf" accessToken={accessToken} disabled={busy || !heats.length} />
      {(busy || !heats.length) && <small>{busy ? reason : noHeats}</small>}
      <HeatCardPrintOptions /><HeatPrintControls />
    </div></OrganizerDialog>}
    {showValidation && <OrganizerDialog title="Проверка заплывов" onClose={() => setShowValidation(false)}><div className={styles.stack}>
      {busy ? <p role="status">Проверяем заплывы…</p> : violations === null ? <Notice>Проверка не завершена. Повторите попытку.</Notice> : <Notice tone={blocking ? 'danger' : violations.length ? 'warning' : 'info'} className={styles.notice}>
        <p role="status">{!violations.length ? 'Нарушений не найдено.' : blocking ? 'Ошибки блокируют публикацию.' : 'Предупреждения не блокируют публикацию.'}</p>
        {violations.map((v, i) => <div key={i}><p>{v.severity === 'ERROR' ? 'Ошибка' : 'Предупреждение'}: {v.message}</p>{(v.heatNumber != null || v.unitId || v.competitionUnitId) && <Button onClick={() => locate(v)}>{v.severity === 'ERROR' ? 'Ошибка' : 'Предупреждение'}: {v.message}</Button>}</div>)}
      </Notice>}
      <Button disabled={!!reason} title={reason} onClick={() => void validate()}>Повторить проверку</Button>{reason && <small>{reason}</small>}
    </div></OrganizerDialog>}
    {publishOpen && <ConfirmDialog title="Опубликовать стартовый протокол?" description={`Публичная страница /e/${event.publicSlug}/start-list и PDF покажут ${heats.length} ${plural(heats.length, ['заплыв', 'заплыва', 'заплывов'])}. Повторный посев потребует подтверждения.`} confirmLabel="Опубликовать" busy={busy} error={error} onClose={() => setPublishOpen(false)} onConfirm={() => {
      if (publishReason) return
      void run(() => publishStartList(event.id, accessToken)).then(ok => { if (ok) { setPublishOpen(false); toast({ text: 'Опубликовано', href: `/e/${event.publicSlug}/start-list` }) } })
    }} />}
    {showShift && <OrganizerDialog title="Сдвинуть расписание" onClose={() => setShowShift(false)}><ScheduleShiftForm eventId={event.id} heats={heats} accessToken={accessToken} timeZone={event.venue?.timezone} disciplineNames={Object.fromEntries(event.disciplines.map(d => [d.id, d.name]))} busy={busy} archived={event.status === 'ARCHIVED'} onClose={() => setShowShift(false)} onApply={(fromUnitId, deltaSec) => { void run(() => shiftSchedule(event.id, fromUnitId, deltaSec, accessToken)).then(ok => { if (ok) setShowShift(false) }) }} /></OrganizerDialog>}
    {confirm && <ConfirmDialog title="Переформировать опубликованные заплывы?" description="Опубликованные заплывы будут заменены. Подтвердите повторное формирование и затем снова опубликуйте протокол." confirmLabel="Подтвердить" tone="danger" busy={busy} error={error} onClose={() => setConfirm(null)} onConfirm={() => { if (!reason) void generate(confirm.disciplineId, true) }} />}
  </div>
}
