import { useEffect, useState, type FormEvent } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import {
  createRegulationTemplate,
  createRegulationVersion,
  publishRegulationVersion,
  type RegulationRules,
} from '../../api/regulations.api'
import {
  getSports,
  type SportResponse,
} from '../../api/sports.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './CreateRegulationPage.module.css'

type FormState = {
  sportId: string
  code: string
  name: string
  description: string
  versionNo: string
  effectiveFrom: string
  notes: string
  rulesText: string
}

const initialForm: FormState = {
  sportId: '',
  code: '',
  name: '',
  description: '',
  versionNo: '1',
  effectiveFrom: '',
  notes: '',
  rulesText: '',
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на создание регламентов.'
    }

    return error.message || 'Не удалось сохранить регламент.'
  }

  return 'Не удалось сохранить регламент.'
}

export function CreateRegulationPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const [form, setForm] = useState<FormState>(initialForm)
  const [sports, setSports] = useState<SportResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadSports() {
      try {
        const result = await getSports(authenticatedAccessToken)
        if (isMounted) {
          setSports(result.filter((sport) => sport.isActive))
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadSports()

    return () => {
      isMounted = false
    }
  }, [accessToken])

  function updateField<K extends keyof FormState>(
    field: K,
    value: FormState[K],
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)

    if (!accessToken) {
      setErrorMessage('Не удалось определить активную сессию.')
      return
    }

    if (!form.sportId || !form.code.trim() || !form.name.trim()) {
      setErrorMessage('Заполните вид спорта, код и название регламента.')
      return
    }

    const versionNo = Number(form.versionNo)

    if (!Number.isInteger(versionNo) || versionNo <= 0) {
      setErrorMessage('Номер версии должен быть положительным целым числом.')
      return
    }

    const rulesJson: RegulationRules = {
      title: form.name.trim(),
      additionalConditions: form.rulesText
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean),
    }

    setIsSubmitting(true)

    try {
      const template = await createRegulationTemplate(
        {
          sportId: form.sportId,
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          description: form.description.trim() || null,
        },
        accessToken,
      )

      const version = await createRegulationVersion(
        template.id,
        {
          versionNo,
          rulesJson,
          notes: form.notes.trim() || null,
          effectiveFrom: form.effectiveFrom || null,
        },
        accessToken,
      )

      const publishedVersion = await publishRegulationVersion(
        version.id,
        accessToken,
      )

      navigate('/organizer/events/new', {
        replace: true,
        state: {
          createdRegulationVersionId: publishedVersion.id,
        },
      })
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page}>
      <NavLink className={styles.backLink} to="/organizer/events/new">
        ← Вернуться к созданию мероприятия
      </NavLink>

      <p className={styles.eyebrow}>Панель организатора</p>

      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>
            Новый регламент
          </h1>

          <p className={styles.description}>
            После сохранения регламент будет опубликован
            и станет доступен при создании мероприятия.
          </p>
        </div>
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Основные сведения</h2>

          <div className={styles.grid}>
            <label className={styles.field}>
              <span className={styles.label}>Вид спорта</span>
              <select
                className={styles.input}
                value={form.sportId}
                disabled={isLoading}
                onChange={(event) => updateField('sportId', event.target.value)}
                required
              >
                <option value="">Выберите вид спорта</option>
                {sports.map((sport) => (
                  <option key={sport.id} value={sport.id}>
                    {sport.name}
                  </option>
                ))}
              </select>
            </label>

            <label className={styles.field}>
              <span className={styles.label}>Код регламента</span>
              <input
                className={styles.input}
                value={form.code}
                onChange={(event) => updateField('code', event.target.value)}
                placeholder="SWIM-MSK-2026"
                maxLength={64}
                required
              />
            </label>

            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Название</span>
              <input
                className={styles.input}
                value={form.name}
                onChange={(event) => updateField('name', event.target.value)}
                placeholder="Регламент соревнований по плаванию"
                maxLength={255}
                required
              />
            </label>

            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Описание</span>
              <textarea
                className={styles.textarea}
                rows={4}
                value={form.description}
                onChange={(event) =>
                  updateField('description', event.target.value)
                }
                placeholder="Кратко опишите назначение и область применения регламента."
              />
            </label>
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Версия и правила</h2>

          <div className={styles.grid}>
            <label className={styles.field}>
              <span className={styles.label}>Номер версии</span>
              <input
                className={styles.input}
                type="number"
                min="1"
                value={form.versionNo}
                onChange={(event) => updateField('versionNo', event.target.value)}
                required
              />
            </label>

            <label className={styles.field}>
              <span className={styles.label}>Действует с</span>
              <input
                className={styles.input}
                type="date"
                value={form.effectiveFrom}
                onChange={(event) =>
                  updateField('effectiveFrom', event.target.value)
                }
              />
            </label>

            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Правила</span>
              <textarea
                className={styles.textarea}
                rows={10}
                value={form.rulesText}
                onChange={(event) =>
                  updateField('rulesText', event.target.value)
                }
                placeholder={
                  'Каждое правило с новой строки:\n' +
                  'Допуск участников от 14 лет\n' +
                  'Обязательна медицинская справка\n' +
                  'Регистрация завершается за 24 часа'
                }
              />
            </label>

            <label className={`${styles.field} ${styles.fieldFull}`}>
              <span className={styles.label}>Примечание к версии</span>
              <textarea
                className={styles.textarea}
                rows={3}
                value={form.notes}
                onChange={(event) => updateField('notes', event.target.value)}
                placeholder="Например: Первая редакция для сезона 2026."
              />
            </label>
          </div>
        </article>

        {errorMessage ? (
          <p className={styles.errorMessage} role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className={styles.actions}>
          <NavLink className={styles.secondaryButton} to="/organizer/events/new">
            Отмена
          </NavLink>

          <button
            className={styles.primaryButton}
            type="submit"
            disabled={isLoading || isSubmitting}
          >
            {isSubmitting ? 'Создаём и публикуем…' : 'Создать и опубликовать'}
          </button>
        </div>
      </form>
    </section>
  )
}