import { plural } from '../../utils/plural'
import { ActionBar } from '../../components/ui/ActionBar'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { useCallback, useState } from 'react'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { publicApi } from '../../api/public.api'
import { usePublicData, LIVE_POLL_MS } from '../public/usePublicData'
import { ScheduleShiftForm } from './ScheduleShiftForm'
import {
  generateHeats,
  recalculateSchedule,
  publishStartList,
  validateHeats,
  changeLane,
  shiftSchedule,
} from '../../api/scheduling.api'
import type { Violation } from '../../api/organizer.types'
import { OrganizerConfirm, OrganizerDialog } from './OrganizerDialog'
import {
  formatDate,
  isHeatPublished,
  normalizeGeneration,
  formatSeed,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerHeatsPage() {
  const {
    event,
    heats,
    sessions,
    registrations,
    accessToken,
    busy, error, clearError,
    run,
    lastGeneration: last,
    saveGeneration,
  } = useOrganizerEvent()
  const toast = useToast()
  const [publishOpen, setPublishOpen] = useState(false)
  const [showShift, setShowShift] = useState(false)
  const hasPublicLive = ['PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'LIVE', 'COMPLETED'].includes(event.status)
  const loadLive = useCallback((signal: AbortSignal) => hasPublicLive ? publicApi.live(event.publicSlug, signal) : Promise.resolve(null), [event.publicSlug, hasPublicLive])
  const live = usePublicData(loadLive, LIVE_POLL_MS)
  const [confirm, setConfirm] = useState<{ disciplineId?: string } | null>(null)
  const [violations, setViolations] = useState<
    (Violation & { disciplineId: string })[] | null
  >(null)
  const [highlight, setHighlight] = useState('')
  async function generate(disciplineId?: string, force = false) {
    const ok = await run(async () => {
      if (disciplineId) {
        const result = await generateHeats(
          disciplineId,
          'event-disciplines',
          force,
          accessToken,
        )
        saveGeneration(
          [{ eventDisciplineId: disciplineId, result }],
          result.durationMs,
        )
      } else {
        const result = await generateHeats(
          event.id,
          'events',
          force,
          accessToken,
        )
        saveGeneration(
          normalizeGeneration(result),
          result.durationMs,
          result.violations,
        )
      }
      setViolations(null)
    })
    if (ok) setConfirm(null)
  }
  function requestGenerate(disciplineId?: string) {
    if (
      heats.some(
        (h) =>
          (!disciplineId || h.eventDisciplineId === disciplineId) &&
          isHeatPublished(h.status),
      )
    )
      setConfirm({ disciplineId })
    else void generate(disciplineId)
  }
  async function validate() {
    await run(async () => {
      const result = await Promise.all(
        event.disciplines.map(async (d) => ({
          id: d.id,
          ...(await validateHeats(d.id, accessToken)),
        })),
      )
      setViolations(
        result.flatMap((d) =>
          d.violations.map((v) => ({
            ...v,
            eventDisciplineId: v.eventDisciplineId ?? d.id,
            disciplineId: d.id,
          })),
        ),
      )
    }, 'none')
  }
  return (
    <div className={styles.stack}>
      <article className={styles.card}>
        {event.status === 'LIVE' && <p aria-live="polite">
          {live.data
            ? (live.data.delaySec ?? 0) > 0
              ? `Отставание: +${Math.ceil((live.data.delaySec ?? 0) / 60)} мин`
              : 'Идём по расписанию'
            : !hasPublicLive
              ? 'Отставание появится после публикации соревнования'
              : live.error
                ? 'Отставание временно недоступно'
                : 'Загрузка отставания…'}
        </p>}
        <ActionBar>
          <details><summary className="fl-btn fl-btn-primary">Сформировать заплывы ▾</summary>
            <button className="fl-btn fl-btn-secondary" disabled={busy || !event.disciplines.length || event.status === 'ARCHIVED'} onClick={() => requestGenerate()}>Всё соревнование</button>
            {event.disciplines.map(d => <button className="fl-btn fl-btn-secondary" key={d.id} disabled={busy || event.status === 'ARCHIVED'} onClick={() => requestGenerate(d.id)}>{d.name}</button>)}
          </details>
          <details><summary className="fl-btn fl-btn-secondary">Расписание ▾</summary>
            <button className="fl-btn fl-btn-secondary" disabled={busy || !heats.length || event.status === 'ARCHIVED'} onClick={() => void run(() => recalculateSchedule(event.id, accessToken))}>Пересчитать время стартов</button>
            <button className="fl-btn fl-btn-secondary" disabled={busy || !heats.some(h => h.scheduledAt && ['DRAFT', 'SCHEDULED', 'PUBLISHED'].includes(h.status)) || event.status === 'ARCHIVED'} onClick={() => setShowShift(true)}>Сдвинуть расписание…</button>
          </details>
          <button className="fl-btn fl-btn-secondary" disabled={busy || !heats.length || event.status === 'ARCHIVED'} onClick={() => { clearError(); setPublishOpen(true) }}>Опубликовать протокол</button>
          <details><summary className="fl-btn fl-btn-secondary">Печать ▾</summary>
            {heats.length > 0 && heats.every(heat => isHeatPublished(heat.status)) ? <a className="fl-btn fl-btn-secondary" href={`/e/${encodeURIComponent(event.publicSlug)}/start-list?print=1`} target="_blank" rel="noreferrer">Печатная форма</a> : <p>Публичная печатная форма появится после публикации. Черновик доступен в PDF.</p>}
            <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/start-list.pdf`} filename="Стартовый-протокол.pdf" accessToken={accessToken} disabled={busy || !heats.length} />
          </details>
        </ActionBar>
        {last && (
          <Notice><div aria-live="polite">
            <h2>Последнее формирование</h2>
            <p>
              за {(last.durationMs / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })} с · Исключено заявок:{' '}
              {last.excludedRegistrationIds.length}
            </p>
            {last.violations.map((v, i) => (
              <p
                className={
                  v.severity === 'ERROR'
                    ? styles.violationError
                    : styles.warning
                }
                key={i}
              >
                {v.severity === 'ERROR' ? 'Ошибка' : 'Предупреждение'}:{' '}
                {v.message}
              </p>
            ))}
          </div></Notice>
        )}
      </article>
      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Проверка</h2>
          <button
            className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'}
            onClick={() => {
              void validate()
            }}
          >
            Проверить заплывы
          </button>
        </div>
        {violations === null ? (
          <p className={styles.muted}>
            Запустите проверку перед публикацией протокола.
          </p>
        ) : violations.length === 0 ? (
          <p role="status">Нарушений не найдено.</p>
        ) : (
          <div className={styles.stack}>
            {violations.map((v, i) => (
              <div
                key={i}
                className={
                  v.severity === 'ERROR'
                    ? styles.violationError
                    : styles.warning
                }
              >
                <button
                  className="fl-btn fl-btn-secondary" onClick={() => {
                    const id =
                      heats.find(
                        (h) =>
                          h.eventDisciplineId ===
                            (v.eventDisciplineId ?? v.disciplineId) &&
                          h.sequenceNumber === v.heatNumber,
                      )?.id ??
                      v.unitId ??
                      v.competitionUnitId
                    if (id) {
                      setHighlight(id)
                      const target = document.getElementById(`heat-${id}`)
                      let parent = target?.closest('details')
                      while (parent) { parent.open = true; parent = parent.parentElement?.closest('details') ?? null }
                      target?.scrollIntoView({
                        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
                        block: 'center',
                      })
                    }
                  }}
                >
                  {v.severity === 'ERROR' ? 'Ошибка' : 'Предупреждение'}:{' '}
                  {v.message}
                </button>
              </div>
            ))}
          </div>
        )}
      </article>
      {[...sessions.map(session => ({ id: session.id, name: session.name, disciplines: event.disciplines.filter(d => d.sessionId === session.id) })), ...(event.disciplines.some(d => !d.sessionId || !sessions.some(s => s.id === d.sessionId)) ? [{ id: 'unassigned', name: 'Без сессии', disciplines: event.disciplines.filter(d => !d.sessionId || !sessions.some(s => s.id === d.sessionId)) }] : [])].map((group, groupIndex) => <details className="fl-group" key={group.id} open={groupIndex === 0}><summary>{group.name} · {heats.filter(h => group.disciplines.some(d => d.id === h.eventDisciplineId)).length} {plural(heats.filter(h => group.disciplines.some(d => d.id === h.eventDisciplineId)).length, ['заплыв', 'заплыва', 'заплывов'])}</summary><div>{group.disciplines.map((d, index) => (
        <div className="fl-discipline-group" key={d.id}>
        <button className="fl-btn fl-btn-secondary fl-group-action" aria-describedby={`discipline-summary-${d.id}`} disabled={busy || event.status === 'ARCHIVED'} onClick={() => requestGenerate(d.id)}>Сформировать по виду</button>
        <details className="fl-group" open={d.id === (group.disciplines.find(item => heats.some(h => h.eventDisciplineId === item.id && !isHeatPublished(h.status))) ?? group.disciplines[0])?.id}>
          <summary id={`discipline-summary-${d.id}`}>Вид № {d.number ?? index + 1} · {d.name} · {heats.filter(h => h.eventDisciplineId === d.id).length} {plural(heats.filter(h => h.eventDisciplineId === d.id).length, ['заплыв', 'заплыва', 'заплывов'])} <StatusBadge kind="heat" code={heats.some(h => h.eventDisciplineId === d.id && !isHeatPublished(h.status)) ? 'DRAFT' : heats.some(h => h.eventDisciplineId === d.id) ? 'PUBLISHED' : 'DRAFT'} /></summary><div className={styles.stack}>
          {heats
            .filter((h) => h.eventDisciplineId === d.id)
            .map((h) => (
              <article
                id={`heat-${h.id}`}
                className={`${styles.card} fl-heat ${highlight === h.id ? styles.highlight : ''}`}
                key={h.id}
              >
                <div className={styles.toolbar}>
                  <h2>
                    Заплыв {h.sequenceNumber ?? h.label} ·{' '}
                    {formatDate(h.scheduledAt, event.venue?.timezone)}
                    {h.ageGroupCode &&
                      ` · группа ${d.ageGroups?.find((g) => g.code === h.ageGroupCode)?.name ?? h.ageGroupCode}`}
                  </h2>
                  <StatusBadge kind="heat" code={h.status} />
                </div>
                <div className={`${styles.table} fl-table-scroll`}>
                  <table className="fl-table fl-table-compact">
                    <thead>
                      <tr>
                        <th>Дорожка</th>
                        <th>Фамилия Имя</th>
                        <th data-numeric>Год</th>
                        <th>Клуб</th>
                        <th data-numeric>Заявочное время</th>
                      </tr>
                    </thead>
                    <tbody>
                      {[...h.entries]
                        .sort(
                          (a, b) =>
                            (a.laneOrPosition ?? 0) - (b.laneOrPosition ?? 0),
                        )
                        .map((entry) => {
                          const r = registrations.find(
                            (r) => r.id === entry.registrationId,
                          )
                          return (
                            <tr key={entry.entryId}>
                              <td>
                                <select
                                  aria-label={`Дорожка ${entry.participantName}`}
                                  disabled={busy || event.status === 'ARCHIVED'}
                                  title={
                                    !event.venue?.lanes
                                      ? 'Число дорожек площадки не указано'
                                      : undefined
                                  }
                                  value={entry.laneOrPosition ?? ''}
                                  onChange={(e) => {
                                    void run(
                                      () =>
                                        changeLane(
                                          entry.entryId,
                                          Number(e.target.value),
                                          accessToken,
                                        ),
                                      { heatId: h.id },
                                    )
                                  }}
                                >
                                  <option value="" disabled>
                                    —
                                  </option>
                                  {Array.from(
                                    {
                                      length:
                                        event.venue?.lanes ??
                                        (Math.max(
                                          0,
                                          ...heats.flatMap((heat) =>
                                            heat.entries.map(
                                              (entry) =>
                                                entry.laneOrPosition ?? 0,
                                            ),
                                          ),
                                        ) ||
                                          8),
                                    },
                                    (_, i) => (
                                      <option value={i + 1} key={i}>
                                        {i + 1}
                                      </option>
                                    ),
                                  )}
                                </select>
                              </td>
                              <td>{entry.participantName}</td>
                              <td>
                                {r?.participantSnapshot.birthDate?.slice(
                                  0,
                                  4,
                                ) ?? '—'}
                              </td>
                              <td>{r?.participantSnapshot.clubName ?? '—'}</td>
                              <td data-numeric>{formatSeed(r?.seedTimeMs)}</td>
                            </tr>
                          )
                        })}
                    </tbody>
                  </table>
                </div>
              </article>
            ))}
          {!heats.some((h) => h.eventDisciplineId === d.id) && (
            <EmptyState title="Заплывы ещё не сформированы" action={<button className="fl-btn fl-btn-secondary" disabled={busy || event.status === 'ARCHIVED'} onClick={() => requestGenerate(d.id)}>Сформировать заплывы</button>} />
          )}
        </div></details></div>
      ))}</div></details>)}
      {publishOpen && <ConfirmDialog title="Опубликовать стартовый протокол?" description={`Публичная страница /e/${event.publicSlug}/start-list и PDF покажут ${heats.length} ${plural(heats.length, ['заплыв', 'заплыва', 'заплывов'])}. Повторный посев потребует подтверждения.`} confirmLabel="Опубликовать" busy={busy} error={error}
        onClose={() => setPublishOpen(false)} onConfirm={() => { void run(() => publishStartList(event.id, accessToken)).then(ok => { if (ok) { setPublishOpen(false); toast({ text: 'Опубликовано', href: `/e/${event.publicSlug}/start-list` }) } }) }} />}
      {showShift && <OrganizerDialog title="Сдвинуть расписание" onClose={() => setShowShift(false)}>
        <ScheduleShiftForm eventId={event.id} heats={heats} accessToken={accessToken} timeZone={event.venue?.timezone} disciplineNames={Object.fromEntries(event.disciplines.map(d => [d.id, d.name]))} busy={busy} archived={event.status === 'ARCHIVED'}
          onClose={() => setShowShift(false)} onApply={(fromUnitId, deltaSec) => {
            void run(() => shiftSchedule(event.id, fromUnitId, deltaSec, accessToken)).then(ok => { if (ok) setShowShift(false) })
          }} />
      </OrganizerDialog>}
      {confirm && (
        <OrganizerConfirm
          title="Переформировать опубликованные заплывы?"
          message="Опубликованные заплывы будут заменены. Подтвердите повторное формирование и затем снова опубликуйте протокол."
          busy={busy}
          onClose={() => setConfirm(null)}
          onConfirm={() => {
            void generate(confirm.disciplineId, true)
          }}
        />
      )}
    </div>
  )
}
