import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { publicApi, PublicApiError } from '../../api/public.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import type { RegistrationRequest } from './public.types'
import styles from './Public.module.css'
import { eligible, maskSeedTime, parseSeedTime } from './registrationFields'

export function PublicRegistrationPage() {
  const event = usePublicEvent()
  const auth = useAuth()
  const load = useCallback(
    (signal: AbortSignal) =>
      publicApi.registrationForm(event.publicSlug, signal),
    [event.publicSlug],
  )
  const state = usePublicData(load)
  const [profile, setProfile] = useState<RegistrationRequest['profile']>({
    lastName: '',
    firstName: '',
    middleName: '',
    birthDate: '',
    gender: 'M',
    club: '',
  })
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [showAll, setShowAll] = useState(false)
  const [consent, setConsent] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string[]>()
  const returnTo = `/e/${encodeURIComponent(event.publicSlug)}/register`
  if (!event.registrationOpen)
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
          Заявка принята сервером. Статус каждой дисциплины доступен в личном
          кабинете.
        </p>
        <ul>
          {success.map((name) => (
            <li key={name}>{name} — отправлена</li>
          ))}
        </ul>
        <Link className={`${styles.button} fl-button-ghost`} to="/my/registrations">
          Мои заявки
        </Link>
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
    if (!consent)
      nextErrors.form = 'Необходимо согласие на обработку персональных данных.'
    if (!disciplines.length)
      nextErrors.form = 'Выберите хотя бы один вид программы.'
    if (disciplines.length > 3)
      nextErrors.form = 'Выберите не больше трёх видов программы.'
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
          mapped[discipline?.id ?? violation.field] = violation.message
        }
      setErrors(mapped)
    } finally {
      setSubmitting(false)
    }
  }
  return (
    <>
      <h1>Заявка на участие</h1>
      <DataState {...state} />
      {state.data && (
        <form className={styles.form} onSubmit={submit}>
          <fieldset disabled={submitting}>
            <legend>Данные спортсмена</legend>
            {[
              ['lastName', 'Фамилия', 'family-name'],
              ['firstName', 'Имя', 'given-name'],
              ['middleName', 'Отчество', 'additional-name'],
            ].map(([key, title, autoComplete]) => (
              <label className={styles.field} key={key}>
                {title}
                <input
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
              </label>
            ))}
            <label className={styles.field}>
              Дата рождения
              <input
                type="date"
                required
                max={new Date().toISOString().slice(0, 10)}
                value={profile.birthDate}
                onChange={(e) => updateProfile('birthDate', e.target.value)}
              />
            </label>
            <label className={styles.field}>
              Пол
              <select
                value={profile.gender}
                onChange={(e) => updateProfile('gender', e.target.value)}
              >
                <option value="M">Мужской</option>
                <option value="F">Женский</option>
              </select>
            </label>
            <label className={styles.field}>
              Клуб / город
              <input
                maxLength={180}
                value={profile.club}
                onChange={(e) => updateProfile('club', e.target.value)}
              />
            </label>
          </fieldset>
          <fieldset disabled={submitting}>
            <legend>Виды программы</legend>
            <p>Укажите пол и дату рождения, чтобы увидеть доступные виды.</p>
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
                  showAll ||
                  eligible(item, profile) ||
                  Object.hasOwn(selected, item.id),
              )
              .map((item) => {
                const allowed = eligible(item, profile)
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
                      {item.name} ·{' '}
                      {item.entryFee === 0 ? 'Бесплатно' : `${item.entryFee} ₽`}
                    </label>
                    {!allowed && (
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
          <label className={styles.check}>
            <input
              type="checkbox"
              required
              checked={consent}
              disabled={submitting}
              onChange={(e) => setConsent(e.target.checked)}
            />
            Я даю согласие на обработку персональных данных (демо)
          </label>
          {Object.entries(errors)
            .filter(
              ([key]) =>
                !state.data?.disciplines.some((item) => item.id === key),
            )
            .map(([key, message]) => (
              <p key={key} role="alert" className={styles.error}>
                {message}
              </p>
            ))}
          <button className="fl-button-primary" disabled={submitting} type="submit">
            {submitting ? 'Отправляем…' : 'Отправить заявку'}
          </button>
        </form>
      )}
    </>
  )
}
