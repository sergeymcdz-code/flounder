import { useEffect, useState } from 'react'
import { shiftSchedule, type ScheduleShift } from '../../api/scheduling.api'
import { time } from '../public/format'
import type { Heat } from '../../api/organizer.types'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { Notice } from '../../components/ui/Notice'
import styles from './Heats.module.css'

export function ScheduleShiftForm({ eventId, heats, accessToken, timeZone, disciplineNames = {}, busy, archived = false, onApply, onClose }: {
  eventId: string
  heats: Heat[]
  accessToken: string
  timeZone?: string
  disciplineNames?: Record<string, string>
  archived?: boolean
  busy: boolean
  onApply: (fromUnitId: string, deltaSec: number) => void
  onClose: () => void
}) {
  const candidates = heats.filter(h => h.scheduledAt && ['DRAFT', 'SCHEDULED', 'PUBLISHED'].includes(h.status))
    .sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))
  const [from, setFrom] = useState(candidates[0]?.id ?? '')
  const [minutes, setMinutes] = useState('20')
  const delta = Number(minutes) * 60
  const valid = !archived && candidates.some(h => h.id === from) && Number.isInteger(Number(minutes)) && Number.isInteger(delta) && delta !== 0 && Math.abs(delta) <= 86400
  const key = `${eventId}/${from}/${delta}`
  const [retry, setRetry] = useState(0)
  const [preview, setPreview] = useState<{ key: string; data?: ScheduleShift; error?: string }>()
  useEffect(() => {
    if (!valid) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      void shiftSchedule(eventId, from, delta, accessToken, true, controller.signal)
        .then(data => { if (!controller.signal.aborted) setPreview({ key, data }) })
        .catch((error: unknown) => { if (!controller.signal.aborted) setPreview({ key, error: error instanceof Error ? error.message : 'Не удалось рассчитать сдвиг.' }) })
    }, 300)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [eventId, from, delta, accessToken, key, valid, retry])
  const current = valid && preview?.key === key ? preview : undefined
  const changes = current?.data?.units ?? []
  const reason = archived ? 'Архивное событие доступно только для чтения' : busy ? 'Дождитесь завершения текущего действия' : !valid ? 'Выберите заплыв и ненулевой целый сдвиг до 1440 минут' : !current ? 'Дождитесь расчёта нового расписания' : current.error ? 'Повторите расчёт перед подтверждением' : !changes.length ? 'Нет заплывов для сдвига расписания' : undefined
  return <form className={styles.form} onSubmit={e => { e.preventDefault(); if (!reason && changes.length) onApply(from, delta) }}>
    <label className="fl-field">С какого заплыва
      <select value={from} disabled={busy || archived} title={archived ? 'Архивное событие доступно только для чтения' : busy ? 'Дождитесь завершения текущего действия' : undefined} onChange={e => { setFrom(e.target.value); setPreview(undefined) }} required>
        {candidates.map(h => <option key={h.id} value={h.id}>{disciplineNames[h.eventDisciplineId]} · {h.label} · {time(h.scheduledAt, timeZone)}</option>)}
      </select>
    </label>
    <label className="fl-field">Сдвиг в минутах
      <input type="number" min={-1440} max={1440} step={1} required value={minutes} disabled={busy || archived} title={archived ? 'Архивное событие доступно только для чтения' : busy ? 'Дождитесь завершения текущего действия' : undefined} onChange={e => { setMinutes(e.target.value); setPreview(undefined) }} />
    </label>
    <p>Положительное число — позже, отрицательное — раньше. При перекрытии следующая сессия этого дня сдвигается на величину перекрытия.</p>
    {!valid && <Notice tone="warning">{reason}</Notice>}
    {valid && !current && <p role="status">Расчёт нового расписания…</p>}
    {current?.error && <Notice tone="danger"><p>{current.error}</p><Button onClick={() => { setPreview(undefined); setRetry(n => n + 1) }}>Повторить расчёт</Button></Notice>}
    {current?.data && !changes.length && <Notice>Нет заплывов для сдвига расписания. Выберите другой заплыв.</Notice>}
    {changes.length > 0 && <Notice tone="info" className={styles.preview}><div aria-live="polite">
      <p>Первый заплыв: {time(changes[0].previousScheduledAt, timeZone)} → {time(changes[0].scheduledAt, timeZone)}</p>
      <p>Последний заплыв: {time(changes.at(-1)!.previousScheduledAt, timeZone)} → {time(changes.at(-1)!.scheduledAt, timeZone)}</p>
      <p>Будет сдвинуто заплывов: {changes.length}.</p>
    </div></Notice>}
    <ActionBar>
      <Button title={busy ? 'Дождитесь завершения текущего действия' : undefined} disabled={busy} onClick={onClose}>Отмена</Button>
      <Button variant="primary" title={reason} disabled={!!reason} loading={busy} type="submit">Подтвердить сдвиг</Button>
    </ActionBar>
    {reason && <small className={styles.muted}>{reason}</small>}
  </form>
}
