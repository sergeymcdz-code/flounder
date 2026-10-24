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
  maxParticipants: string
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

const regulationStatusLabels: Record<string, string> = {
  PUBLISHED: 'Опубликована', DRAFT: 'Черновик', ARCHIVED: 'Архив',
}

const initialFormState: FormState = {
  maxParticipants: '',
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
      return 'У вашей учётной записи нет прав на создание соревнований.'
    }

    if (error.status === 400) {
      return error.message || 'Проверьте корректность заполненных данных.'
    }

    if (error.status === 409) {
      return error.message || 'Соревнование с такими данными уже существует.'
    }

    return error.message || 'Не удалось выполнить запрос.'
  }

  if (error instanceof Error) {
    return error.message
  }

  return 'Не удалось создать соревнование.'
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
    `${regulationStatusLabels[regulationVersion.status.trim().toUpperCase()] ?? regulationVersion.status} · с ${effectiveFrom}`
  )
}

function isPublishedRegulation(status: string) {
  return status.trim().toLowerCase() === 'published'
}

function validateForm(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.title.trim()) {
    errors.title = 'Укажите название соревнования.'
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
    errors.eventStartAt = 'Укажите дату начала соревнования.'
  }

  if (!form.eventEndAt) {
    errors.eventEndAt = 'Укажите дату окончания соревнования.'
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
      'Укажите корректную дату начала соревнования.'
  }

  if (form.eventEndAt && eventEndAt === null) {
    errors.eventEndAt =
      'Укажите корректную дату окончания соревнования.'
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
      'Начало соревнования не может быть раньше закрытия регистрации.'
  }

  if (
    eventStartAt !== null &&
    eventEndAt !== null &&
    eventEndAt < eventStartAt
  ) {
    errors.eventEndAt =
      'Окончание соревнования не может быть раньше его начала.'
  }

  return errors
}

