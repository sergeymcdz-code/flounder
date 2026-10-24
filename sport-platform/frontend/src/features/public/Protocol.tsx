import { ResultStatus } from '../results/ResultStatus'
import { StatusLegend } from '../results/StatusLegend'
import { useEffect, useRef } from 'react'
import { Link, useLocation } from 'react-router'
import { Button } from '../../components/ui/Button'
import { Notice } from '../../components/ui/Notice'
import type { Heat, PublicEvent, ResultRow } from './public.types'
import { date, isValidResult, seedTime, time } from './format'
import styles from './Public.module.css'
import startStyles from './StartList.module.css'

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
    <Button
      variant="ghost"
      className={styles.noPrint}
      onClick={() => window.print()}
      disabled={!ready}
      title={!ready ? 'Печать доступна после публикации протокола' : undefined}
    >
      Печать
    </Button>
  )
}

export function ProtocolState({ loading, error, errorStatus, data, retry }: {
  loading: boolean
  error?: string
  errorStatus?: number
  data?: unknown
  retry: () => void
}) {
  if (loading && data === undefined) return <div className={styles.noPrint} role="status">
    <p>Загружаем стартовый протокол…</p>
    <div aria-hidden="true" className={startStyles.skeletons}>
      {[0, 1].map(heat => <div key={heat} className={startStyles.skeletonHeat}>
        {[0, 1, 2].map(row => <div key={row} className={startStyles.skeletonRow} />)}
      </div>)}
    </div>
  </div>
  if (!error) return null
  const message = errorStatus === 404 ? 'Данные не найдены.'
    : errorStatus ? `Сервер ответил ошибкой (${errorStatus}). ${error}` : error
  return <Notice tone="danger" className={`${styles.noPrint} ${startStyles.error}`}>
    <p>{message}{data !== undefined && ' Показаны последние данные.'}</p>
    <p>Попробуйте загрузить протокол ещё раз.</p>
    <Button variant="secondary" onClick={retry}>Повторить</Button>
  </Notice>
}

export function LaneTable({ heat, lanes, compact = false }: { heat: Heat; lanes: number; compact?: boolean }) {
  const visibleLanes = Array.from({ length: lanes }, (_, index) => {
    const lane = heat.lanes.find(item => item.lane === index + 1)
    return lane && !lane.empty ? lane : undefined
  })
  return (
    <>
    <div className={compact ? `${startStyles.tableScroll} fl-table-scroll` : undefined}>
    <table className={`${styles.table} fl-protocol fl-lanes ${compact ? `${startStyles.laneTable} fl-table` : ''}`} role="table" tabIndex={compact ? undefined : 0}>
      <caption className={styles.srOnly}>
        Дорожки заплыва №{heat.number}
      </caption>
      <thead>
        <tr>
          <th scope="col">
            <span className={`${styles.laneFull} ${compact ? startStyles.laneFull : ''}`}>Дорожка</span>
            <abbr className={`${styles.laneShort} ${compact ? startStyles.laneShort : ''}`} title="Дорожка">
              Дор.
            </abbr>
          </th>
          <th scope="col">Фамилия Имя</th>
          <th scope="col" className={compact ? startStyles.secondaryColumn : undefined}>Год</th>
          <th scope="col" className={compact ? startStyles.secondaryColumn : undefined}>Клуб</th>
          <th scope="col">
            <span className={compact ? startStyles.seedFull : undefined}>Заявочное время</span>
            {compact && <span className={`${startStyles.seedShort} ${styles.noPrint}`}>Заяв. время</span>}
          </th>
        </tr>
      </thead>
      <tbody>
        {visibleLanes.map((entry, index) => {
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
                    {compact && <small className={`${startStyles.athleteMeta} ${styles.noPrint}`} aria-hidden="true">
                      {entry.athlete.birthYear} · {entry.athlete.club ?? 'Без клуба'}
                    </small>}
                    {withdrawn && <small>Снят</small>}
                  </>
                ) : (
                  '—'
                )}
              </td>
              <td className={compact ? startStyles.secondaryColumn : undefined}>{entry?.athlete.birthYear ?? '—'}</td>
              <td className={compact ? startStyles.secondaryColumn : undefined}>{entry?.athlete.club ?? '—'}</td>
              <td>{entry && !withdrawn ? <ResultStatus status={seedTime(entry.seedTimeMs)} /> : '—'}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
    </div>
    <StatusLegend statuses={visibleLanes.map(entry => entry && !entry.withdrawn && entry.seedTimeMs === null ? 'NT' : null)} />
    </>
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
    <article className={`${styles.heat} ${startStyles.heat} fl-heat`}>
      <div className={`${startStyles.heatHeading} ${styles.noPrint}`}>
        <h4>Заплыв {heat.number} · {time(heat.scheduledAt, timeZone)}</h4>
        {slug && <Button as={Link} variant="ghost" size="sm"
          aria-label={`Открыть заплыв ${heat.number}`}
          to={`/e/${encodeURIComponent(slug)}/heats/${encodeURIComponent(heat.id)}`}>Открыть</Button>}
      </div>
      <h4 className={startStyles.printOnly} aria-hidden="true">
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
      <LaneTable heat={heat} lanes={lanes} compact />
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
    <>
    <table className={`${styles.table} fl-protocol fl-results`} role="table" tabIndex={0}>
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
              {isValidResult(row.status) ? (row.display ?? '—') : row.status === 'CORRECTED' ? <>{row.display ?? '—'} <ResultStatus status={row.status} /></> : <ResultStatus status={row.status} />}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
    <StatusLegend statuses={rows.map(row => row.status)} />
    </>
  )
}
