import { useEffect, useState, type FormEvent } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router'

import {
  createEvent,
  type CreateEventRequest,
} from '../../api/events.api'
import { ApiError } from '../../api/http'
import {
  getOrganizations,
  type OrganizationResponse,
} from '../../api/organizations.api'
import {
  getRegulationVersions,
  type RegulationVersionResponse,
} from '../../api/regulations.api'
import {
  getSports,
  type SportResponse,
} from '../../api/sports.api'
import {
  getVenues,
  type VenueResponse,
} from '../../api/venues.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './CreateEventPage.module.css'

type FormState = {
  title: string
  description: string
  registrationOpenAt: string
  registrationCloseAt: string
  eventStartAt: string
  eventEndAt: string
  organizationId: string
  venueId: string
  sportId: string
  regulationVersionId: string
}

type FormErrors = Partial<Record<keyof FormState, string>>

const initialFormState: FormState = {
  title: '',
  description: '',
  registrationOpenAt: '',
  registrationCloseAt: '',
  eventStartAt: '',
  eventEndAt: '',
  organizationId: '',
  venueId: '',
  sportId: '',
  regulationVersionId: '',
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Сессия завершена. Войдите в систему повторно.'
    }

    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на создание мероприятий.'
    }

    if (error.status === 400) {
      return error.message || 'Проверьте корректность заполненных данных.'
    }

    if (error.status === 409) {
      return error.message || 'Мероприятие с такими данными уже существует.'
    }

    return error.message || 'Не удалось выполнить запрос.'
  }

  if (error instanceof Error) {
    return error.message
  }

  return 'Не удалось создать мероприятие.'
}

function parseTime(value: string): number | null {
  if (!value.trim()) {
    return null
  }

  const timestamp = new Date(value).getTime()

  return Number.isNaN(timestamp) ? null : timestamp
}

function formatRegulationVersionLabel(
  regulationVersion: RegulationVersionResponse,
) {
  const effectiveFrom = regulationVersion.effectiveFrom
    ? new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'medium',
      }).format(new Date(regulationVersion.effectiveFrom))
    : 'дата не указана'

  return (
    `Версия ${regulationVersion.versionNo} · ` +
    `${regulationVersion.status} · с ${effectiveFrom}`
  )
}

function isPublishedRegulation(status: string) {
  return status.trim().toLowerCase() === 'published'
}

function validateForm(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.title.trim()) {
    errors.title = 'Укажите название мероприятия.'
  }

  if (!form.organizationId) {
    errors.organizationId = 'Выберите организацию.'
  }

  if (!form.venueId) {
    errors.venueId = 'Выберите площадку.'
  }

  if (!form.sportId) {
    errors.sportId = 'Выберите вид спорта.'
  }

  if (!form.regulationVersionId) {
    errors.regulationVersionId = 'Выберите версию регламента.'
  }

  if (!form.registrationOpenAt) {
    errors.registrationOpenAt =
      'Укажите дату открытия регистрации.'
  }

  if (!form.registrationCloseAt) {
    errors.registrationCloseAt =
      'Укажите дату закрытия регистрации.'
  }

  if (!form.eventStartAt) {
    errors.eventStartAt = 'Укажите дату начала события.'
  }

  if (!form.eventEndAt) {
    errors.eventEndAt = 'Укажите дату окончания события.'
  }

  const registrationOpenAt = parseTime(
    form.registrationOpenAt,
  )
  const registrationCloseAt = parseTime(
    form.registrationCloseAt,
  )
  const eventStartAt = parseTime(form.eventStartAt)
  const eventEndAt = parseTime(form.eventEndAt)

  if (
    form.registrationOpenAt &&
    registrationOpenAt === null
  ) {
    errors.registrationOpenAt =
      'Укажите корректную дату открытия регистрации.'
  }

  if (
    form.registrationCloseAt &&
    registrationCloseAt === null
  ) {
    errors.registrationCloseAt =
      'Укажите корректную дату закрытия регистрации.'
  }

  if (form.eventStartAt && eventStartAt === null) {
    errors.eventStartAt =
      'Укажите корректную дату начала события.'
  }

  if (form.eventEndAt && eventEndAt === null) {
    errors.eventEndAt =
      'Укажите корректную дату окончания события.'
  }

  if (
    registrationOpenAt !== null &&
    registrationCloseAt !== null &&
    registrationCloseAt < registrationOpenAt
  ) {
    errors.registrationCloseAt =
      'Закрытие регистрации не может быть раньше открытия.'
  }

  if (
    registrationCloseAt !== null &&
    eventStartAt !== null &&
    eventStartAt < registrationCloseAt
  ) {
    errors.eventStartAt =
      'Начало события не может быть раньше закрытия регистрации.'
  }

  if (
    eventStartAt !== null &&
    eventEndAt !== null &&
    eventEndAt < eventStartAt
  ) {
    errors.eventEndAt =
      'Окончание события не может быть раньше его начала.'
  }

  return errors
}

