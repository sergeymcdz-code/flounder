import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router'
import { apiFetch, ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'
import styles from './LoginPage.module.css'

export function PasswordPage({ mode }: { mode: 'forgot' | 'reset' | 'change' }) {
  const { session, logout } = useAuth()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [email, setEmail] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [busy, setBusy] = useState(false)
  const title = mode === 'forgot' ? 'Восстановить пароль' : mode === 'reset' ? 'Новый пароль' : 'Сменить пароль'

  if (mode === 'change' && !session && !success) return <Navigate to="/login" state={{ from: '/account/password' }} replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (mode !== 'forgot') {
      if ([...password].length < 8 || !/\p{L}/u.test(password) || !/\p{Nd}/u.test(password)
          || new TextEncoder().encode(password).length > 72) {
        setError('Пароль должен содержать не менее 8 символов, буквы и цифры, и занимать не более 72 байт.')
        return
      }
      if (password !== confirmation) { setError('Пароли не совпадают.'); return }
    }
    setBusy(true)
    try {
      const path = mode === 'forgot' ? 'password-reset/request' : mode === 'reset' ? 'password-reset/confirm' : 'change-password'
      const body = mode === 'forgot' ? { email: email.trim() } : mode === 'reset' ? { token, newPassword: password } : { currentPassword, newPassword: password }
      await apiFetch(`/api/v1/auth/${path}`, {
        method: 'POST', body: JSON.stringify(body), accessToken: mode === 'change' ? session?.accessToken : undefined,
      })
      setSuccess(true)
      setCurrentPassword(''); setPassword(''); setConfirmation('')
      if (mode === 'change') {
        // Password change already revoked the current refresh on the backend.
        await logout().catch(() => {})
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.')
    } finally { setBusy(false) }
  }

  return <section className={styles.page}>
    <Link className={styles.backLink} to="/login">← Войти</Link>
    <div className={styles.card}>
      <h1 className={styles.title}>{title}</h1>
      {success ? <p role="status">{mode === 'forgot'
        ? 'Если аккаунт с этим email существует, мы отправили ссылку восстановления.'
        : mode === 'change' ? 'Пароль изменён. Войдите заново с новым паролем'
        : 'Пароль изменён. При следующем входе используйте новый пароль.'}</p>
        : mode === 'reset' && !token ? <p role="alert">В ссылке отсутствует токен. <Link to="/forgot-password">Запросить новую ссылку</Link></p>
        : <form className={`${styles.form} fl-form`} onSubmit={submit}>
          {mode === 'forgot' ? <label className="fl-field">Email
            <input type="email" autoComplete="email" required disabled={busy} value={email} onChange={event => setEmail(event.target.value)} />
          </label> : <>
            {mode === 'change' ? <label className="fl-field">Текущий пароль
              <input type="password" autoComplete="current-password" required disabled={busy} value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} />
            </label> : null}
            <p>Не менее 8 символов, буквы и цифры.</p>
            <label className="fl-field">Новый пароль
              <input type="password" autoComplete="new-password" required disabled={busy} value={password} onChange={event => setPassword(event.target.value)} />
            </label>
            <label className="fl-field">Повторите пароль
              <input type="password" autoComplete="new-password" required disabled={busy} value={confirmation} onChange={event => setConfirmation(event.target.value)} />
            </label>
          </>}
          {error ? <p role="alert" className={styles.errorMessage}>{error}</p> : null}
          <button className="fl-btn fl-btn-primary" disabled={busy} type="submit">{busy ? 'Отправляем…' : mode === 'forgot' ? 'Отправить ссылку' : 'Сохранить пароль'}</button>
        </form>}
    </div>
  </section>
}
