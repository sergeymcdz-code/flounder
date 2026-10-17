import { Link } from 'react-router'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../utils/formatDateTime'
import type { Session, SwimDiscipline } from '../../api/organizer.types'
import type { CompetitionUnitResponse } from '../../api/results.api'

type Props = {
  eventId: string
  sessions: Session[]
  disciplines: SwimDiscipline[]
  units: CompetitionUnitResponse[]
  timeZone?: string
}

export function JudgingHeatList({ eventId, sessions, disciplines, units, timeZone }: Props) {
  const unassigned = disciplines.filter(d => !d.sessionId || !sessions.some(s => s.id === d.sessionId))
  const groups = [
    ...sessions.map(session => ({ ...session, disciplines: disciplines.filter(d => d.sessionId === session.id) })),
    ...(unassigned.length ? [{ id: 'unassigned', name: 'Без сессии', disciplines: unassigned }] : []),
  ]
  return <div className="fl-judging-list" aria-label="Заплывы по сессиям">
    {groups.map((group, index) => <details className="fl-group" key={group.id} open={index === 0}>
      <summary>{group.name}</summary>
      <div>{group.disciplines.map(d => {
        const heats = units.filter(u => u.eventDisciplineId === d.id)
        return <section key={d.id}>
          <h3>Вид № {d.number ?? d.orderInSession} · {d.name}</h3>
          {heats.length ? <div className="fl-table-scroll">
            <table className="fl-table fl-table-compact">
              <thead><tr><th>№</th><th data-numeric>Время</th><th>Статус</th><th>Действия</th></tr></thead>
              <tbody>{heats.map(unit => <tr key={unit.id}>
                <td data-number>{unit.sequenceNumber ?? unit.label}</td>
                <td data-numeric>{formatDateTime(unit.scheduledAt, timeZone)}</td>
                <td><StatusBadge kind="heat" code={unit.status} /></td>
                <td><Link className="fl-btn fl-btn-ghost fl-btn-sm" to={`/operator/competition-units/${unit.id}?eventId=${eventId}`}>Открыть →</Link></td>
              </tr>)}</tbody>
            </table>
          </div> : <div className="fl-empty">
            Заплывы пока не сформированы. <Link className="fl-btn fl-btn-ghost" to={`/organizer/events/${eventId}/results`}>К результатам соревнования</Link>
          </div>}
        </section>
      })}</div>
    </details>)}
  </div>
}
