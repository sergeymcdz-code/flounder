import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import {
  changeCompetitionUnitPublication,
  getCompetitionUnitDetails,
  recalculatePlaces,
  recordResult,
  type CompetitionUnitDetailsResponse,
  type CompetitionUnitEntryView,
  type RankingStrategy,
  type ResultType,
} from '../../api/results.api'
import styles from './OperatorResultsPage.module.css'

type EntryDraft = {
  rawValue: string
  status: EntryResultStatus
}

type EntryResultStatus =
  | 'PENDING'
  | 'DID_NOT_START'
  | 'DID_NOT_FINISH'
  | 'DISQUALIFIED'

const RESULT_TYPES: Array<{
  value: ResultType
  label: string
}> = [
  {
    value: 'TIME',
    label: 'Время',
  },
  {
    value: 'POINTS',
    label: 'Очки',
  },
]

const RESULT_STATUSES: Array<{
  value: EntryResultStatus
  label: string
}> = [
  {
    value: 'PENDING',
    label: 'Обычный результат',
  },
  {
    value: 'DID_NOT_START',
    label: 'Не стартовал',
  },
  {
    value: 'DID_NOT_FINISH',
    label: 'Не финишировал',
  },
  {
    value: 'DISQUALIFIED',
    label: 'Дисквалифицирован',
  },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для выполнения этого действия.'
    }

    if (error.status === 404) {
      return 'Соревновательная единица не найдена.'
    }

    return error.message
  }

  return fallback
}

function unitStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
  }

  return labels[status] ?? status
}

function unitStatusClassName(status: string) {
  if (status === 'PUBLISHED') {
    return `${styles.statusBadge} ${styles.statusPublished}`
  }

  if (status === 'DRAFT') {
    return `${styles.statusBadge} ${styles.statusDraft}`
  }

  return `${styles.statusBadge} ${styles.statusNeutral}`
}

function resultStatusLabel(status: string | null) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает результата',
    DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал',
    DISQUALIFIED: 'Дисквалифицирован',
  }

  if (!status) {
    return 'Не введён'
  }

  return labels[status] ?? status
}

function resultStatusClassName(status: string | null) {
  if (!status || status === 'PENDING') {
    return `${styles.resultStatus} ${styles.resultStatusPending}`
  }

  if (status === 'DISQUALIFIED') {
    return `${styles.resultStatus} ${styles.resultStatusDanger}`
  }

  return `${styles.resultStatus} ${styles.resultStatusNeutral}`
}

function createEntryDrafts(
  entries: CompetitionUnitEntryView[],
): Record<string, EntryDraft> {
  return Object.fromEntries(
    entries.map((entry) => [
      entry.entryId,
      {
        rawValue: entry.rawValue ?? '',
        status: isEntryResultStatus(entry.resultStatus)
          ? entry.resultStatus
          : 'PENDING',
      },
    ]),
  )
}

function isEntryResultStatus(value: string | null): value is EntryResultStatus {
  return (
    value === 'PENDING' ||
    value === 'DID_NOT_START' ||
    value === 'DID_NOT_FINISH' ||
    value === 'DISQUALIFIED'
  )
}

