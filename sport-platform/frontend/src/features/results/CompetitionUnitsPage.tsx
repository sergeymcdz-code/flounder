import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import {
  getCompetitionUnitsByDiscipline,
  type CompetitionUnitResponse,
} from '../../api/results.api'
import styles from './CompetitionUnitsPage.module.css'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр результатов.'
    }

    if (error.status === 404) {
      return 'Дисциплина не найдена.'
    }

    return error.message
  }

  return 'Не удалось загрузить соревновательные единицы.'
}

function unitStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
  }

  return labels[status] ?? status
}

function unitStatusClassName(status: string) {
  const classes: Record<string, string> = {
    DRAFT: styles.statusDraft,
    PUBLISHED: styles.statusPublished,
  }

  return `${styles.statusBadge} ${classes[status] ?? styles.statusNeutral}`
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Не назначено'
  }

  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value))
}

export function CompetitionUnitsPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const accessToken = session?.accessToken

  const [eventDisciplineId, setEventDisciplineId] = useState('')
  const [units, setUnits] = useState<CompetitionUnitResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const normalizedDisciplineId = eventDisciplineId.trim()

    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }

    if (!normalizedDisciplineId) {
      setErrorMessage('Введите ID дисциплины.')
      return
    }

    setIsLoading(true)
    setHasLoaded(false)
    setErrorMessage(null)

    try {
      const data = await getCompetitionUnitsByDiscipline(
        normalizedDisciplineId,
        accessToken,
      )

      setUnits(data)
      setHasLoaded(true)
    } catch (error) {
      setUnits([])
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/operator/payments">
        ← К панели оператора
      </Link>

      <p className={styles.eyebrow}>Оператор</p>

      <h1 className={styles.title}>Результаты</h1>

      <p className={styles.description}>
        Введите идентификатор дисциплины, чтобы открыть список заплывов,
        матчей, групп или финалов.
      </p>

      <form className={styles.searchForm} onSubmit={handleSubmit}>
        <label className={styles.field}>
          <span>ID дисциплины</span>

          <input
            autoComplete="off"
            disabled={isLoading}
            onChange={(event) => setEventDisciplineId(event.target.value)}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            spellCheck={false}
            type="text"
            value={eventDisciplineId}
          />
        </label>

        <button
          className={styles.submitButton}
          disabled={isLoading}
          type="submit"
        >
          {isLoading ? 'Загружаем…' : 'Открыть'}
        </button>
      </form>

      {errorMessage ? (
        <p className={styles.errorMessage} role="alert">
          {errorMessage}
        </p>
      ) : null}

      {hasLoaded && units.length === 0 ? (
        <div className={styles.stateCard}>
          <h2>Соревновательных единиц пока нет</h2>
          <p>
            Организатор должен создать заплыв, матч, группу или финал для этой
            дисциплины.
          </p>
        </div>
      ) : null}

      {units.length > 0 ? (
        <div className={styles.unitsList}>
          {units.map((unit) => (
            <article className={styles.unitCard} key={unit.id}>
              <div>
                <p className={styles.unitLabel}>
                  Единица #{unit.sequenceNumber ?? '—'}
                </p>

                <h2 className={styles.unitTitle}>{unit.label}</h2>

                <p className={styles.unitMeta}>
                  Проведение: {formatDate(unit.scheduledAt)}
                </p>
              </div>

              <div className={styles.unitActions}>
                <span className={unitStatusClassName(unit.status)}>
                  {unitStatusLabel(unit.status)}
                </span>

                <Link
                  className={styles.openLink}
                  to={`/operator/competition-units/${unit.id}`}
                >
                  Открыть результаты
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}