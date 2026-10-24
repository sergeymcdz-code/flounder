import type { ReactNode } from 'react'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { time } from './format'
import type { UnitStatus } from './public.types'
import styles from './SessionTimeline.module.css'

type TimelineEntry = {
  id: string
  title: string
  detail?: ReactNode
  startsAt?: string | null
  status?: UnitStatus
}
export type TimelineSession = {
  id: string
  name: string
  startsAt?: string | null
  entries: TimelineEntry[]
}

export function SessionTimeline({ sessions, timezone, headingLevel = 3 }: {
  sessions: TimelineSession[]
  timezone?: string
  headingLevel?: 2 | 3
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  return <ol className={styles.timeline}>
    {sessions.map(session => <li key={session.id} className={styles.session}>
      <Heading className={styles.heading}>
        {session.startsAt !== undefined && <><span className={styles.time}>{time(session.startsAt, timezone)}</span><span aria-hidden="true"> · </span></>}{session.name}
      </Heading>
      {session.entries.length ? <ul className={styles.entries}>
        {session.entries.map(entry => <li key={entry.id} className={styles.entry} data-current={entry.status === 'IN_PROGRESS' || undefined}>
          <div className={styles.entryTitle}>
            <span className={entry.startsAt !== undefined ? styles.numeric : undefined}>{entry.startsAt !== undefined ? `${time(entry.startsAt, timezone)} · ` : ''}{entry.title}</span>
            {entry.status && <StatusBadge kind="heat" code={entry.status} />}
          </div>
          {entry.detail && <div className={styles.detail}>{entry.detail}</div>}
        </li>)}
      </ul> : <p className={styles.detail}>Виды программы пока не добавлены.</p>}
    </li>)}
  </ol>
}
