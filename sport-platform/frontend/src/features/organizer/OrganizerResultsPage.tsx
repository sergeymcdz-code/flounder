import { Link } from 'react-router'
import { publishDisciplineResults } from '../../api/scheduling.api'
import {
  formatDate,
  statusLabels,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'
export function OrganizerResultsPage() {
  const { event, heats, accessToken, busy, run } = useOrganizerEvent()
  const hasResults = (id: string) =>
    heats
      .find((h) => h.id === id)
      ?.entries.some(
        (e) =>
          e.rawValue != null ||
          (e.resultStatus != null && e.resultStatus !== 'PENDING'),
      ) ?? false
  return (
    <div className={styles.stack}>
      <div className={styles.toolbar}>
        <h2>Результаты соревнования</h2>
        <a
          className={styles.button}
          href={`/e/${encodeURIComponent(event.publicSlug)}/results?print=1`}
          target="_blank"
          rel="noreferrer"
        >
          Печать итогового протокола
        </a>
      </div>
      {event.disciplines.map((d, index) => {
        const units = heats.filter((h) => h.eventDisciplineId === d.id)
        return (
          <article className={styles.card} key={d.id}>
            <div className={styles.toolbar}>
              <h2>
                Вид № {d.number ?? index + 1}. {d.name}
              </h2>
              <button
                disabled={
                  busy ||
                  !units.some(
                    (h) =>
                      ['PUBLISHED', 'IN_PROGRESS'].includes(h.status) &&
                      h.entries.length > 0 &&
                      h.entries.every(
                        (e) =>
                          e.rawValue != null ||
                          (e.resultStatus != null &&
                            e.resultStatus !== 'PENDING'),
                      ),
                  )
                }
                onClick={() => {
                  void run(() => publishDisciplineResults(units, accessToken))
                }}
              >
                Опубликовать результаты вида
              </button>
            </div>
            <div className={styles.table}>
              <table>
                <thead>
                  <tr>
                    <th>Заплыв</th>
                    <th>Время</th>
                    <th>Статус</th>
                    <th>Результаты</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {units.map((h) => (
                    <tr key={h.id}>
                      <td>{h.sequenceNumber ?? h.label}</td>
                      <td>{formatDate(h.scheduledAt)}</td>
                      <td>
                        <span className={styles.badge}>
                          {statusLabels[h.status] ?? h.status}
                        </span>
                      </td>
                      <td>
                        {['FINISHED', 'COMPLETED'].includes(h.status)
                          ? 'Опубликованы'
                          : hasResults(h.id) ? 'Внесены' : 'Нет'}
                      </td>
                      <td>
                        <Link
                          className={styles.link}
                          to={`/operator/competition-units/${h.id}`}
                        >
                          Ввести
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {units.length === 0 && (
              <p className={styles.muted}>Сначала сформируйте заплывы.</p>
            )}
          </article>
        )
      })}
    </div>
  )
}
