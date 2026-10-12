import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/http'
import {
  changeCompetitionUnitPublication,
  getCompetitionUnitDetails,
  recalculatePlaces,
  recordResult,
  type CompetitionUnitDetailsResponse,
  type RankingStrategy,
  type ResultType,
} from '../../api/results.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './JudgePanelPage.module.css'

type EditableResultStatus =
  | 'PENDING'
  | 'DID_NOT_START'
  | 'DID_NOT_FINISH'
  | 'DISQUALIFIED'

const RESULT_STATUS_OPTIONS: Array<{
  value: EditableResultStatus
  label: string
}> = [
  {
    value: 'PENDING',
    label: 'Обычный результат',
  },
  {
    value: 'DID_NOT_START',
    label: 'DNS — не стартовал',
  },
  {
    value: 'DID_NOT_FINISH',
    label: 'DNF — не финишировал',
  },
  {
    value: 'DISQUALIFIED',
    label: 'DSQ — дисквалифицирован',
  },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия истекла. Войдите в систему снова.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав для работы с результатами.'
    }

    if (error.status === 404) {
      return 'Заплыв, матч или соревновательная единица не найдены.'
    }

    return error.message
  }

  return fallback
}

function resultStatusLabel(status: string | null) {
  const labels: Record<string, string> = {
    PENDING: 'Ожидает пересчёта',
    VALID: 'Учтён',
    DID_NOT_START: 'Не стартовал',
    DID_NOT_FINISH: 'Не финишировал',
    DISQUALIFIED: 'Дисквалифицирован',
    CORRECTED: 'Исправлен',
    EXHIBITION: 'Вне зачёта',
  }

  return status ? labels[status] ?? status : 'Нет результата'
}

function resultStatusClassName(status: string | null) {
  if (status === 'VALID') {
    return `${styles.resultStatus} ${styles.resultStatusValid}`
  }

  if (
    status === 'DID_NOT_START' ||
    status === 'DID_NOT_FINISH'
  ) {
    return `${styles.resultStatus} ${styles.resultStatusWarning}`
  }

  if (status === 'DISQUALIFIED') {
    return `${styles.resultStatus} ${styles.resultStatusDanger}`
  }

  if (status === 'CORRECTED') {
    return `${styles.resultStatus} ${styles.resultStatusCorrected}`
  }

  if (status === 'EXHIBITION') {
    return `${styles.resultStatus} ${styles.resultStatusNeutral}`
  }

  return `${styles.resultStatus} ${styles.resultStatusPending}`
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
    return `${styles.unitStatus} ${styles.unitStatusPublished}`
  }

  if (status === 'DRAFT') {
    return `${styles.unitStatus} ${styles.unitStatusDraft}`
  }

  return `${styles.unitStatus} ${styles.unitStatusNeutral}`
}

function sortEntries(
  entries: CompetitionUnitDetailsResponse['entries'],
) {
  return [...entries].sort((left, right) => {
    const leftLane = left.laneOrPosition ?? Number.MAX_SAFE_INTEGER
    const rightLane = right.laneOrPosition ?? Number.MAX_SAFE_INTEGER

    return leftLane - rightLane
  })
}

