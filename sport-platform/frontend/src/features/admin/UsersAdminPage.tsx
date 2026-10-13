import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { adminError, roleLabel } from './admin-utils'
import { assignRole, changePassword, createUser, getRoles, getUsers, getUserDetails, revokeRole, setUserStatus,
  type AdminUser, type Role, type UserPage } from '../../api/admin.api'
import { AdminDialog } from './AdminDialog'
import styles from './Admin.module.css'

type Action = { type: 'roles' | 'password' | 'status'; user: AdminUser } | { type: 'create' }

export function UsersAdminPage() {
  const { session } = useAuth()
  const token = session?.accessToken ?? ''
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<UserPage | null>(null)
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [action, setAction] = useState<Action | null>(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [success, setSuccess] = useState('')
  const [roleReady, setRoleReady] = useState(false)
  const [scopedRoles, setScopedRoles] = useState<string[]>([])

  useEffect(() => {
    const timer = setTimeout(() => { setSearch(query.trim()); setPage(0) }, 300)
    return () => clearTimeout(timer)
  }, [query])
  useEffect(() => {
    let active = true
    getRoles(token).then(value => { if (active) setRoles(value) }).catch(err => { if (active) setError(adminError(err)) })
    return () => { active = false }
  }, [token, reload])
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    getUsers(token, search, role, page, controller.signal).then(value => {
      if (!controller.signal.aborted) { setData(value); setLoading(false) }
    }).catch(err => { if (!controller.signal.aborted) { setError(adminError(err)); setLoading(false) } })
    return () => controller.abort()
  }, [token, search, role, page, reload])

  function open(next: Action) {
    setAction(next); setFormError(''); setPassword(''); setConfirmation(''); setConfirmed(false); setShowPassword(false)
    setSelected(next.type === 'roles' ? [...next.user.roles] : ['participant'])
    setSuccess(''); setScopedRoles([]); setRoleReady(false)
    if (next.type === 'roles') {
      setBusy(true)
      getUserDetails(token, next.user.id).then(details => {
        const global = details.roles.filter(item => !item.organizationId && !item.eventId).map(item => item.code)
        setSelected(global); setAction({ type: 'roles', user: { ...next.user, roles: global } }); setRoleReady(true)
        setScopedRoles(details.roles.filter(item => item.organizationId || item.eventId).map(item => roleLabel(item.code, item.name)))
      }).catch(err => setFormError(adminError(err))).finally(() => setBusy(false))
    }
  }
  function toggle(code: string) {
    setSelected(previous => previous.includes(code) ? previous.filter(value => value !== code) : [...previous, code])
  }
  const removesAdmin = action?.type === 'roles' && action.user.roles.includes('platform_admin') && !selected.includes('platform_admin')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!action || busy || (action.type === 'roles' && !roleReady)) return
    if ((action.type === 'password' || action.type === 'create') && (password.length < 8 || password !== confirmation)) {
      setFormError('Пароль должен содержать не менее 8 символов. Пароли должны совпадать.'); return
    }
    if (removesAdmin && !confirmed) { setFormError('Подтвердите снятие роли администратора платформы.'); return }
    const values = new FormData(event.currentTarget)
    setBusy(true); setFormError('')
    try {
      if (action.type === 'create') {
        await createUser(token, { email: String(values.get('email')).trim(), firstName: String(values.get('firstName')).trim(),
          lastName: String(values.get('lastName')).trim(), birthDate: String(values.get('birthDate')), password, roles: selected })
      } else if (action.type === 'password') {
        await changePassword(token, action.user.id, password)
      } else if (action.type === 'status') {
        await setUserStatus(token, action.user.id, action.user.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE')
      } else {
        // Apply each successful mutation locally so retrying after a partial failure remains safe.
        const current = new Set(action.user.roles)
        for (const code of selected.filter(code => !current.has(code))) {
          await assignRole(token, action.user.id, code); current.add(code)
          setAction({ type: 'roles', user: { ...action.user, roles: [...current] } })
        }
        for (const code of [...current].filter(code => !selected.includes(code))) {
          await revokeRole(token, action.user.id, code); current.delete(code)
          setAction({ type: 'roles', user: { ...action.user, roles: [...current] } })
        }
      }
      setAction(null); setSuccess('Изменения сохранены.'); setReload(value => value + 1)
    } catch (err) { setFormError(adminError(err)) }
    finally { setBusy(false) }
  }

  const roleCheckboxes = <fieldset disabled={busy || !roles.length || (action?.type === 'roles' && !roleReady)}><legend>Роли</legend><div className={styles.checks}>
    {roles.map(item => <label key={item.code}><input type="checkbox" checked={selected.includes(item.code)}
      onChange={() => toggle(item.code)} />{roleLabel(item.code, item.name)}</label>)}
  </div></fieldset>
  const passwordFields = <>
    <label>Новый пароль<input autoComplete="new-password" type={showPassword ? 'text' : 'password'} minLength={8} maxLength={72}
      required value={password} onChange={event => setPassword(event.target.value)} /></label>
    <label>Повторите пароль<input autoComplete="new-password" type={showPassword ? 'text' : 'password'} required
      value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
    <label><span><input type="checkbox" checked={showPassword} onChange={event => setShowPassword(event.target.checked)} /> Показать пароль</span></label>
  </>
  return <section className={styles.page}>
    <div className={styles.heading}><h1>Пользователи</h1><button type="button" disabled={!roles.length} onClick={() => open({ type: 'create' })}>Создать пользователя</button></div>
    <div className={styles.toolbar}>
      <label>Поиск пользователей<input placeholder="Email, фамилия или имя" value={query} onChange={event => setQuery(event.target.value)} /></label>
      <label>Фильтр по роли<select aria-label="Фильтр по роли" value={role} onChange={event => { setRole(event.target.value); setPage(0) }}><option value="">Все роли</option>
        {roles.map(item => <option key={item.code} value={item.code}>{roleLabel(item.code, item.name)}</option>)}</select></label>
    </div>
    {success && <p role="status">{success}</p>}
    {error && <p className={styles.error} role="alert">{error} <button onClick={() => setReload(value => value + 1)}>Повторить</button></p>}
    {loading ? <p role="status">Загружаем пользователей…</p> : !error && data && <>
      {!data.items.length ? <p className="fl-empty">Пользователи не найдены — измените запрос</p> : <table className={styles.table}>
        <thead><tr><th>Email</th><th>ФИО</th><th>Роли</th><th>Статус / создан</th><th>Действия</th></tr></thead>
        <tbody>{data.items.map(user => <tr key={user.id}>
          <td>{user.email}</td><td>{user.lastName} {user.firstName}</td>
          <td>{user.roles.map(code => <span className={styles.badge} key={code}>{roleLabel(code, roles.find(item => item.code === code)?.name)}</span>)}</td>
          <td>{user.status === 'ACTIVE' ? 'Активен' : user.status === 'INACTIVE' ? 'Удалён' : 'Заблокирован'}<br />{new Date(user.createdAt).toLocaleDateString('ru-RU')}</td>
          <td><div className={styles.actions}><button onClick={() => open({ type: 'roles', user })}>Роли</button>
            {user.status !== 'INACTIVE' && <button onClick={() => open({ type: 'status', user })}>{user.status === 'ACTIVE' ? 'Заблокировать' : 'Разблокировать'}</button>}
            <button onClick={() => open({ type: 'password', user })}>Сменить пароль</button></div></td>
        </tr>)}</tbody></table>}
      <div className={styles.pagination}><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Назад</button>
        <span>Страница {page + 1} · Всего {data.total}</span><button disabled={(page + 1) * data.size >= data.total} onClick={() => setPage(value => value + 1)}>Далее</button></div>
    </>}
    {action && <AdminDialog title={action.type === 'create' ? 'Создать пользователя' : action.type === 'roles' ? 'Роли пользователя' : action.type === 'password' ? 'Сменить пароль' : 'Изменить статус'} busy={busy} onClose={() => setAction(null)}>
      <form className={styles.form} onSubmit={submit}><fieldset disabled={busy} className={styles.form}>
        {action.type !== 'create' && <p>{action.user.email}</p>}
        {action.type === 'create' && <>
          <label>Email<input name="email" type="email" autoComplete="off" required /></label>
          <label>Имя<input name="firstName" maxLength={128} required /></label>
          <label>Фамилия<input name="lastName" maxLength={128} required /></label>
          <label>Дата рождения<input name="birthDate" type="date" required /></label>
          {passwordFields}{roleCheckboxes}
        </>}
        {action.type === 'password' && passwordFields}
        {action.type === 'roles' && <><p>Глобальные роли пользователя</p>{roleCheckboxes}
          {scopedRoles.length > 0 && <p>Роли в отдельных организациях или событиях: {scopedRoles.join(', ')}. Они сохраняются при изменении глобальных ролей.</p>}
        </>}
        {removesAdmin && <label><span><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} /> Подтверждаю снятие роли администратора платформы</span></label>}
        {action.type === 'status' && <p>{action.user.status === 'ACTIVE' ? 'Заблокировать пользователя? Вход и существующие токены станут недоступны.' : 'Разблокировать пользователя?'}</p>}
      </fieldset>
        {formError && <p className={styles.error} role="alert">{formError}</p>}
        <button type="submit" disabled={busy || (action.type === 'roles' && !roleReady)}>{busy ? 'Сохраняем…' : action.type === 'status' ? 'Подтвердить' : 'Сохранить'}</button>
      </form>
    </AdminDialog>}
  </section>
}
