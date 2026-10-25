import { useCallback, useEffect } from 'react'
import { Link, useParams } from 'react-router'
import { getPublicDisciplineResults, type PublicDisciplineResultEntry } from '../../api/results.api'
import { LoadingState } from '../../components/LoadingState'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { statusTone } from '../../components/ui/status-dictionary'
import { usePublicEvent } from '../public/publicContext'
import { usePublicData } from '../public/usePublicData'
import { formatCount } from '../../utils/plural'
import { ResultStatus } from './ResultStatus'
import { StatusLegend } from './StatusLegend'
import { resultStatusCode } from './results-ui'
import styles from './PublicDisciplineResultsPage.module.css'

const participants = ['участник', 'участника', 'участников'] as const
const heats = ['заплыв', 'заплыва', 'заплывов'] as const

function resultStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта',
    VALID: 'Учтён',
    EXHIBITION: 'Вне зачёта',
  }
  return labels[status] ?? status
}

function displayResult(rawValue: string | null, status: string) {
  if (status === 'DID_NOT_START' || status === 'DID_NOT_FINISH' || status === 'DISQUALIFIED') return '—'
  return rawValue?.trim() || '—'
}

function sortEntries(entries: PublicDisciplineResultEntry[]) {
  return [...entries].sort((left, right) => (left.place ?? Number.MAX_SAFE_INTEGER) - (right.place ?? Number.MAX_SAFE_INTEGER))
}

function errorText(status: number | undefined) {
  if (status === 404) return 'Опубликованные результаты этого вида не найдены.'
  if (status) return `Сервер ответил ошибкой (${status}). Не удалось загрузить результаты.`
  return 'Нет связи. Не удалось загрузить результаты.'
}

export function PublicDisciplineResultsPage() {
  const event = usePublicEvent()
  const { disciplineId = '' } = useParams()
  const discipline = event.disciplines.find(item => item.id === disciplineId)
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  const load = useCallback(
    () => getPublicDisciplineResults(event.id, disciplineId),
    [event.id, disciplineId],
  )
  const state = usePublicData(load)
  const units = state.data?.units ?? []
  const entriesCount = units.reduce((total, unit) => total + unit.entries.length, 0)

  useEffect(() => {
    document.title = discipline
      ? `Результаты: ${discipline.name} · ${event.title} · SportGate`
      : `Вид программы не найден · ${event.title} · SportGate`
  }, [discipline, event.title])

  const back = <Button as={Link} variant="ghost" size="sm" className="fl-back-link" to={`${base}/results`}>← Все результаты</Button>
  if (!discipline) return <div className={styles.page}>
    {back}
    <EmptyState headingLevel={1} title="Вид программы не найден"
      action={<Button as={Link} to={`${base}/results`} variant="primary">Открыть результаты</Button>}>
      <p>Вид не опубликован или ссылка устарела. Выберите вид в общих результатах соревнования.</p>
    </EmptyState>
  </div>

  return (
    <div className={styles.page}>
      {back}
      <p className="fl-eyebrow">Результаты вида</p>
      <PageHeader title={`Вид № ${discipline.number}. ${discipline.name}`}
        meta={state.data && units.length > 0 ? `${formatCount(units.length, heats)} · ${formatCount(entriesCount, participants)}` : undefined} />

      {state.loading && !state.data && <LoadingState message="Загружаем результаты…" />}

      {state.error && !state.loading && <Notice tone="danger">
        <p>{errorText(state.errorStatus)}</p>
        <Button variant="secondary" onClick={state.retry}>Повторить</Button>
      </Notice>}

      {state.data && units.length === 0 && <EmptyState title="Результаты ещё не опубликованы"
        action={<Button as={Link} to={`${base}/live`} variant="primary">Открыть табло</Button>}>
        <p>После публикации здесь появятся места и время участников по заплывам.</p>
      </EmptyState>}

      {units.length > 0 && <div className={styles.units}>
        {units.map(unit => {
          const entries = sortEntries(unit.entries)
          const title = unit.label || `Заплыв ${unit.sequenceNumber ?? ''}`.trim()
          return <section className={styles.unit} key={unit.id} aria-labelledby={`unit-${unit.id}`}>
            <header className={styles.unitHeader}>
              <h2 id={`unit-${unit.id}`}>{title}</h2>
              <span className="fl-badge fl-badge-neutral">{formatCount(entries.length, participants)}</span>
            </header>
            {entries.length === 0 ? <p className={styles.muted}>В этом заплыве нет опубликованных результатов.</p> : <>
              <div className={`${styles.tableWrap} fl-data-table-scroll fl-scroll-x`} tabIndex={0} role="region" aria-label={`${title}: таблица результатов, прокручивается по горизонтали`}>
                <table className={`${styles.table} fl-table`}>
                  <caption className="fl-sr-only">{`${discipline.name} · ${title}`}</caption>
                  <thead>
                    <tr>
                      <th scope="col" data-numeric>Место</th>
                      <th scope="col">Участник</th>
                      <th scope="col">Клуб</th>
                      <th scope="col" data-numeric>Результат</th>
                      <th scope="col">Статус</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((entry, index) => <tr key={`${unit.id}-${entry.participantName}-${index}`}>
                      <td className={styles.place} data-numeric>
                        <span className={entry.place !== null && entry.place >= 1 && entry.place <= 3 ? styles.medal : undefined}>{entry.place ?? '—'}</span>
                      </td>
                      <td className={styles.athlete}>
                        <strong>{entry.participantName}</strong>
                        {entry.laneOrPosition !== null && entry.laneOrPosition > 0 && <small>Дорожка {entry.laneOrPosition}</small>}
                      </td>
                      <td>{entry.clubName ?? '—'}</td>
                      <td className={styles.result} data-numeric>{displayResult(entry.rawValue, entry.status)}</td>
                      <td>
                        <span className={`fl-badge fl-badge-${statusTone('result', entry.status)}`}>
                          <ResultStatus status={entry.status}>{resultStatusCode(entry.status) ? undefined : resultStatusLabel(entry.status)}</ResultStatus>
                        </span>
                      </td>
                    </tr>)}
                  </tbody>
                </table>
              </div>
              <StatusLegend statuses={entries.map(entry => entry.status)} />
            </>}
          </section>
        })}
      </div>}
    </div>
  )
}