export function CreateEventPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { session, currentOrgId, memberships, isPlatformAdmin } = useAuth()
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

        const eligible = isPlatformAdmin ? loadedOrganizations : loadedOrganizations.filter(org => (memberships ?? []).some(m =>
          m.organizationId === org.id && ['owner', 'admin', 'organizer'].includes(m.memberRole)))
        setOrganizations(eligible)
        setForm(current => ({ ...current, organizationId: eligible.some(o => o.id === currentOrgId) ? currentOrgId : eligible[0]?.id ?? '' }))
        setVenues(loadedVenues)
        setSports(loadedSports.filter((sport) => sport.isActive))
        setRegulationVersions(publishedRegulationVersions)
        if (publishedRegulationVersions.length === 1) setForm(current => ({ ...current, regulationVersionId: current.regulationVersionId || publishedRegulationVersions[0].id }))

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
  }, [accessToken, createdRegulationVersionId, memberships, currentOrgId, isPlatformAdmin])

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
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
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
      maxParticipants: form.maxParticipants ? Number(form.maxParticipants) : null,
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
        `Соревнование «${created.title}» успешно создано.`,
      )

      navigate(`/organizer/events/${created.id}/program`, { replace: true, state: { createdEventId: created.id } })
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
        ← Назад к моим соревнованиям
      </NavLink>

      <p className={styles.eyebrow}>
        Панель организатора
      </p>

      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>
            Создать соревнование
          </h1>

          <p className={styles.description}>
            Заполните обязательные поля соревнования. После
            создания вы сможете настроить дисциплины и
            опубликовать соревнование.
          </p>
        </div>
      </div>

      <ol className="fl-wizard" aria-label="Этапы создания"><li aria-current="step">① Основное</li><li>② Регистрация</li><li>③ Программа — после создания</li></ol>
      <form
        className={`${styles.form} fl-form`}
        onSubmit={handleSubmit}
        onBlur={event => { const target = event.target; if (!(target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement)) return; const name = target.name as keyof FormState; if (name && name in form) setErrors(previous => ({ ...previous, [name]: validateForm(form)[name] })) }}
      >
        <fieldset className={styles.card}>
          <legend className={styles.cardTitle}>
            Основные данные
          </legend>

          <div className={styles.grid}>
            <label
              className={`fl-field ${styles.fieldFull}`}
            >
              <span className={styles.label}>
                Название
              </span>

              <input
                className={styles.input}
                value={form.title} name="title" aria-invalid={Boolean(errors.title)} aria-describedby={errors.title ? "create-title-error" : undefined}
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
                <span className={styles.fieldError} id="create-title-error">
                  {errors.title}
                </span>
              ) : null}
            </label>

            <label
              className={`fl-field ${styles.fieldFull}`}
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
                placeholder="Описание соревнования, формат участия, ключевая информация."
              />
            </label>
          <label>Лимит участников (необязательно)
            <input type="number" min="1" step="1" value={form.maxParticipants} placeholder="Без лимита"
              onChange={e => setForm(previous => ({ ...previous, maxParticipants: e.target.value }))} />
          </label>
          </div>
        </fieldset>

        <fieldset className={styles.card}>
          <legend className={styles.cardTitle}>Даты</legend>

          <div className={styles.grid}>
            <label className="fl-field">
              <span className={styles.label}>
                Открытие регистрации
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                value={form.registrationOpenAt} name="registrationOpenAt" aria-invalid={Boolean(errors.registrationOpenAt)} aria-describedby={errors.registrationOpenAt ? "create-registrationOpenAt-error" : undefined}
                onChange={(event) =>
                  updateField(
                    'registrationOpenAt',
                    event.target.value,
                  )
                }
              />

              {errors.registrationOpenAt ? (
                <span className={styles.fieldError} id="create-registrationOpenAt-error">
                  {errors.registrationOpenAt}
                </span>
              ) : null}
            </label>

            <label className="fl-field">
              <span className={styles.label}>
                Закрытие регистрации
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.registrationOpenAt || undefined}
                value={form.registrationCloseAt} name="registrationCloseAt" aria-invalid={Boolean(errors.registrationCloseAt)} aria-describedby={errors.registrationCloseAt ? "create-registrationCloseAt-error" : undefined}
                onChange={(event) =>
                  updateField(
                    'registrationCloseAt',
                    event.target.value,
                  )
                }
              />

              {errors.registrationCloseAt ? (
                <span className={styles.fieldError} id="create-registrationCloseAt-error">
                  {errors.registrationCloseAt}
                </span>
              ) : null}
            </label>

            <label className="fl-field">
              <span className={styles.label}>
                Начало соревнования
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.registrationCloseAt || undefined}
                value={form.eventStartAt} name="eventStartAt" aria-invalid={Boolean(errors.eventStartAt)} aria-describedby={errors.eventStartAt ? "create-eventStartAt-error" : undefined}
                onChange={(event) =>
                  updateField(
                    'eventStartAt',
                    event.target.value,
                  )
                }
              />

              {errors.eventStartAt ? (
                <span className={styles.fieldError} id="create-eventStartAt-error">
                  {errors.eventStartAt}
                </span>
              ) : null}
            </label>

            <label className="fl-field">
              <span className={styles.label}>
                Окончание соревнования
              </span>

              <input
                className={styles.input}
                type="datetime-local"
                required
                disabled={isSubmitting}
                min={form.eventStartAt || undefined}
                value={form.eventEndAt} name="eventEndAt" aria-invalid={Boolean(errors.eventEndAt)} aria-describedby={errors.eventEndAt ? "create-eventEndAt-error" : undefined}
                onChange={(event) =>
                  updateField(
                    'eventEndAt',
                    event.target.value,
                  )
                }
              />

              {errors.eventEndAt ? (
                <span className={styles.fieldError} id="create-eventEndAt-error">
                  {errors.eventEndAt}
                </span>
              ) : null}
            </label>
          </div>
        </fieldset>

        <fieldset className={styles.card}>
          <legend className={styles.cardTitle}>
            Связанные сущности
          </legend>

          {isLoadingOptions ? (
            <p className={styles.description}>
              Загружаем доступные значения…
            </p>
          ) : (
            <div className={styles.grid}>
              <label className="fl-field">
                <span className={styles.label}>
                  Организация
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.organizationId} name="organizationId" aria-invalid={Boolean(errors.organizationId)} aria-describedby={errors.organizationId ? "create-organizationId-error" : undefined}
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
                  <span className={styles.fieldError} id="create-organizationId-error">
                    {errors.organizationId}
                  </span>
                ) : null}
              </label>

              <label className="fl-field">
                <span className={styles.label}>
                  Площадка
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.venueId} name="venueId" aria-invalid={Boolean(errors.venueId)} aria-describedby={errors.venueId ? "create-venueId-error" : undefined}
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
                  <span className={styles.fieldError} id="create-venueId-error">
                    {errors.venueId}
                  </span>
                ) : null}
              </label>

              <label className="fl-field">
                <span className={styles.label}>
                  Вид спорта
                </span>

                <select
                  className={styles.input}
                  required
                  disabled={isSubmitting}
                  value={form.sportId} name="sportId" aria-invalid={Boolean(errors.sportId)} aria-describedby={errors.sportId ? "create-sportId-error" : undefined}
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
                  <span className={styles.fieldError} id="create-sportId-error">
                    {errors.sportId}
                  </span>
                ) : null}
              </label>

              <label className="fl-field">
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
                  value={form.regulationVersionId} name="regulationVersionId" aria-invalid={Boolean(errors.regulationVersionId)} aria-describedby={errors.regulationVersionId ? "create-regulationVersionId-error" : undefined}
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

                <p className={styles.fieldHint}>Положение можно прикрепить позже. Для создания нужна опубликованная версия регламента.</p>
                {regulationVersions.length === 0 ? (
                  <p className={styles.fieldHint}>
                    Нет опубликованных регламентов.
                    Создайте и опубликуйте версию, чтобы
                    использовать её в соревновании.
                  </p>
                ) : null}

                {errors.regulationVersionId ? (
                  <span className={styles.fieldError} id="create-regulationVersionId-error">
                    {errors.regulationVersionId}
                  </span>
                ) : null}
              </label>
            </div>
          )}
        </fieldset>

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
            className="fl-btn fl-btn-secondary"
            to="/organizer/events"
          >
            Отмена
          </NavLink>


          <button title={isSubmitting ? 'Дождитесь сохранения' : isLoadingOptions ? 'Загружаем справочники' :
              !organizations.length || !venues.length || !sports.length || !regulationVersions.length
                ? 'Нет доступных справочников: нужны организация, площадка, вид спорта и опубликованный регламент' : undefined}
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
              : 'Создать соревнование'}
          </button>
        </div>
      </form>
    </section>
  )
}