export function CreateEventPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { session } = useAuth()
  const accessToken = session?.accessToken

  const createdRegulationVersionId =
    (
      location.state as {
        createdRegulationVersionId?: string
      } | null
    )?.createdRegulationVersionId

  const [form, setForm] =
    useState<FormState>(initialFormState)
  const [errors, setErrors] = useState<FormErrors>({})
  const [errorMessage, setErrorMessage] = useState<
    string | null
  >(null)
  const [successMessage, setSuccessMessage] = useState<
    string | null
  >(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoadingOptions, setIsLoadingOptions] =
    useState(true)

  const [organizations, setOrganizations] = useState<
    OrganizationResponse[]
  >([])
  const [venues, setVenues] = useState<VenueResponse[]>([])
  const [sports, setSports] = useState<SportResponse[]>([])
  const [regulationVersions, setRegulationVersions] =
    useState<RegulationVersionResponse[]>([])

  useEffect(() => {
    if (!accessToken) {
      setIsLoadingOptions(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadOptions() {
      setIsLoadingOptions(true)
      setErrorMessage(null)

      try {
        const [
          loadedOrganizations,
          loadedVenues,
          loadedSports,
          loadedRegulationVersions,
        ] = await Promise.all([
          getOrganizations(authenticatedAccessToken),
          getVenues(authenticatedAccessToken),
          getSports(authenticatedAccessToken),
          getRegulationVersions(authenticatedAccessToken),
        ])

        if (!isMounted) {
          return
        }

        const publishedRegulationVersions = loadedRegulationVersions.filter(
          (regulationVersion) => isPublishedRegulation(regulationVersion.status),
        )

        setOrganizations(loadedOrganizations)
        setVenues(loadedVenues)
        setSports(loadedSports.filter((sport) => sport.isActive))
        setRegulationVersions(publishedRegulationVersions)

        if (
          createdRegulationVersionId &&
          publishedRegulationVersions.some(
            (version) => version.id === createdRegulationVersionId,
          )
        ) {
          setForm((current) => ({
            ...current,
            regulationVersionId: createdRegulationVersionId,
          }))
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (isMounted) {
          setIsLoadingOptions(false)
        }
      }
    }

    void loadOptions()

    return () => {
      isMounted = false
    }
  }, [accessToken, createdRegulationVersionId])

  function updateField<K extends keyof FormState>(
    field: K,
    value: FormState[K],
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))

    setErrors((current) => ({
      ...current,
      [field]: undefined,
    }))

    setErrorMessage(null)
    setSuccessMessage(null)
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const validationErrors = validateForm(form)

    setErrors(validationErrors)
    setErrorMessage(null)
    setSuccessMessage(null)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    if (!accessToken) {
      setErrorMessage(
        'Не удалось определить активную сессию.',
      )
      return
    }

    const payload: CreateEventRequest = {
      organizationId: form.organizationId,
      venueId: form.venueId,
      sportId: form.sportId,
      regulationVersionId: form.regulationVersionId,
      title: form.title.trim(),
      description: form.description.trim() || null,
      registrationOpenAt: new Date(
        form.registrationOpenAt,
      ).toISOString(),
      registrationCloseAt: new Date(
        form.registrationCloseAt,
      ).toISOString(),
      eventStartAt: new Date(
        form.eventStartAt,
      ).toISOString(),
      eventEndAt: new Date(
        form.eventEndAt,
      ).toISOString(),
    }

    setIsSubmitting(true)

    try {
      const created = await createEvent(
        payload,
        accessToken,
      )

      setSuccessMessage(
        `Мероприятие «${created.title}» успешно создано.`,
      )

      window.setTimeout(() => {
        navigate('/organizer/events', {
          replace: true,
          state: {
            createdEventId: created.id,
          },
        })
      }, 800)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page}>
      <NavLink
        className={styles.backLink}
        to="/organizer/events"
      >
        ← Назад к моим событиям
      </NavLink>

      <p className={styles.eyebrow}>
        Панель организатора
      </p>

      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>
            Создание мероприятия
          </h1>

          <p className={styles.description}>
            Заполните обязательные поля события. После
            создания вы сможете настроить дисциплины и
            опубликовать мероприятие.
          </p>
        </div>
      </div>

      <form
        className={styles.form}
        onSubmit={handleSubmit}
      >
        <article className={styles.card}>
          <h2 className={styles.cardTitle}>
            Основные данные
          </h2>

          <div className={styles.grid}>
            <label
              className={`${styles.field} ${styles.fieldFull}`}
            >
              <span className={styles.label}>
                Название
              </span>

              <input
                className={styles.input}
                value={form.title}
                maxLength={255}
                required
                disabled={isSubmitting}
                onChange={(event) =>
                  updateField(
                    'title',
                    event.target.value,
                  )
                }
                placeholder="Кубок Москвы по плаванию"
              />

              {errors.title ? (
                <span className={styles.fieldError}>
                  {errors.title}
                </span>
              ) : null}
            </label>

            <label
              className={`${styles.field} ${styles.fieldFull}`}
            >
              <span className={styles.label}>
                Описание
              </span>

              <textarea
                className={styles.textarea}
                rows={5}
                value={form.description}
                disabled={isSubmitting}
                onChange={(event) =>
                  updateField(
                    'description',
                    event.target.value,
                  )
                }
                placeholder="Описание события, формат участия, ключевая информация."
              />
            </label>
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>Даты</h2>

          <div className={styles.grid}>
            <label className={styles.field}>
              <span className={styles.label}>
                Открытие регистрации
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                value={form.registrationOpenAt}
                onChange={(event) =>
                  updateField(
                    'registrationOpenAt',
                    event.target.value,
                  )
                }
              />

              {errors.registrationOpenAt ? (
                <span className={styles.fieldError}>
                  {errors.registrationOpenAt}
                </span>
              ) : null}
            </label>

            <label className={styles.field}>
              <span className={styles.label}>
                Закрытие регистрации
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.registrationOpenAt || undefined}
                value={form.registrationCloseAt}
                onChange={(event) =>
                  updateField(
                    'registrationCloseAt',
                    event.target.value,
                  )
                }
              />

              {errors.registrationCloseAt ? (
                <span className={styles.fieldError}>
                  {errors.registrationCloseAt}
                </span>
              ) : null}
            </label>

            <label className={styles.field}>
              <span className={styles.label}>
                Начало события
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.registrationCloseAt || undefined}
                value={form.eventStartAt}
                onChange={(event) =>
                  updateField(
                    'eventStartAt',
                    event.target.value,
                  )
                }
              />

              {errors.eventStartAt ? (
                <span className={styles.fieldError}>
                  {errors.eventStartAt}
                </span>
              ) : null}
            </label>

            <label className={styles.field}>
              <span className={styles.label}>
                Окончание события
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.eventStartAt || undefined}
                value={form.eventEndAt}
                onChange={(event) =>
                  updateField(
                    'eventEndAt',
                    event.target.value,
                  )
                }
              />

              {errors.eventEndAt ? (
                <span className={styles.fieldError}>
                  {errors.eventEndAt}
                </span>
              ) : null}
            </label>
          </div>
        </article>

        <article className={styles.card}>
          <h2 className={styles.cardTitle}>
            Связанные сущности
          </h2>

          {isLoadingOptions ? (
            <p className={styles.description}>
              Загружаем доступные значения…
            </p>
          ) : (
            <div className={styles.grid}>
              <label className={styles.field}>
                <span className={styles.label}>
                  Организация
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.organizationId}
                  onChange={(event) =>
                    updateField(
                      'organizationId',
                      event.target.value,
                    )
                  }
                >
                  <option value="">
                    Выберите организацию
                  </option>

                  {organizations.map((organization) => (
                    <option
                      key={organization.id}
                      value={organization.id}
                    >
                      {organization.name}
                    </option>
                  ))}
                </select>

                {organizations.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Для вашей учётной записи нет доступных
                    организаций.
                  </p>
                ) : null}

                {errors.organizationId ? (
                  <span className={styles.fieldError}>
                    {errors.organizationId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <span className={styles.label}>
                  Площадка
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.venueId}
                  onChange={(event) =>
                    updateField(
                      'venueId',
                      event.target.value,
                    )
                  }
                >
                  <option value="">
                    Выберите площадку
                  </option>

                  {venues.map((venue) => (
                    <option
                      key={venue.id}
                      value={venue.id}
                    >
                      {venue.name} · {venue.city}
                    </option>
                  ))}
                </select>

                {venues.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Доступные площадки не найдены.
                  </p>
                ) : null}

                {errors.venueId ? (
                  <span className={styles.fieldError}>
                    {errors.venueId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <span className={styles.label}>
                  Вид спорта
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.sportId}
                  onChange={(event) =>
                    updateField(
                      'sportId',
                      event.target.value,
                    )
                  }
                >
                  <option value="">
                    Выберите вид спорта
                  </option>

                  {sports.map((sport) => (
                    <option
                      key={sport.id}
                      value={sport.id}
                    >
                      {sport.name}
                    </option>
                  ))}
                </select>

                {sports.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Активные виды спорта не найдены.
                  </p>
                ) : null}

                {errors.sportId ? (
                  <span className={styles.fieldError}>
                    {errors.sportId}
                  </span>
                ) : null}
              </label>

              <label className={styles.field}>
                <div className={styles.fieldHeader}>
                  <span className={styles.label}>
                    Версия регламента
                  </span>

                  <NavLink
                    className={styles.inlineAction}
                    to="/organizer/regulations/create"
                  >
                    Создать регламент
                  </NavLink>
                </div>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.regulationVersionId}
                  onChange={(event) =>
                    updateField(
                      'regulationVersionId',
                      event.target.value,
                    )
                  }
                >
                  <option value="">
                    Выберите версию регламента
                  </option>

                  {regulationVersions.map(
                    (regulationVersion) => (
                      <option
                        key={regulationVersion.id}
                        value={regulationVersion.id}
                      >
                        {formatRegulationVersionLabel(
                          regulationVersion,
                        )}
                      </option>
                    ),
                  )}
                </select>

                {regulationVersions.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Нет опубликованных регламентов.
                    Создайте и опубликуйте версию, чтобы
                    использовать её в мероприятии.
                  </p>
                ) : null}

                {errors.regulationVersionId ? (
                  <span className={styles.fieldError}>
                    {errors.regulationVersionId}
                  </span>
                ) : null}
              </label>
            </div>
          )}
        </article>

        {errorMessage ? (
          <p
            className={styles.errorMessage}
            role="alert"
          >
            {errorMessage}
          </p>
        ) : null}

        {successMessage ? (
          <p
            className={styles.successMessage}
            role="status"
          >
            {successMessage}
          </p>
        ) : null}

        <div className={styles.actions}>
          <NavLink
            className={styles.secondaryButton}
            to="/organizer/events"
          >
            Отмена
          </NavLink>

          <button
            className={styles.primaryButton}
            type="submit"
            disabled={
              isSubmitting ||
              isLoadingOptions ||
              organizations.length === 0 ||
              venues.length === 0 ||
              sports.length === 0 ||
              regulationVersions.length === 0
            }
          >
            {isSubmitting
              ? 'Создаём…'
              : 'Создать мероприятие'}
          </button>
        </div>
      </form>
    </section>
  )
}