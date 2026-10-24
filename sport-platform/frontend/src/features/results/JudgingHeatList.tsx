import { DataTable } from '../../components/ui/DataTable'
import { EmptyState } from '../../components/ui/EmptyState'
import styles from './CompetitionUnitsPage.module.css'
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
  return <div className={styles.heatList} aria-label="Заплывы по сессиям">
    {groups.map((group, index) => <details className={styles.group} key={group.id} open={index === 0}>
      <summary className="fl-btn fl-btn-ghost">{group.name}</summary>
      <div>{group.disciplines.map(d => {
        const heats = units.filter(u => u.eventDisciplineId === d.id)
        return <section key={d.id}>
          <h3>Вид № {d.number ?? d.orderInSession} · {d.name}</h3>
          {heats.length ? <DataTable cards caption={`${d.name} — заплывы`} rows={heats} rowKey={unit => unit.id} columns={[
            { key: 'number', header: '№', label: 'Заплыв', rowHeader: true, render: unit => unit.sequenceNumber ?? unit.label },
            { key: 'time', header: 'Время', numeric: true, render: unit => formatDateTime(unit.scheduledAt, timeZone) },
            { key: 'status', header: 'Статус', render: unit => <StatusBadge kind="heat" code={unit.status} /> },
            { key: 'actions', header: 'Действия', render: unit => <Link className="fl-btn fl-btn-ghost fl-btn-sm" to={`/operator/competition-units/${unit.id}?eventId=${eventId}`}>Открыть →</Link> },
          ]} /> : <EmptyState title="Заплывы пока не сформированы" action={<Link className="fl-btn fl-btn-ghost" to={`/organizer/events/${eventId}/results`}>К результатам соревнования</Link>}><p>Организатор готовит стартовый протокол.</p></EmptyState>}
        </section>
      })}</div>
    </details>)}
  </div>
}
