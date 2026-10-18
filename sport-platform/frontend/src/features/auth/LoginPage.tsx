import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { authReturnTarget } from '../../utils/authReturn'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './LoginPage.module.css'

type LoginLocationState = {
  from?: string
}

export function LoginPage() {
  const { isAuthenticated, login, roles } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const state = location.state as LoginLocationState | null
  const returnTo = authReturnTarget(location.search, state?.from, roles)

  if (isAuthenticated) {
    return <Navigate to={returnTo} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      const session = await login({
        email: email.trim(),
        password,
      })

      navigate(authReturnTarget(location.search, state?.from, session?.roles ?? roles), { replace: true })
    } catch (error) {
      let message = 'Не удалось выполнить вход. Попробуйте ещё раз.'
      if (error instanceof ApiError) {
        if (error.status === 403 && error.code === 'identity.user_blocked') {
          message = 'Аккаунт заблокирован, обратитесь к администратору'
        } else if (error.status === 429) {
          message = error.retryAfter !== undefined
            ? `Слишком много попыток входа. Попробуйте через ${Math.max(1, Math.ceil(error.retryAfter / 60))} мин.`
            : 'Слишком много попыток входа. Попробуйте позже'
        } else if (error.status === 401) {
          message = 'Проверьте email и пароль.'
        } else {
          message = error.message
        }
      }

      setErrorMessage(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={styles.page}>
      <Link className={styles.backLink} to="/">
        ← К соревнованиям
      </Link>

      <div className={styles.card}>
        <p className={styles.eyebrow}>Личный кабинет</p>

        <h1 className={styles.title}>Войти</h1>

        <p className={styles.description}>
          Войдите в кабинет организатора, секретаря, судьи или участника
        </p>

        <form className={`${styles.form} fl-form`} onSubmit={handleSubmit}>
          <label className="fl-field">
            <span>Email</span>
            <input
              autoComplete="email"
              disabled={isSubmitting}
              name="email"
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </label>

          <label className="fl-field">
            <span>Пароль</span>
            <input
              autoComplete="current-password"
              disabled={isSubmitting}
              name="password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>

          {errorMessage ? (
            <p className={styles.errorMessage} role="alert">
              {errorMessage}
            </p>
          ) : null}

          <button
            className="fl-btn fl-btn-primary"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Входим…' : 'Войти'}
          </button>
        </form>
        <p><Link to="/forgot-password">Забыли пароль?</Link></p>
        <p className={styles.registerPrompt}>
          Ещё нет аккаунта?{' '}
          <Link to="/register" state={{ from: returnTo }}>
            Создать аккаунт
          </Link>
        </p>
      </div>
    </section>
  )
}
