import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './LoginPage.module.css'

type LoginLocationState = {
  from?: string
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const state = location.state as LoginLocationState | null
  const returnTo = state?.from ?? '/my/registrations'

  if (isAuthenticated) {
    return <Navigate to={returnTo} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      await login({
        email: email.trim(),
        password,
      })

      navigate(returnTo, { replace: true })
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'Проверьте email и пароль.'
          : error instanceof ApiError
            ? error.message
            : 'Не удалось выполнить вход. Попробуйте ещё раз.'

      setErrorMessage(message)
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

        <h1 className={styles.title}>Войти</h1>

        <p className={styles.description}>
          Войдите, чтобы подать заявку на дисциплину и посмотреть её статус.
        </p>

        <form className={styles.form} onSubmit={handleSubmit}>
          <label className={styles.field}>
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

          <label className={styles.field}>
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
            className={styles.submitButton}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Входим…' : 'Войти'}
          </button>
        </form>
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