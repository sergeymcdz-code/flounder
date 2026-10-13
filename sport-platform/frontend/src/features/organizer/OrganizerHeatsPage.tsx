import { useState } from 'react'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import {
  generateHeats,
  recalculateSchedule,
  publishStartList,
  validateHeats,
  changeLane,
} from '../../api/scheduling.api'
import type { Violation } from '../../api/organizer.types'
import { OrganizerConfirm } from './OrganizerDialog'
import {
  formatDate,
  isHeatPublished,
  normalizeGeneration,
  formatSeed,
  statusLabels,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerHeatsPage() {
  const {
    event,
    heats,
    registrations,
    accessToken,
    busy,
    run,
    lastGeneration: last,
    saveGeneration,
  } = useOrganizerEvent()
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
        <label>
          <input type="checkbox" checked={false} disabled aria-describedby="circle-seed-help" />
          Круговой посев трёх сильнейших заплывов
        </label>
        <p id="circle-seed-help" className={styles.muted}>
          Круговой посев выключен. Настройка доступна только для просмотра.
        </p>
        <div className={styles.actions}>
          <button
            className={styles.primary}
            disabled={busy || !event.disciplines.length}
            onClick={() => requestGenerate()}
          >
            Сформировать заплывы (всё событие)
          </button>
          <button
            disabled={busy || !heats.length}
            onClick={() => {
              void run(() => recalculateSchedule(event.id, accessToken))
            }}
          >
            Пересчитать расписание
          </button>
          <button
            disabled={busy || !heats.length}
            onClick={() => {
              void run(() => publishStartList(event.id, accessToken))
            }}
          >
            Опубликовать стартовый протокол
          </button>
          <a
            className={styles.button}
            href={`/e/${encodeURIComponent(event.publicSlug)}/start-list?print=1`}
            target="_blank"
            rel="noreferrer"
          >
            Открыть печатную форму
          </a>
          <PdfDownloadButton
            path={`/api/v1/events/${event.id}/documents/start-list.pdf`}
            filename="Стартовый-протокол.pdf"
            accessToken={accessToken}
            disabled={busy || !heats.length}
          />
        </div>
        {last && (
          <div aria-live="polite">
            <h2>Последнее формирование</h2>
            <p>
              Длительность: {last.durationMs} мс · Исключено заявок:{' '}
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
          </div>
        )}
      </article>
      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Проверка</h2>
          <button
            disabled={busy}
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
                  onClick={() => {
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
                      document.getElementById(`heat-${id}`)?.scrollIntoView({
                        behavior: 'smooth',
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
      {event.disciplines.map((d, index) => (
        <div className={styles.stack} key={d.id}>
          <div className={styles.toolbar}>
            <h2>
              Вид № {d.number ?? index + 1}. {d.name}
            </h2>
            <button disabled={busy} onClick={() => requestGenerate(d.id)}>
              Сформировать по виду
            </button>
          </div>
          {heats
            .filter((h) => h.eventDisciplineId === d.id)
            .map((h) => (
              <article
                id={`heat-${h.id}`}
                className={`${styles.card} ${highlight === h.id ? styles.highlight : ''}`}
                key={h.id}
              >
                <div className={styles.toolbar}>
                  <h2>
                    Заплыв {h.sequenceNumber ?? h.label} ·{' '}
                    {formatDate(h.scheduledAt)}
                    {h.ageGroupCode &&
                      ` · группа ${d.ageGroups?.find((g) => g.code === h.ageGroupCode)?.name ?? h.ageGroupCode}`}
                  </h2>
                  <span className={styles.badge}>
                    {statusLabels[h.status] ?? h.status}
                  </span>
                </div>
                <div className={styles.table}>
                  <table>
                    <thead>
                      <tr>
                        <th>Дорожка</th>
                        <th>Фамилия Имя</th>
                        <th>Год</th>
                        <th>Клуб</th>
                        <th>Заявочное время</th>
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
                                  disabled={busy}
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
                              <td>{formatSeed(r?.seedTimeMs)}</td>
                            </tr>
                          )
                        })}
                    </tbody>
                  </table>
                </div>
              </article>
            ))}
          {!heats.some((h) => h.eventDisciplineId === d.id) && (
            <p className={styles.muted}>Заплывы ещё не сформированы.</p>
          )}
        </div>
      ))}
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
