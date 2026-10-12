import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { register } from '../../api/auth.api'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './RegisterPage.module.css'

type RegisterLocationState = {
  from?: string
}

function getRegisterErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return 'Пользователь с таким email уже зарегистрирован.'
    }

    if (error.status === 400) {
      return 'Проверьте правильность заполнения полей.'
    }

    return error.message
  }

  return 'Не удалось создать аккаунт. Попробуйте ещё раз.'
}

export function RegisterPage() {
  const { isAuthenticated, login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [middleName, setMiddleName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [city, setCity] = useState('')
  const [clubName, setClubName] = useState('')
  const [agreeToTerms, setAgreeToTerms] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const state = location.state as RegisterLocationState | null
  const returnTo = state?.from ?? '/my/registrations'

  if (isAuthenticated) {
    return <Navigate replace to={returnTo} />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (isSubmitting) {
      return
    }

    setErrorMessage(null)

    if (password !== passwordConfirmation) {
      setErrorMessage('Пароль и его подтверждение не совпадают.')
      return
    }

    if (!agreeToTerms) {
      setErrorMessage(
        'Необходимо согласиться с правилами и обработкой персональных данных.',
      )
      return
    }

    setIsSubmitting(true)

    try {
      const normalizedEmail = email.trim().toLowerCase()

      await register({
        email: normalizedEmail,
        password,
        phone: phone.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        middleName: middleName.trim() || null,
        birthDate,
        gender: null,
        city: city.trim() || null,
        countryCode: 'RU',
        clubName: clubName.trim() || null,
      })

      await login({
        email: normalizedEmail,
        password,
      })

      navigate(returnTo, { replace: true })
    } catch (error) {
      setErrorMessage(getRegisterErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/">
        ← К событиям
      </Link>

      <div className={styles.card}>
        <p className={styles.eyebrow}>Личный кабинет</p>

        <h1 className={styles.title}>Создать аккаунт</h1>

        <p className={styles.description}>
          Зарегистрируйтесь как участник, чтобы подавать заявки на дисциплины,
          оплачивать стартовые взносы и отслеживать статус регистрации.
        </p>

        <form className={styles.form} onSubmit={handleSubmit}>
          <fieldset className={styles.fieldset} disabled={isSubmitting}>
            <legend className={styles.legend}>Данные участника</legend>

            <div className={styles.twoColumns}>
              <label className={styles.field}>
                <span>Имя</span>
                <input
                  autoComplete="given-name"
                  maxLength={120}
                  onChange={(event) => setFirstName(event.target.value)}
                  required
                  value={firstName}
                />
              </label>

              <label className={styles.field}>
                <span>Фамилия</span>
                <input
                  autoComplete="family-name"
                  maxLength={120}
                  onChange={(event) => setLastName(event.target.value)}
                  required
                  value={lastName}
                />
              </label>
            </div>

            <label className={styles.field}>
              <span>Отчество</span>
              <input
                autoComplete="additional-name"
                maxLength={120}
                onChange={(event) => setMiddleName(event.target.value)}
                value={middleName}
              />
            </label>

            <label className={styles.field}>
              <span>Дата рождения</span>
              <input
                max={new Date().toISOString().slice(0, 10)}
                onChange={(event) => setBirthDate(event.target.value)}
                required
                type="date"
                value={birthDate}
              />
            </label>
          </fieldset>

          <fieldset className={styles.fieldset} disabled={isSubmitting}>
            <legend className={styles.legend}>Контакты и вход</legend>

            <label className={styles.field}>
              <span>Email</span>
              <input
                autoComplete="email"
                inputMode="email"
                maxLength={255}
                onChange={(event) => setEmail(event.target.value)}
                required
                type="email"
                value={email}
              />
            </label>

            <label className={styles.field}>
              <span>Телефон</span>
              <input
                autoComplete="tel"
                inputMode="tel"
                maxLength={32}
                onChange={(event) => setPhone(event.target.value)}
                required
                type="tel"
                value={phone}
              />
            </label>

            <div className={styles.twoColumns}>
              <label className={styles.field}>
                <span>Пароль</span>
                <input
                  autoComplete="new-password"
                  minLength={8}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  type="password"
                  value={password}
                />
              </label>

              <label className={styles.field}>
                <span>Повторите пароль</span>
                <input
                  autoComplete="new-password"
                  minLength={8}
                  onChange={(event) =>
                    setPasswordConfirmation(event.target.value)
                  }
                  required
                  type="password"
                  value={passwordConfirmation}
                />
              </label>
            </div>
          </fieldset>

          <fieldset className={styles.fieldset} disabled={isSubmitting}>
            <legend className={styles.legend}>Дополнительно</legend>

            <div className={styles.twoColumns}>
              <label className={styles.field}>
                <span>Город</span>
                <input
                  autoComplete="address-level2"
                  maxLength={120}
                  onChange={(event) => setCity(event.target.value)}
                  value={city}
                />
              </label>

              <label className={styles.field}>
                <span>Клуб</span>
                <input
                  maxLength={180}
                  onChange={(event) => setClubName(event.target.value)}
                  value={clubName}
                />
              </label>
            </div>
          </fieldset>

          <label className={styles.checkboxField}>
            <input
              checked={agreeToTerms}
              disabled={isSubmitting}
              onChange={(event) => setAgreeToTerms(event.target.checked)}
              required
              type="checkbox"
            />

            <span>
              Я согласен(на) с правилами платформы и обработкой персональных
              данных.
            </span>
          </label>

          {errorMessage ? (
            <p className={styles.errorMessage} role="alert">
              {errorMessage}
            </p>
          ) : null}

          <button
            className={styles.submitButton}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Создаём аккаунт…' : 'Создать аккаунт'}
          </button>
        </form>

        <p className={styles.loginPrompt}>
          Уже есть аккаунт?{' '}
          <Link
            to="/login"
            state={{
              from: returnTo,
            }}
          >
            Войти
          </Link>
        </p>
      </div>
    </section>
  )
}