export function JudgePanelPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const { unitId } = useParams<{ unitId: string }>()
  const accessToken = session?.accessToken

  const [unit, setUnit] =
    useState<CompetitionUnitDetailsResponse | null>(null)

  const [draftValues, setDraftValues] =
    useState<Record<string, string>>({})

  const [draftStatuses, setDraftStatuses] =
    useState<Record<string, EditableResultStatus>>({})

  const [resultType, setResultType] =
    useState<ResultType>('TIME')

  const [rankingStrategy, setRankingStrategy] =
    useState<RankingStrategy>('ASC')

  const [isLoading, setIsLoading] = useState(true)

  const [savingEntryId, setSavingEntryId] =
    useState<string | null>(null)

  const [isRecalculating, setIsRecalculating] = useState(false)

  const [isChangingPublication, setIsChangingPublication] =
    useState(false)

  const [errorMessage, setErrorMessage] =
    useState<string | null>(null)

  const [successMessage, setSuccessMessage] =
    useState<string | null>(null)

  const entries = useMemo(
    () => (unit ? sortEntries(unit.entries) : []),
    [unit],
  )

  const isPublished = unit?.status === 'PUBLISHED'

  const isBusy =
    savingEntryId !== null ||
    isRecalculating ||
    isChangingPublication

  const completedResultsCount = useMemo(
    () =>
      entries.filter(
        (entry) =>
          Boolean(entry.rawValue?.trim()) ||
          entry.resultStatus === 'DID_NOT_START' ||
          entry.resultStatus === 'DID_NOT_FINISH' ||
          entry.resultStatus === 'DISQUALIFIED',
      ).length,
    [entries],
  )

  const hasPendingResults = entries.some(
    (entry) =>
      Boolean(entry.rawValue?.trim()) &&
      entry.resultStatus === 'PENDING',
  )

  function updateDraftValues(
    nextUnit: CompetitionUnitDetailsResponse,
  ) {
    setDraftValues(
      Object.fromEntries(
        nextUnit.entries.map((entry) => [
          entry.entryId,
          entry.rawValue ?? '',
        ]),
      ),
    )

    setDraftStatuses(
      Object.fromEntries(
        nextUnit.entries.map((entry) => [
          entry.entryId,
          entry.resultStatus === 'DID_NOT_START' ||
          entry.resultStatus === 'DID_NOT_FINISH' ||
          entry.resultStatus === 'DISQUALIFIED'
            ? entry.resultStatus
            : 'PENDING',
        ]),
      ),
    )
  }

  async function loadUnit() {
    if (!unitId || !accessToken) {
      return
    }

    setIsLoading(true)
    setErrorMessage(null)

    try {
      const loadedUnit = await getCompetitionUnitDetails(
        unitId,
        accessToken,
      )

      setUnit(loadedUnit)
      updateDraftValues(loadedUnit)
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          'Не удалось загрузить стартовый лист и результаты.',
        ),
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    if (!unitId) {
      setErrorMessage('Идентификатор заплыва или матча не указан.')
      setIsLoading(false)
      return
    }

    void loadUnit()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, unitId, navigate])

  async function handleSaveResult(entryId: string) {
    if (!accessToken || isPublished) {
      return
    }

    const status = draftStatuses[entryId] ?? 'PENDING'
    const rawValue = draftValues[entryId]?.trim()

    if (status === 'PENDING' && !rawValue) {
      setErrorMessage('Введите результат перед сохранением.')
      return
    }

    setSavingEntryId(entryId)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      await recordResult(
        status === 'PENDING'
          ? {
              competitionUnitEntryId: entryId,
              rawValue,
            }
          : {
              competitionUnitEntryId: entryId,
              rawValue: '',
              status,
            },
        {
          resultType,
          accessToken,
        },
      )

      setSuccessMessage(
        status === 'PENDING'
          ? 'Результат сохранён. Пересчитайте места, чтобы обновить таблицу.'
          : `Статус «${resultStatusLabel(status)}» сохранён.`,
      )

      await loadUnit()
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось сохранить результат.'),
      )
    } finally {
      setSavingEntryId(null)
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
        rankingStrategy,
        accessToken,
      })

      setUnit(updatedUnit)
      updateDraftValues(updatedUnit)
      setSuccessMessage('Места успешно пересчитаны.')
    } catch (error) {
      setErrorMessage(
        getErrorMessage(error, 'Не удалось пересчитать места.'),
      )
    } finally {
      setIsRecalculating(false)
    }
  }

  async function handleChangePublication() {
    if (!unit || !accessToken) {
      return
    }

    const willPublish = unit.status !== 'PUBLISHED'

    setIsChangingPublication(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      await changeCompetitionUnitPublication(
        unit.id,
        willPublish,
        accessToken,
      )

      await loadUnit()

      setSuccessMessage(
        willPublish
          ? 'Результаты опубликованы и доступны на публичной странице.'
          : 'Публикация результатов снята.',
      )
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          'Не удалось изменить статус публикации результатов.',
        ),
      )
    } finally {
      setIsChangingPublication(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/operator/results">
        ← К списку соревновательных единиц
      </Link>

      <div className={styles.titleRow}>
        <div>
          <p className={styles.eyebrow}>Судейская панель</p>

          <h1 className={styles.title}>
            {unit?.label ?? 'Результаты'}
          </h1>
        </div>

        {unit ? (
          <span className={unitStatusClassName(unit.status)}>
            {unitStatusLabel(unit.status)}
          </span>
        ) : null}
      </div>

      <p className={styles.description}>
        Вносите результаты участников, затем пересчитывайте итоговые места.
        Для времени используйте формат <code>00:58.42</code>.
      </p>

      {isLoading ? (
        <div className={styles.stateCard}>
          <h2>Загрузка</h2>
          <p>Получаем стартовый лист и результаты.</p>
        </div>
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

      {!isLoading && !errorMessage && unit ? (
        <>
          <div className={styles.summaryCard}>
            <div>
              <span className={styles.summaryLabel}>Статус</span>

              <strong>{unitStatusLabel(unit.status)}</strong>
            </div>

            <div>
              <span className={styles.summaryLabel}>Участников</span>

              <strong>{unit.entries.length}</strong>
            </div>

            <div>
              <span className={styles.summaryLabel}>Введено результатов</span>

              <strong>
                {completedResultsCount} / {unit.entries.length}
              </strong>
            </div>

            <div className={styles.disciplineSummary}>
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

              <button
                className={styles.unpublishButton}
                disabled={isBusy}
                onClick={handleChangePublication}
                type="button"
              >
                {isChangingPublication
                  ? 'Снимаем…'
                  : 'Снять с публикации'}
              </button>
            </div>
          ) : null}

          <section className={styles.toolbarCard}>
            <div>
              <h2>Настройки результата</h2>

              <p>
                Выберите формат результата. Для времени лучшим считается
                меньшее значение, для очков — большее.
              </p>
            </div>

            <div className={styles.toolbarControls}>
              <label className={styles.field}>
                <span>Тип результата</span>

                <select
                  disabled={isPublished || isBusy}
                  onChange={(event) => {
                    const nextType = event.target.value as ResultType

                    setResultType(nextType)
                    setRankingStrategy(
                      nextType === 'TIME' ? 'ASC' : 'DESC',
                    )
                  }}
                  value={resultType}
                >
                  <option value="TIME">
                    Время — меньше лучше
                  </option>

                  <option value="POINTS">
                    Очки — больше лучше
                  </option>
                </select>
              </label>

              <button
                className={styles.recalculateButton}
                disabled={
                  isPublished ||
                  isBusy ||
                  entries.length === 0
                }
                onClick={handleRecalculate}
                type="button"
              >
                {isRecalculating
                  ? 'Пересчитываем…'
                  : 'Пересчитать места'}
              </button>

              {!isPublished ? (
                <button
                  className={styles.publishButton}
                  disabled={
                    isBusy ||
                    entries.length === 0 ||
                    hasPendingResults
                  }
                  onClick={handleChangePublication}
                  type="button"
                >
                  {isChangingPublication
                    ? 'Публикуем…'
                    : 'Опубликовать результаты'}
                </button>
              ) : null}
            </div>

            {!isPublished && hasPendingResults ? (
              <p className={styles.recalculateHint}>
                Перед публикацией пересчитайте места после внесения
                результатов.
              </p>
            ) : null}
          </section>

          <section className={styles.entriesSection}>
            <div className={styles.sectionHeader}>
              <div>
                <h2>Стартовый протокол</h2>

                <p>
                  Сохраняйте результат каждого участника отдельно. После
                  изменений пересчитайте места.
                </p>
              </div>

              <span className={styles.entriesCount}>
                {entries.length}{' '}
                {entries.length === 1 ? 'участник' : 'участников'}
              </span>
            </div>

            {entries.length === 0 ? (
              <div className={styles.stateCard}>
                <h2>Участники не назначены</h2>

                <p>
                  Организатор должен добавить подтверждённые заявки в этот
                  заплыв или матч.
                </p>
              </div>
            ) : (
              <div className={styles.tableWrap}>
                <table className={styles.resultsTable}>
                  <thead>
                    <tr>
                      <th scope="col">Место</th>
                      <th scope="col">Дорожка</th>
                      <th scope="col">Участник</th>
                      <th scope="col">Результат</th>
                      <th scope="col">Статус ввода</th>
                      <th scope="col">Статус</th>
                      <th scope="col">
                        <span className={styles.visuallyHidden}>
                          Действия
                        </span>
                      </th>
                    </tr>
                  </thead>

                  <tbody>
                    {entries.map((entry) => {
                      const isSaving =
                        savingEntryId === entry.entryId

                      const draftStatus =
                        draftStatuses[entry.entryId] ?? 'PENDING'

                      const hasSpecialStatus =
                        draftStatus !== 'PENDING'

                      const isEntryDisabled =
                        isPublished ||
                        isSaving ||
                        isBusy

                      return (
                        <tr key={entry.entryId}>
                          <td className={styles.placeCell}>
                            {entry.finalPlace ?? '—'}
                          </td>

                          <td className={styles.laneCell}>
                            {entry.laneOrPosition ?? '—'}
                          </td>

                          <td>
                            <strong className={styles.participantName}>
                              {entry.participantName}
                            </strong>

                            <span className={styles.registrationId}>
                              Заявка {entry.registrationId.slice(0, 8)}
                            </span>
                          </td>

                          <td>
                            <input
                              className={styles.resultInput}
                              disabled={
                                isEntryDisabled || hasSpecialStatus
                              }
                              onChange={(event) => {
                                setDraftValues((current) => ({
                                  ...current,
                                  [entry.entryId]: event.target.value,
                                }))
                              }}
                              placeholder={
                                hasSpecialStatus
                                  ? 'Не требуется'
                                  : resultType === 'TIME'
                                    ? '00:58.42'
                                    : '0'
                              }
                              spellCheck={false}
                              type="text"
                              value={draftValues[entry.entryId] ?? ''}
                            />
                          </td>

                          <td>
                            <select
                              className={styles.statusSelect}
                              disabled={isEntryDisabled}
                              onChange={(event) => {
                                const nextStatus =
                                  event.target
                                    .value as EditableResultStatus

                                setDraftStatuses((current) => ({
                                  ...current,
                                  [entry.entryId]: nextStatus,
                                }))

                                if (nextStatus !== 'PENDING') {
                                  setDraftValues((current) => ({
                                    ...current,
                                    [entry.entryId]: '',
                                  }))
                                }
                              }}
                              value={draftStatus}
                            >
                              {RESULT_STATUS_OPTIONS.map((option) => (
                                <option
                                  key={option.value}
                                  value={option.value}
                                >
                                  {option.label}
                                </option>
                              ))}
                            </select>
                          </td>

                          <td>
                            <span
                              className={resultStatusClassName(
                                entry.resultStatus,
                              )}
                            >
                              {resultStatusLabel(entry.resultStatus)}
                            </span>
                          </td>

                          <td>
                            <button
                              className={styles.saveButton}
                              disabled={
                                isEntryDisabled ||
                                (!hasSpecialStatus &&
                                  !draftValues[
                                    entry.entryId
                                  ]?.trim())
                              }
                              onClick={() =>
                                handleSaveResult(entry.entryId)
                              }
                              type="button"
                            >
                              {isSaving
                                ? 'Сохраняем…'
                                : 'Сохранить'}
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : null}
    </section>
  )
}