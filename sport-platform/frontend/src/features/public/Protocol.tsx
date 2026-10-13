import { useEffect, useRef } from 'react'
import { Link, useLocation } from 'react-router'
import type { Heat, PublicEvent, ResultRow } from './public.types'
import { date, isValidResult, seedTime, time } from './format'
import styles from './Public.module.css'

export function PrintHeader({
  event,
  title,
}: {
  event: PublicEvent
  title: string
}) {
  return (
    <div className={styles.printHeader}>
      <strong>
        {event.title} · {title}
      </strong>
      <div>
        {date(event.eventStartAt, event.venue?.timezone)} ·{' '}
        {event.venue
          ? `${event.venue.name}, ${event.venue.city}`
          : 'Площадка не указана'}
      </div>
    </div>
  )
}

export function PrintButton({ ready }: { ready: boolean }) {
  const { search } = useLocation()
  const printed = useRef(false)
  useEffect(() => {
    if (
      !ready ||
      printed.current ||
      new URLSearchParams(search).get('print') !== '1'
    )
      return
    let disposed = false
    let frame: number | undefined
    void document.fonts?.ready.then(() => {
      if (disposed) return
      frame = requestAnimationFrame(() => {
        if (!disposed) {
          printed.current = true
          window.print()
        }
      })
    })
    return () => {
      disposed = true
      if (frame) cancelAnimationFrame(frame)
    }
  }, [ready, search])
  return (
    <button
      className={`${styles.noPrint} fl-button-ghost`}
      onClick={() => window.print()}
      disabled={!ready}
    >
      Печать
    </button>
  )
}

export function LaneTable({ heat, lanes }: { heat: Heat; lanes: number }) {
  return (
    <table className={`${styles.table} fl-protocol fl-lanes`}>
      <caption className={styles.srOnly}>
        Дорожки заплыва №{heat.number}
      </caption>
      <thead>
        <tr>
          <th scope="col">
            <span className={styles.laneFull}>Дорожка</span>
            <abbr className={styles.laneShort} title="Дорожка">
              Дор.
            </abbr>
          </th>
          <th scope="col">Фамилия Имя</th>
          <th scope="col">Год</th>
          <th scope="col">Клуб</th>
          <th scope="col">Заявочное время</th>
        </tr>
      </thead>
      <tbody>
        {Array.from({ length: lanes }, (_, index) => {
          const lane = heat.lanes.find((item) => item.lane === index + 1)
          const entry = lane && !lane.empty ? lane : undefined
          const withdrawn = entry?.withdrawn === true
          return (
            <tr
              key={index}
              className={withdrawn ? styles.withdrawn : undefined}
            >
              <th scope="row">{index + 1}</th>
              <td>
                {entry ? (
                  <>
                    <span>
                      {entry.athlete.lastName} {entry.athlete.firstName}
                    </span>
                    {withdrawn && <small>Снят</small>}
                  </>
                ) : (
                  '—'
                )}
              </td>
              <td>{entry?.athlete.birthYear ?? '—'}</td>
              <td>{entry?.athlete.club ?? '—'}</td>
              <td>{entry && !withdrawn ? seedTime(entry.seedTimeMs) : '—'}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export function HeatProtocol({
  heat,
  lanes,
  slug,
  timeZone,
}: {
  heat: Heat
  lanes: number
  slug?: string
  timeZone?: string
}) {
  return (
    <article className={`${styles.heat} fl-heat`}>
      <h4>
        {slug ? (
          <Link
            to={`/e/${encodeURIComponent(slug)}/heats/${encodeURIComponent(heat.id)}`}
          >
            Заплыв {heat.number} — {time(heat.scheduledAt, timeZone)}
          </Link>
        ) : (
          <>
            Заплыв {heat.number} — {time(heat.scheduledAt, timeZone)}
          </>
        )}
      </h4>
      <LaneTable heat={heat} lanes={lanes} />
    </article>
  )
}

export function ResultsTable({
  rows,
  grouped = false,
}: {
  rows: ResultRow[]
  grouped?: boolean
}) {
  const sorted = [...rows].sort(
    (a, b) =>
      Number(!isValidResult(a.status)) - Number(!isValidResult(b.status)) ||
      (a.timeMs ?? Infinity) - (b.timeMs ?? Infinity) ||
      a.lastName.localeCompare(b.lastName, 'ru'),
  )
  return (
    <table className={`${styles.table} fl-protocol fl-results`}>
      <caption className={styles.srOnly}>
        Итоговый протокол{grouped ? ' по возрастной группе' : ''}
      </caption>
      <thead>
        <tr>
          <th scope="col">Место</th>
          <th scope="col">Фамилия Имя</th>
          <th scope="col">Год</th>
          <th scope="col">Клуб</th>
          <th scope="col">Результат</th>
        </tr>
      </thead>
      <tbody>
        {sorted.map((row, rowIndex) => (
          <tr
            key={`${row.lastName}-${row.firstName}-${row.birthYear}-${row.club}-${rowIndex}`}
          >
            <th scope="row">
              {isValidResult(row.status) ? (row.place ?? '—') : '—'}
            </th>
            <td>
              {row.lastName} {row.firstName}
            </td>
            <td>{row.birthYear}</td>
            <td>{row.club ?? '—'}</td>
            <td className={`fl-stopwatch ${row.status === 'DSQ' ? 'fl-result-dsq' : row.status === 'DNS' || row.status === 'DNF' ? 'fl-result-muted' : ''}`}>
              {isValidResult(row.status) ? (row.display ?? '—') : row.status}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
