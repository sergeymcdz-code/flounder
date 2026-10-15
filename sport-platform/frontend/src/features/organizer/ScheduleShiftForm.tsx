import { useEffect, useState } from 'react'
import { shiftSchedule, type ScheduleShift } from '../../api/scheduling.api'
import { time } from '../public/format'
import type { Heat } from '../../api/organizer.types'
import styles from './OrganizerEventPage.module.css'

export function ScheduleShiftForm({ eventId, heats, accessToken, timeZone, disciplineNames = {}, busy, onApply, onClose }: {
  eventId: string
  heats: Heat[]
  accessToken: string
  timeZone?: string
  disciplineNames?: Record<string, string>
  busy: boolean
  onApply: (fromUnitId: string, deltaSec: number) => void
  onClose: () => void
}) {
  const candidates = heats.filter(h => h.scheduledAt && ['DRAFT', 'SCHEDULED', 'PUBLISHED'].includes(h.status))
    .sort((a, b) => Date.parse(a.scheduledAt!) - Date.parse(b.scheduledAt!))
  const [from, setFrom] = useState(candidates[0]?.id ?? '')
  const [minutes, setMinutes] = useState('20')
  const delta = Number(minutes) * 60
  const valid = from && Number.isInteger(delta) && delta !== 0 && Math.abs(delta) <= 86400
  const key = `${from}/${delta}`
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
  }, [eventId, from, delta, accessToken, key, valid])
  const current = valid && preview?.key === key ? preview : undefined
  const changes = current?.data?.units ?? []
  return <form className={styles.stack} onSubmit={e => { e.preventDefault(); if (changes.length) onApply(from, delta) }}>
    <label className="fl-field">С какого заплыва
      <select value={from} disabled={busy} onChange={e => setFrom(e.target.value)} required>
        {candidates.map(h => <option key={h.id} value={h.id}>{disciplineNames[h.eventDisciplineId]} · {h.label} · {time(h.scheduledAt, timeZone)}</option>)}
      </select>
    </label>
    <label className="fl-field">Сдвиг в минутах
      <input type="number" min={-1440} max={1440} step={1} required value={minutes} disabled={busy} onChange={e => setMinutes(e.target.value)} />
    </label>
    <p>Положительное число — позже, отрицательное — раньше. При перекрытии следующая сессия этого дня сдвигается на величину перекрытия.</p>
    {!valid && <p role="alert">Выберите заплыв и ненулевой сдвиг до 1440 минут.</p>}
    {valid && !current && <p role="status">Расчёт нового расписания…</p>}
    {current?.error && <p role="alert">{current.error}</p>}
    {changes.length > 0 && <div aria-live="polite">
      <p>Первый заплыв: {time(changes[0].previousScheduledAt, timeZone)} → {time(changes[0].scheduledAt, timeZone)}</p>
      <p>Последний заплыв: {time(changes.at(-1)!.previousScheduledAt, timeZone)} → {time(changes.at(-1)!.scheduledAt, timeZone)}</p>
      <p>Будет сдвинуто заплывов: {changes.length}.</p>
    </div>}
    <div className={styles.actions}>
      <button className="fl-btn fl-btn-secondary" type="button" disabled={busy} onClick={onClose}>Отмена</button>
      <button className="fl-btn fl-btn-primary" disabled={busy || !changes.length} type="submit">Подтвердить сдвиг</button>
    </div>
  </form>
}
