import { apiFetch } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { publicApi, PublicApiError } from '../../api/public.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import type { RegistrationRequest, RegistrationDiscipline } from './public.types'
import styles from './Public.module.css'
import formStyles from './RegistrationPage.module.css'
import { eligible, maskSeedTime, parseSeedTime } from './registrationFields'

function isDisciplineFull(item: RegistrationDiscipline, profile: { birthDate: string; gender: string }) {
  if (item.maxParticipants != null && (item.participantsCount ?? 0) >= item.maxParticipants) return true
  const year = Number(profile.birthDate.slice(0, 4))
  const group = item.ageGroups.find(g => (g.gender === 'X' || g.gender === profile.gender)
    && (g.birthYearFrom == null || year >= g.birthYearFrom) && (g.birthYearTo == null || year <= g.birthYearTo))
  const limit = group ? item.limitsByAgeGroup?.[group.code] : undefined
  return group != null && limit != null && (item.participantsByAgeGroup?.[group.code] ?? 0) >= limit
}

export function PublicRegistrationPage() {
  const event = usePublicEvent()
  const auth = useAuth()
  const load = useCallback(
    async (signal: AbortSignal) => {
      if (auth.session) { const user = await apiFetch<{ email: string }>('/api/v1/auth/me', { accessToken: auth.session.accessToken, signal }).catch(() => null); if (user && !signal.aborted) setEmail(user.email) }
      return publicApi.registrationForm(event.publicSlug, signal)
    },
    [event.publicSlug, auth.session],
  )
  const state = usePublicData(load)
  const [profile, setProfile] = useState<Omit<RegistrationRequest['profile'], 'gender'> & { gender: '' | 'M' | 'F' }>({
    lastName: '',
    firstName: '',
    middleName: '',
    birthDate: '',
    gender: '',
    club: '',
  })
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [showAll, setShowAll] = useState(false)
  const [consent, setConsent] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [email, setEmail] = useState('')
  const [success, setSuccess] = useState<string[]>()
  const profileReady = !!profile.gender && !!profile.birthDate
  const today = new Date().toISOString().slice(0, 10)
  const returnTo = `/e/${encodeURIComponent(event.publicSlug)}/register`
  if (!event.registrationOpen || state.data?.registrationOpen === false)
    return (
      <>
        <h1>Заявка на участие</h1>
        <p>Регистрация закрыта.</p>
      </>
    )
  if (!auth.isAuthenticated)
    return (
      <section className={styles.card}>
        <h1>Войдите или зарегистрируйтесь</h1>
        <p>Для подачи заявки нужен аккаунт участника.</p>
        <div className={styles.actions}>
          <Link
            className={`${styles.button} fl-button-primary`}
            to={`/login?next=${encodeURIComponent(returnTo)}`}
            state={{ from: returnTo }}
          >
            Войти
          </Link>
          <Link
            className={`${styles.button} fl-button-ghost`}
            to={`/register?next=${encodeURIComponent(returnTo)}`}
            state={{ from: returnTo }}
          >
            Зарегистрироваться
          </Link>
        </div>
      </section>
    )
  if (!auth.isParticipant)
    return (
      <>
        <h1>Заявка на участие</h1>
        <p>Подача заявки доступна с аккаунта участника.</p>
      </>
    )
  if (success)
    return (
      <section className={styles.card} role="status">
        <h1>Заявка отправлена</h1>
        <p>
          Заявка отправлена. Организатор проверит её и подтвердит; письмо придёт на {email || 'ваш email'}. После подтверждения появится оплата.
        </p>
        <ul>
          {success.map((name) => (
            <li key={name}>{name} — отправлена</li>
          ))}
        </ul>
        <Link className={`${styles.button} fl-button-primary`} to="/my/registrations">
          Мои заявки
        </Link><Link className={`${styles.button} fl-button-ghost`} to={`/e/${event.publicSlug}`}>Вернуться к соревнованию</Link>
      </section>
    )
  function updateProfile(
    key: keyof RegistrationRequest['profile'],
    value: string,
  ) {
    setProfile((previous) => ({ ...previous, [key]: value }))
  }
  async function submit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()
    if (submitting || !state.data || !auth.session) return
    const disciplines = state.data.disciplines.filter((item) =>
      Object.hasOwn(selected, item.id),
    )
    const nextErrors: Record<string, string> = {}
    if (!profile.lastName.trim()) nextErrors.lastName = 'Укажите фамилию спортсмена.'
    if (!profile.firstName.trim()) nextErrors.firstName = 'Укажите имя спортсмена.'
    if (!profile.birthDate || profile.birthDate > today) nextErrors.birthDate = 'Укажите дату рождения не позднее сегодняшней.'
    if (!profile.gender) nextErrors.gender = 'Укажите пол спортсмена.'
    if (!consent)
      nextErrors.consent = 'Необходимо согласие на обработку персональных данных.'
    if (!disciplines.length)
      nextErrors.disciplines = 'Выберите хотя бы один вид программы.'
    if (disciplines.length > 3)
      nextErrors.disciplines = 'Выберите не больше трёх видов программы.'
    for (const item of disciplines) {
      if (!eligible(item, profile))
        nextErrors[item.id] = 'Вид не подходит по полу или году рождения.'
      else if (state.data.requiresSeedTime && parseSeedTime(selected[item.id]) === null)
        nextErrors[item.id] = 'Укажите заявочное время в формате мм:сс.сс.'
      else if (parseSeedTime(selected[item.id]) === undefined)
        nextErrors[item.id] =
          'Введите ненулевое время в формате мм:сс.сс (секунды 00–59).'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) return
    setSubmitting(true)
    try {
      await publicApi.register(
        event.publicSlug,
        {
          profile: {
            ...profile,
            gender: profile.gender === 'F' ? 'F' : 'M',
            lastName: profile.lastName.trim(),
            firstName: profile.firstName.trim(),
            middleName: profile.middleName.trim(),
            club: profile.club.trim(),
          },
          items: disciplines.map((item) => ({
            eventDisciplineId: item.id,
            seedTimeMs: parseSeedTime(selected[item.id]) ?? null,
          })),
        },
        auth.session.accessToken,
      )
      setSuccess(disciplines.map((item) => item.name))
    } catch (error) {
      const mapped: Record<string, string> = {
        form:
          error instanceof PublicApiError
            ? error.message
            : 'Не удалось отправить заявку. Проверьте подключение.',
      }
      if (error instanceof PublicApiError)
        for (const violation of error.violations) {
          const index = /items\[(\d+)\]/.exec(violation.field)?.[1]
          const discipline =
            index !== undefined
              ? disciplines[Number(index)]
              : disciplines.find((item) => violation.field.includes(item.id))
          mapped[discipline?.id ?? violation.field.replace(/^profile\./, '')] = violation.message
        }
      setErrors(mapped)
    } finally {
      setSubmitting(false)
    }
  }
  function fieldError(key: string) {
    return errors[key] && <p id={`registration-error-${key}`} className={styles.error} role="alert">{errors[key]}</p>
  }
  return (
    <section className={formStyles.card}>
      <h1>Заявка на участие</h1>
      <DataState {...state} />
      {state.data && state.data.maxParticipants != null && (state.data.participantsCount ?? 0) >= state.data.maxParticipants && <p role="status">Мест нет, запись в лист ожидания</p>}
      {state.data && (
        <form className={formStyles.form} onSubmit={submit} noValidate>
          <fieldset disabled={submitting}>
            <legend>Данные спортсмена</legend>
            <button type="button" onClick={() => { void (async () => {
              const cached = email && sessionStorage.getItem(`sportgate.profile.${email}`)
              if (cached) { setProfile(previous => ({ ...previous, ...JSON.parse(cached) as Partial<typeof profile> })); return }
              const rows = await getMyRegistrations(auth.session!.accessToken)
              const own = rows[0]?.participantSnapshot
              if (own) setProfile({ firstName: own.firstName, lastName: own.lastName, middleName: own.middleName ?? '', birthDate: own.birthDate ?? '', gender: own.gender === 'F' ? 'F' : own.gender === 'M' ? 'M' : profile.gender, club: own.clubName ?? '' })
              else setErrors(previous => ({ ...previous, form: 'В профиле пока нет данных спортсмена. Заполните их ниже.' }))
            })().catch(() => setErrors(previous => ({ ...previous, form: 'Не удалось загрузить профиль. Заполните данные ниже.' }))) }}>Это я</button>
            {[
              ['lastName', 'Фамилия', 'family-name'],
              ['firstName', 'Имя', 'given-name'],
              ['middleName', 'Отчество', 'additional-name'],
            ].map(([key, title, autoComplete]) => (
              <div className={styles.field} key={key}>
                <label htmlFor={`registration-${key}`}>{title}</label>
                <input
                  id={`registration-${key}`}
                  aria-invalid={!!errors[key]}
                  aria-describedby={errors[key] ? `registration-error-${key}` : undefined}
                  autoComplete={autoComplete}
                  maxLength={120}
                  required={key !== 'middleName'}
                  pattern={key !== 'middleName' ? '.*\\S.*' : undefined}
                  value={
                    profile[key as 'lastName' | 'firstName' | 'middleName']
                  }
                  onChange={(e) =>
                    updateProfile(key as keyof typeof profile, e.target.value)
                  }
                />
                {fieldError(key)}
              </div>
            ))}
            <div className={styles.field}>
              <label htmlFor="registration-birth-date">Дата рождения</label>
              <input id="registration-birth-date" type="date" required max={today}
                aria-invalid={!!errors.birthDate}
                aria-describedby={`registration-date-hint${errors.birthDate ? ' registration-error-birthDate' : ''}`}
                value={profile.birthDate} onChange={e => updateProfile('birthDate', e.target.value)} />
              <small id="registration-date-hint">Формат даты: ДД.ММ.ГГГГ</small>
              {fieldError('birthDate')}
            </div>
            <div className={formStyles.row}>
              <div className={styles.field}>
                <label htmlFor="registration-gender">Пол</label>
                <select id="registration-gender" required value={profile.gender}
                  aria-invalid={!!errors.gender}
                  aria-describedby={errors.gender ? 'registration-error-gender' : undefined}
                  onChange={e => updateProfile('gender', e.target.value)}>
                  <option value="">Выберите пол</option>
                  <option value="M">Мужской</option>
                  <option value="F">Женский</option>
                </select>
                {fieldError('gender')}
              </div>
              <div className={styles.field}>
                <label htmlFor="registration-club">Клуб / город</label>
                <input id="registration-club" maxLength={180} value={profile.club}
                  aria-invalid={!!errors.club}
                  aria-describedby={errors.club ? 'registration-error-club' : undefined}
                  onChange={e => updateProfile('club', e.target.value)} />
                {fieldError('club')}
              </div>
            </div>
          </fieldset>
          <fieldset disabled={submitting || !profileReady} aria-describedby="registration-disciplines-hint">
            <legend>Виды программы</legend>
            <p id="registration-disciplines-hint">{profileReady ? 'Можно выбрать до 3 видов.' : 'Укажите пол и дату рождения, чтобы увидеть доступные виды'}</p>
            {fieldError('disciplines')}
            <label className={styles.check}>
              <input
                type="checkbox"
                checked={showAll}
                onChange={(e) => setShowAll(e.target.checked)}
              />
              Показать все виды
            </label>
            {state.data.disciplines
              .filter(
                (item) =>
                  !profileReady || showAll ||
                  eligible(item, profile) ||
                  Object.hasOwn(selected, item.id),
              )
              .map((item) => {
                const allowed = profileReady && eligible(item, profile)
                const checked = Object.hasOwn(selected, item.id)
                return (
                  <div key={item.id} className={styles.discipline}>
                    <label className={styles.check}>
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!allowed && !checked}
                        onChange={(e) =>
                          setSelected((previous) => {
                            const next = { ...previous }
                            if (e.target.checked) next[item.id] = ''
                            else delete next[item.id]
                            return next
                          })
                        }
                      />
                      {item.name}{profileReady && isDisciplineFull(item, profile) ? ' · Мест нет, запись в лист ожидания' : ''} ·{' '}
                      {item.entryFee === 0 ? 'Бесплатно' : `${item.entryFee} ₽`}
                    </label>
                    {profileReady && !allowed && (
                      <small>Не подходит по полу или году рождения</small>
                    )}
                    {checked && (
                      <label className={styles.field}>
                        Заявочное время
                        <input
                          inputMode="numeric"
                          placeholder="мм:сс.сс"
                          required={state.data?.requiresSeedTime}
                          maxLength={8}
                          aria-describedby={`hint-${item.id}`}
                          aria-invalid={!!errors[item.id]}
                          value={selected[item.id]}
                          onChange={(e) =>
                            setSelected((previous) => ({
                              ...previous,
                              [item.id]: maskSeedTime(e.target.value),
                            }))
                          }
                        />
                        <small id={`hint-${item.id}`}>
                          {state.data?.requiresSeedTime ? 'Укажите заявочное время' : 'Можно оставить пустым'}
                        </small>
                      </label>
                    )}
                    {errors[item.id] && (
                      <p className={styles.error} role="alert">
                        {errors[item.id]}
                      </p>
                    )}
                  </div>
                )
              })}
          </fieldset>
          <fieldset disabled={submitting}>
            <legend>Согласие и отправка</legend>
            <label className={styles.check}>
              <input
                aria-invalid={!!errors.consent}
                aria-describedby={errors.consent ? 'registration-error-consent' : undefined}
                type="checkbox"
                required
                checked={consent}
                disabled={submitting}
                onChange={(e) => setConsent(e.target.checked)}
              />
              Я даю согласие на обработку персональных данных
            </label>
            {fieldError('consent')}
            {Object.entries(errors)
              .filter(
                ([key]) =>
                  !['lastName', 'firstName', 'middleName', 'birthDate', 'gender', 'club', 'consent', 'disciplines'].includes(key) && !state.data?.disciplines.some((item) => item.id === key),
              )
              .map(([key, message]) => (
                <p key={key} role="alert" className={styles.error}>
                  {message}
                </p>
              ))}
            <button className={`${formStyles.submit} fl-button-primary`} disabled={submitting} type="submit">
              {submitting ? 'Отправляем…' : 'Отправить заявку'}
            </button>
          </fieldset>
        </form>
      )}
    </section>
  )
}
