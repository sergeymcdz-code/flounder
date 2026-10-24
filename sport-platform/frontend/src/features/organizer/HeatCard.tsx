import { useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { StatusBadge } from '../../components/ui/StatusBadge'
import type { Heat, OrganizerRegistration, SwimDiscipline } from '../../api/organizer.types'
import { formatSeed } from './organizer-event-context'
import { time } from '../public/format'
import styles from './Heats.module.css'

export function HeatCard({ heat, discipline, registrations, lanes, timeZone, disabledReason, highlighted, onChangeLane }: {
  heat: Heat
  discipline: SwimDiscipline
  registrations: OrganizerRegistration[]
  lanes: number
  timeZone?: string
  disabledReason?: string
  highlighted: boolean
  onChangeLane: (entryId: string, lane: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [expanded, setExpanded] = useState(() => window.matchMedia('(min-width: 768px)').matches)
  useEffect(() => {
    const query = window.matchMedia('(min-width: 768px)')
    const update = () => setExpanded(query.matches)
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])
  const title = `Заплыв ${heat.sequenceNumber ?? heat.label}`
  const group = discipline.ageGroups?.find(g => g.code === heat.ageGroupCode)?.name ?? heat.ageGroupCode
  return <article id={`heat-${heat.id}`} className={`${styles.heat} fl-heat ${highlighted ? styles.highlight : ''}`} aria-label={title}>
    <header className={styles.heatHeader}>
      <div><h3 className={styles.heatNumber}>{title}</h3><p className={styles.numeric}>{time(heat.scheduledAt, timeZone)}</p></div>
      <StatusBadge kind="heat" code={heat.status} />
    </header>
    <p className={styles.muted}>{discipline.name}{group && ` · группа ${group}`}</p>
    {heat.entries.length === 0 ? <EmptyState title="В заплыве нет участников" action={null}>Пересоберите вид после проверки заявок.</EmptyState> : <details className={styles.lanes} open={expanded} onToggle={e => setExpanded(e.currentTarget.open)}>
      <summary>Дорожки · {heat.entries.length}</summary>
      <div className={styles.editAction}>
        <Button variant="ghost" size="sm" disabled={!!disabledReason} title={disabledReason} aria-pressed={editing} onClick={() => setEditing(!editing)}>{editing ? 'Завершить редактирование' : 'Редактировать дорожки'}</Button>
        {disabledReason && <small>{disabledReason}</small>}
      </div>
      <div className={`fl-table-scroll ${styles.tableScroll}`}>
        <table className={`fl-table fl-table-compact ${styles.laneTable}`}>
          <caption className="fl-sr-only">{title} — дорожки и заявочное время</caption>
          <thead><tr><th scope="col">Дор.</th><th scope="col">Участник</th><th scope="col" className={styles.extra} data-numeric>Год</th><th scope="col" className={styles.extra}>Клуб</th><th scope="col" data-numeric>Заяв. время</th></tr></thead>
          <tbody>{[...heat.entries].sort((a, b) => (a.laneOrPosition ?? 0) - (b.laneOrPosition ?? 0)).map(entry => {
            const registration = registrations.find(r => r.id === entry.registrationId)
            const year = registration?.participantSnapshot.birthDate?.slice(0, 4) ?? '—'
            const club = registration?.participantSnapshot.clubName ?? '—'
            return <tr key={entry.entryId}>
              <td className={styles.numeric}>
                <span className={editing ? styles.hidden : styles.readLane}>{entry.laneOrPosition ?? '—'}</span>
                <select className={editing ? styles.editLane : styles.desktopLane} aria-label={`Дорожка ${entry.participantName}`} value={entry.laneOrPosition ?? ''} disabled={!!disabledReason} title={disabledReason} onChange={e => onChangeLane(entry.entryId, Number(e.target.value))}>
                  <option value="" disabled>—</option>{Array.from({ length: lanes }, (_, i) => <option value={i + 1} key={i}>{i + 1}</option>)}
                </select>
              </td>
              <th scope="row">{entry.participantName}<span className={styles.mobileMeta}>{year} · {club}</span></th>
              <td className={styles.extra} data-numeric>{year}</td><td className={styles.extra}>{club}</td><td data-numeric>{formatSeed(registration?.seedTimeMs)}</td>
            </tr>
          })}</tbody>
        </table>
      </div>
    </details>}
  </article>
}
