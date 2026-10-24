import { resultStatusCode, resultStatusDictionary, type ResultStatusCode } from './results-ui'
import styles from './ResultStatus.module.css'

export function StatusLegend({ statuses }: { statuses: readonly (string | null | undefined)[] }) {
  const present = new Set(statuses.map(resultStatusCode))
  const codes = (Object.keys(resultStatusDictionary) as ResultStatusCode[]).filter(code => present.has(code))
  if (!codes.length) return null
  return <div className={`${styles.legend} fl-status-legend`} aria-label="Обозначения статусов результатов">
    {codes.map(code => `${code} — ${resultStatusDictionary[code].legend}`).join(' · ')}
  </div>
}