export function OperatorResultsPage() {
  const { unitId } = useParams<{ unitId: string }>()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [unit, setUnit] = useState<CompetitionUnitDetailsResponse | null>(null)
  const [drafts, setDrafts] = useState<Record<string, EntryDraft>>({})
  const [resultType, setResultType] = useState<ResultType>('TIME')
  const [rankingStrategy, setRankingStrategy] =
    useState<RankingStrategy>('ASC')

  const [isLoading, setIsLoading] = useState(true)
  const [isSavingEntryId, setIsSavingEntryId] = useState<string | null>(null)
  const [isRecalculating, setIsRecalculating] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)

  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const isPublished = unit?.status === 'PUBLISHED'
  const canPublish = unit?.status === 'DRAFT'
  const canUnpublish = unit?.status === 'PUBLISHED'

  const isBusy =
    isSavingEntryId !== null || isRecalculating || isPublishing

  const filledResultsCount = useMemo(() => {
    if (!unit) {
      return 0
    }

    return unit.entries.filter(
      (entry) =>
        Boolean(entry.rawValue) ||
        entry.resultStatus === 'DID_NOT_START' ||
        entry.resultStatus === 'DID_NOT_FINISH' ||
        entry.resultStatus === 'DISQUALIFIED',
    ).length
  }, [unit])

  useEffect(() => {
    if (!unitId || !accessToken) {
      setIsLoading(false)
      return
    }
    const resolvedUnitId = unitId
    const resolvedAccessToken = accessToken

    let isMounted = true

    async function loadUnit() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const loadedUnit = await getCompetitionUnitDetails(
          resolvedUnitId,
          resolvedAccessToken,
        )
        if (!isMounted) {
          return
        }

        setUnit(loadedUnit)
        setDrafts(createEntryDrafts(loadedUnit.entries))
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            getErrorMessage(
              error,
              'Не удалось загрузить соревновательную единицу.',
            ),
          )
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadUnit()

    return () => {
      isMounted = false
    }
  }, [accessToken, unitId])

  function updateDraft(
    entryId: string,
    field: keyof EntryDraft,
    value: string,
  ) {
    setDrafts((currentDrafts) => ({
      ...currentDrafts,
      [entryId]: {
        rawValue: currentDrafts[entryId]?.rawValue ?? '',
        status: currentDrafts[entryId]?.status ?? 'PENDING',
        [field]: value,
      } as EntryDraft,
    }))
  }

  async function handleSaveResult(
    event: FormEvent<HTMLFormElement>,
    entry: CompetitionUnitEntryView,
  ) {
    event.preventDefault()

    if (!accessToken || isPublished) {
      return
    }

    const draft = drafts[entry.entryId] ?? {
      rawValue: '',
      status: 'PENDING' as const,
    }

    if (draft.status === 'PENDING' && !draft.rawValue.trim()) {
      setErrorMessage('Введите результат или укажите специальный статус.')
      return
    }

    setIsSavingEntryId(entry.entryId)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const savedResult = await recordResult(
        {
          competitionUnitEntryId: entry.entryId,
          rawValue: draft.status === 'PENDING' ? draft.rawValue.trim() : '',
          status: draft.status,
        },
        {
          accessToken,
          resultType,
        },
      )

      setUnit((currentUnit) => {
        if (!currentUnit) {
          return currentUnit
        }

        return {
          ...currentUnit,
          entries: currentUnit.entries.map((currentEntry) =>
            currentEntry.entryId === entry.entryId
              ? {
                  ...currentEntry,
                  rawValue: savedResult.rawValue,
                  resultType: savedResult.resultType,
                  resultStatus: savedResult.status,
                  finalPlace: savedResult.finalPlace,
                }
              : currentEntry,
          ),
        }
      })

      setSuccessMessage(
        `Результат участника «${entry.participantName}» сохранён.`,
      )
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось сохранить результат.'),
      )
    } finally {
      setIsSavingEntryId(null)
    }
  }

  async function handleRecalculate() {
    if (!unit || !accessToken || isPublished) {
      return
    }

    setIsRecalculating(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updatedUnit = await recalculatePlaces(unit.id, {
        accessToken,
        rankingStrategy,
      })

      setUnit(updatedUnit)
      setDrafts(createEntryDrafts(updatedUnit.entries))
      setSuccessMessage('Итоговые места пересчитаны.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось пересчитать итоговые места.'),
      )
    } finally {
      setIsRecalculating(false)
    }
  }

  async function handlePublicationChange(published: boolean) {
    if (!unit || !accessToken) {
      return
    }

    setIsPublishing(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const updatedUnit = await changeCompetitionUnitPublication(
        unit.id,
        published,
        accessToken,
      )

      setUnit(updatedUnit)
      setDrafts(createEntryDrafts(updatedUnit.entries))
      setSuccessMessage(
        published
          ? 'Результаты опубликованы.'
          : 'Результаты сняты с публикации.',
      )
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          published
            ? 'Не удалось опубликовать результаты.'
            : 'Не удалось снять результаты с публикации.',
        ),
      )
    } finally {
      setIsPublishing(false)
    }
  }

  if (!unitId || !accessToken) {
    return null
  }

  if (isLoading) {
    return (
      <section className={styles.page}>
        <div className={styles.stateCard}>Загрузка судейской панели…</div>
      </section>
    )
  }

  if (errorMessage && !unit) {
    return (
      <section className={styles.page}>
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>

        <Link className={styles.backLink} to="/operator/results">
          ← К поиску соревнований
        </Link>
      </section>
    )
  }

  if (!unit) {
    return null
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/operator/results">
        ← К списку соревновательных единиц
      </Link>

      <div className={styles.titleRow}>
        <div>
          <p className={styles.eyebrow}>Судейская панель</p>
          <h1 className={styles.title}>{unit.label}</h1>
        </div>

        <span className={unitStatusClassName(unit.status)}>
          {unitStatusLabel(unit.status)}
        </span>
      </div>

      <div className={styles.summaryCard}>
        <div>
          <span className={styles.summaryLabel}>Участников</span>
          <strong>{unit.entries.length}</strong>
        </div>

        <div>
          <span className={styles.summaryLabel}>Введено результатов</span>
          <strong>{filledResultsCount}</strong>
        </div>

        <div>
          <span className={styles.summaryLabel}>ID дисциплины</span>
          <code>{unit.eventDisciplineId}</code>
        </div>
      </div>

      {isPublished ? (
        <div className={styles.publishedNotice} role="status">
          <div>
            <strong>Результаты опубликованы</strong>

            <p>
              Ввод результатов и пересчёт мест заблокированы, чтобы
              опубликованный протокол не изменился случайно.
            </p>
          </div>

          {canUnpublish ? (
            <button
              className={styles.noticeAction}
              disabled={isBusy}
              onClick={() => handlePublicationChange(false)}
              type="button"
            >
              {isPublishing ? 'Снимаем…' : 'Снять с публикации'}
            </button>
          ) : null}
        </div>
      ) : null}

      <section className={styles.controlsCard}>
        <div className={styles.controlsHeader}>
          <div>
            <h2>Настройки результата</h2>

            <p>
              Выберите тип результата и правило, по которому будут рассчитаны
              итоговые места.
            </p>
          </div>
        </div>

        <div className={styles.controlsGrid}>
          <label className={styles.field}>
            <span>Тип результата</span>

            <select
              disabled={isPublished || isBusy}
              onChange={(event) =>
                setResultType(event.target.value as ResultType)
              }
              value={resultType}
            >
              {RESULT_TYPES.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </label>

          <label className={styles.field}>
            <span>Ранжирование</span>

            <select
              disabled={isPublished || isBusy}
              onChange={(event) =>
                setRankingStrategy(event.target.value as RankingStrategy)
              }
              value={rankingStrategy}
            >
              <option value="ASC">
                По возрастанию — меньшее значение лучше
              </option>

              <option value="DESC">
                По убыванию — большее значение лучше
              </option>
            </select>
          </label>

          <button
            className={styles.recalculateButton}
            disabled={isPublished || isBusy}
            onClick={handleRecalculate}
            type="button"
          >
            {isRecalculating ? 'Пересчитываем…' : 'Пересчитать места'}
          </button>
        </div>
      </section>

      <section className={styles.entriesSection}>
        <div className={styles.sectionHeader}>
          <div>
            <h2>Стартовый протокол</h2>

            <p>Введите результат каждого участника и сохраните изменения.</p>
          </div>

          <span className={styles.entriesCount}>
            {unit.entries.length}{' '}
            {unit.entries.length === 1 ? 'участник' : 'участников'}
          </span>
        </div>

        {unit.entries.length === 0 ? (
          <div className={styles.stateCard}>
            В этой соревновательной единице пока нет участников.
          </div>
        ) : (
          <div className={styles.entriesList}>
            {unit.entries.map((entry) => {
              const draft = drafts[entry.entryId] ?? {
                rawValue: '',
                status: 'PENDING' as const,
              }

              const isSaving = isSavingEntryId === entry.entryId
              const isSpecialStatus = draft.status !== 'PENDING'
              const isEntryDisabled = isPublished || isSaving || isBusy

              return (
                <article className={styles.entryCard} key={entry.entryId}>
                  <div className={styles.entryHeader}>
                    <div className={styles.participantBlock}>
                      <span className={styles.positionNumber}>
                        {entry.laneOrPosition ?? '—'}
                      </span>

                      <div>
                        <h3>{entry.participantName}</h3>

                        <p>
                          {entry.laneOrPosition !== null
                            ? `Дорожка / позиция ${entry.laneOrPosition}`
                            : 'Дорожка / позиция не назначена'}
                        </p>
                      </div>
                    </div>

                    <div className={styles.entryResult}>
                      {entry.finalPlace ? (
                        <span className={styles.placeBadge}>
                          Место {entry.finalPlace}
                        </span>
                      ) : null}

                      <span
                        className={resultStatusClassName(entry.resultStatus)}
                      >
                        {resultStatusLabel(entry.resultStatus)}
                      </span>
                    </div>
                  </div>

                  <form
                    className={styles.entryForm}
                    onSubmit={(event) => handleSaveResult(event, entry)}
                  >
                    <label className={styles.field}>
                      <span>Результат</span>

                      <input
                        disabled={isEntryDisabled}
                        onChange={(event) =>
                          updateDraft(
                            entry.entryId,
                            'rawValue',
                            event.target.value,
                          )
                        }
                        placeholder={
                          resultType === 'TIME'
                            ? 'Например: 1:05.32'
                            : 'Например: 125'
                        }
                        required={!isSpecialStatus}
                        spellCheck={false}
                        type="text"
                        value={draft.rawValue}
                      />
                    </label>

                    <label className={styles.field}>
                      <span>Статус</span>

                      <select
                        disabled={isEntryDisabled}
                        onChange={(event) =>
                          updateDraft(
                            entry.entryId,
                            'status',
                            event.target.value,
                          )
                        }
                        value={draft.status}
                      >
                        {RESULT_STATUSES.map((status) => (
                          <option key={status.value} value={status.value}>
                            {status.label}
                          </option>
                        ))}
                      </select>
                    </label>

                    <button
                      className={styles.saveButton}
                      disabled={isEntryDisabled}
                      type="submit"
                    >
                      {isSaving ? 'Сохраняем…' : 'Сохранить'}
                    </button>
                  </form>

                  <p className={styles.entryTechnicalId}>
                    ID записи протокола: <code>{entry.entryId}</code>
                  </p>
                </article>
              )
            })}
          </div>
        )}
      </section>

      {canPublish ? (
        <section className={styles.publicationCard}>
          <div>
            <h2>Публикация</h2>

            <p>
              Перед публикацией убедитесь, что результаты всех участников
              сохранены, а итоговые места пересчитаны.
            </p>
          </div>

          <button
            className={styles.publishButton}
            disabled={isBusy}
            onClick={() => handlePublicationChange(true)}
            type="button"
          >
            {isPublishing ? 'Публикуем…' : 'Опубликовать результаты'}
          </button>
        </section>
      ) : null}

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {successMessage ? (
        <p className={styles.successMessage} role="status">
          {successMessage}
        </p>
      ) : null}
    </section>
  )
}