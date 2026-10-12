import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getPublicEventDetails } from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  getPublicDisciplineResults,
  type PublicDisciplineResultEntry,
  type PublicDisciplineResultsResponse,
} from '../../api/results.api'
import styles from './PublicDisciplineResultsPage.module.css'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 404) {
    return 'Событие, дисциплина или опубликованные результаты не найдены.'
  }

  if (error instanceof ApiError) {
    return error.message
  }

  return 'Не удалось загрузить результаты.'
}

function resultStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта',
    VALID: 'Учтён',
    DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал',
    DISQUALIFIED: 'Дисквалифицирован',
    CORRECTED: 'Исправлен',
    EXHIBITION: 'Вне зачёта',
  }

  return labels[status] ?? status
}

function resultStatusClassName(status: string) {
  if (status === 'VALID') {
    return `${styles.statusBadge} ${styles.statusValid}`
  }

  if (
    status === 'DID_NOT_START' ||
    status === 'DID_NOT_FINISH'
  ) {
    return `${styles.statusBadge} ${styles.statusWarning}`
  }

  if (status === 'DISQUALIFIED') {
    return `${styles.statusBadge} ${styles.statusDanger}`
  }

  if (status === 'CORRECTED') {
    return `${styles.statusBadge} ${styles.statusCorrected}`
  }

  if (status === 'EXHIBITION') {
    return `${styles.statusBadge} ${styles.statusNeutral}`
  }

  return `${styles.statusBadge} ${styles.statusPending}`
}

function placeClassName(place: number | null) {
  if (place === 1) {
    return `${styles.place} ${styles.placeGold}`
  }

  if (place === 2) {
    return `${styles.place} ${styles.placeSilver}`
  }

  if (place === 3) {
    return `${styles.place} ${styles.placeBronze}`
  }

  return styles.place
}

function displayResult(rawValue: string | null, status: string) {
  if (
    status === 'DID_NOT_START' ||
    status === 'DID_NOT_FINISH' ||
    status === 'DISQUALIFIED'
  ) {
    return '—'
  }

  return rawValue?.trim() || '—'
}

function sortEntries(entries: PublicDisciplineResultEntry[]) {
  return [...entries].sort((left, right) => {
    const leftPlace = left.place ?? Number.MAX_SAFE_INTEGER
    const rightPlace = right.place ?? Number.MAX_SAFE_INTEGER

    return leftPlace - rightPlace
  })
}

export function PublicDisciplineResultsPage() {
  const { publicSlug, disciplineId } = useParams<{
    publicSlug: string
    disciplineId: string
  }>()

  const [results, setResults] =
    useState<PublicDisciplineResultsResponse | null>(null)

  const [eventTitle, setEventTitle] = useState<string | null>(null)

  const [disciplineName, setDisciplineName] =
    useState<string | null>(null)

  const [isLoading, setIsLoading] = useState(true)

  const [errorMessage, setErrorMessage] =
    useState<string | null>(null)

  const publishedEntriesCount = useMemo(
    () =>
      results?.units.reduce(
        (total, unit) => total + unit.entries.length,
        0,
      ) ?? 0,
    [results],
  )

  async function loadResults() {
    if (!publicSlug || !disciplineId) {
      setErrorMessage(
        'Не указана ссылка на событие или идентификатор дисциплины.',
      )
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const event = await getPublicEventDetails(publicSlug)

      const discipline = (event.disciplines ?? []).find(
        (item) => item.id === disciplineId,
      )

      if (!discipline) {
        setErrorMessage(
          'Дисциплина не найдена или больше не опубликована.',
        )
        return
      }

      const data = await getPublicDisciplineResults(
        event.id,
        disciplineId,
      )

      setEventTitle(event.title)
      setDisciplineName(discipline.name)
      setResults(data)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadResults()
  }, [publicSlug, disciplineId])

  return (
    <section className={styles.page}>
      <Link
        className={styles.backLink}
        to={publicSlug ? `/events/${publicSlug}` : '/'}
      >
        ← К событию
      </Link>

      <p className={styles.eyebrow}>Опубликованные результаты</p>

      <h1 className={styles.title}>
        {disciplineName
          ? `Результаты: ${disciplineName}`
          : 'Таблица результатов'}
      </h1>

      {eventTitle ? (
        <p className={styles.eventTitle}>{eventTitle}</p>
      ) : null}

      <p className={styles.description}>
        Здесь показаны только опубликованные результаты дисциплины.
      </p>

      {!isLoading && !errorMessage && results ? (
        <div className={styles.summaryRow}>
          <span>
            Соревновательных единиц: <strong>{results.units.length}</strong>
          </span>

          <span>
            Участников в протоколах:{' '}
            <strong>{publishedEntriesCount}</strong>
          </span>
        </div>
      ) : null}

      {isLoading ? (
        <div className={styles.stateCard}>
          <h2>Загрузка</h2>
          <p>Получаем опубликованные результаты дисциплины.</p>
        </div>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && results ? (
        results.units.length === 0 ? (
          <div className={styles.stateCard}>
            <h2>Результаты ещё не опубликованы</h2>

            <p>
              Организатор опубликует итоговую таблицу после завершения
              соревнования.
            </p>
          </div>
        ) : (
          <div className={styles.unitsList}>
            {results.units.map((unit) => {
              const entries = sortEntries(unit.entries)

              return (
                <article className={styles.unitCard} key={unit.id}>
                  <div className={styles.unitHeader}>
                    <div>
                      <p className={styles.unitLabel}>
                        Соревновательная единица
                      </p>

                      <h2 className={styles.unitTitle}>{unit.label}</h2>
                    </div>

                    <div className={styles.unitMeta}>
                      {unit.sequenceNumber !== null ? (
                        <span className={styles.sequenceBadge}>
                          № {unit.sequenceNumber}
                        </span>
                      ) : null}

                      <span className={styles.entriesBadge}>
                        {entries.length}{' '}
                        {entries.length === 1
                          ? 'участник'
                          : 'участников'}
                      </span>
                    </div>
                  </div>

                  {entries.length === 0 ? (
                    <p className={styles.hint}>
                      В этой соревновательной единице нет опубликованных
                      результатов.
                    </p>
                  ) : (
                    <div className={styles.tableWrap}>
                      <table className={styles.table}>
                        <thead>
                          <tr>
                            <th scope="col">Место</th>
                            <th scope="col">Участник</th>
                            <th scope="col">Клуб</th>
                            <th scope="col">Результат</th>
                            <th scope="col">Статус</th>
                          </tr>
                        </thead>

                        <tbody>
                          {entries.map((entry, index) => (
                            <tr
                              key={`${unit.id}-${entry.participantName}-${index}`}
                            >
                              <td>
                                <span className={placeClassName(entry.place)}>
                                  {entry.place ?? '—'}
                                </span>
                              </td>

                              <td>
                                <strong className={styles.participantName}>
                                  {entry.participantName}
                                </strong>

                                {entry.laneOrPosition !== null ? (
                                  <span className={styles.positionLabel}>
                                    Дорожка / позиция{' '}
                                    {entry.laneOrPosition}
                                  </span>
                                ) : null}
                              </td>

                              <td className={styles.clubCell}>
                                {entry.clubName ?? '—'}
                              </td>

                              <td className={styles.resultValue}>
                                {displayResult(
                                  entry.rawValue,
                                  entry.status,
                                )}
                              </td>

                              <td>
                                <span
                                  className={resultStatusClassName(
                                    entry.status,
                                  )}
                                >
                                  {resultStatusLabel(entry.status)}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        )
      ) : null}
    </section>
  )
}