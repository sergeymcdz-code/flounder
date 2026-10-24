import { StatusBadge } from '../../components/ui/StatusBadge'
import { ResultStatus } from '../results/ResultStatus'
import { StatusLegend } from '../results/StatusLegend'
import { resultStatusCode } from '../results/results-ui'
import { useSyncExternalStore } from 'react'
import { isValidResult, seedTime } from './format'
import type { ResultRow } from './public.types'
import styles from './ResultsPage.module.css'

const fullTableQuery = 'print, (min-width: 768px)'
function subscribeLayout(onChange: () => void) {
  if (!window.matchMedia) return () => {}
  const media = window.matchMedia(fullTableQuery)
  media.addEventListener('change', onChange)
  return () => media.removeEventListener('change', onChange)
}
function fullTableSnapshot() {
  return window.matchMedia ? window.matchMedia(fullTableQuery).matches : true
}

function Surname({ name, query }: { name: string; query: string }) {
  const index = query ? name.toLocaleLowerCase('ru').indexOf(query) : -1
  return index < 0 ? name : <>{name.slice(0, index)}<mark>{name.slice(index, index + query.length)}</mark>{name.slice(index + query.length)}</>
}

function RowStatus({ status }: { status: ResultRow['status'] }) {
  const code = resultStatusCode(status)
  if (!code) return null
  return <ResultStatus status={code}>
    {code === 'NT' ? code : <StatusBadge kind="result" code={code} />}
  </ResultStatus>
}

export function ResultsTable({ rows, caption, query = '' }: {
  rows: ResultRow[]; caption: string; query?: string
}) {
  const fullTable = useSyncExternalStore(subscribeLayout, fullTableSnapshot, () => true)
  // Preserve the existing protocol order and the places supplied by the API.
  const sorted = [...rows].sort((a, b) =>
    Number(!isValidResult(a.status)) - Number(!isValidResult(b.status)) ||
    (a.timeMs ?? Infinity) - (b.timeMs ?? Infinity) || a.lastName.localeCompare(b.lastName, 'ru'),
  )
  return <div className={`${styles.tableWrap} fl-data-table-scroll`}>
    <table className={`${styles.table} fl-table fl-protocol fl-results`}>
      <caption className="fl-sr-only">{caption}</caption>
      <colgroup><col className={styles.placeColumn} /><col />
        {fullTable && <><col className={styles.yearColumn} /><col className={styles.clubColumn} /></>}
        <col className={styles.timeColumn} />{fullTable && <col className={styles.statusColumn} />}</colgroup>
      <thead><tr>
        <th scope="col" data-numeric>Место</th>
        <th scope="col">Участник</th>
        {fullTable && <><th scope="col" className={styles.year} data-numeric>Год</th>
          <th scope="col" className={styles.club}>Клуб</th></>}
        <th scope="col" data-numeric>Время</th>
        {fullTable && <th scope="col" className={styles.status}>Статус</th>}
      </tr></thead>
      <tbody>{sorted.map((row, index) => {
        const valid = isValidResult(row.status)
        const hasTime = valid || row.status === 'CORRECTED'
        const display = hasTime ? (row.display ?? (row.timeMs === null ? '—' : seedTime(row.timeMs))) : '—'
        const medal = valid && row.place !== null && row.place >= 1 && row.place <= 3
        return <tr key={`${row.lastName}-${row.firstName}-${row.birthYear}-${row.club}-${index}`}>
          <th scope="row" className={styles.place} data-numeric>
            <span className={medal ? styles.medal : undefined}>{valid ? row.place ?? '—' : '—'}</span>
          </th>
          <td className={styles.athlete}>
            <span><Surname name={row.lastName} query={query} /> {row.firstName}</span>
            {!fullTable && <small>{row.birthYear} · {row.club ?? 'Клуб не указан'}</small>}
          </td>
          {fullTable && <><td className={styles.year} data-numeric>{row.birthYear}</td>
            <td className={styles.club}>{row.club ?? '—'}</td></>}
          <td className={styles.result} data-numeric>
            {hasTime && <span>{display}</span>}
            {!hasTime && fullTable && <span>—</span>}
            {!fullTable && <span><RowStatus status={row.status} /></span>}
          </td>
          {fullTable && <td className={styles.status}><RowStatus status={row.status} /></td>}
        </tr>
      })}</tbody>
    </table>
    <StatusLegend statuses={rows.map(row => row.status)} />
  </div>
}
