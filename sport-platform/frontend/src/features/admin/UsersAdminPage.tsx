import { useEffect, useState, type FormEvent } from 'react'
import { OverflowMenu } from '../../components/ui/OverflowMenu'
import { ActionBar } from '../../components/ui/ActionBar'
import { Button } from '../../components/ui/Button'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { AdminNav } from './AdminNav'
import { useAuth } from '../../app/providers/AuthProvider'
import { adminError, roleLabel } from './admin-utils'
import { assignRole, changePassword, createUser, getRoles, getUsers, getUserDetails, revokeRole, setUserStatus,
  type AdminUser, type Role, type UserPage } from '../../api/admin.api'
import { OrganizerDialog } from '../organizer/OrganizerDialog'
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
  const [rolesLoading, setRolesLoading] = useState(true)
  const [rolesError, setRolesError] = useState('')
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
    setRolesLoading(true); setRolesError('')
    getRoles(token).then(value => { if (active) setRoles(value) }).catch(err => { if (active) setRolesError(adminError(err)) }).finally(() => { if (active) setRolesLoading(false) })
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

  const roleCheckboxes = <fieldset className="fl-fieldset" disabled={busy || !roles.length || (action?.type === 'roles' && !roleReady)}><legend>Роли</legend><div className={styles.checks}>
    {roles.map(item => <label key={item.code}><input type="checkbox" checked={selected.includes(item.code)}
      onChange={() => toggle(item.code)} />{roleLabel(item.code, item.name)}</label>)}
  </div></fieldset>
  const passwordFields = <>
    <label className="fl-field">Новый пароль<input autoComplete="new-password" type={showPassword ? 'text' : 'password'} minLength={8} maxLength={72}
      required value={password} onChange={event => setPassword(event.target.value)} /></label>
    <label className="fl-field">Повторите пароль<input autoComplete="new-password" type={showPassword ? 'text' : 'password'} required
      value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
    <label className={styles.check}><span><input type="checkbox" checked={showPassword} onChange={event => setShowPassword(event.target.checked)} /> Показать пароль</span></label>
  </>
  const columns: DataTableColumn<AdminUser>[] = [
    { key: 'email', header: 'Email', rowHeader: true, render: user => user.email },
    { key: 'name', header: 'ФИО', render: user => [user.lastName, user.firstName].filter(Boolean).join(' ') || 'Имя не указано' },
    { key: 'roles', header: 'Роли', render: user => <div className={styles.badges}>{user.roles.map(code => <span className="fl-badge fl-badge-neutral" key={code}>{roleLabel(code, roles.find(item => item.code === code)?.name)}</span>)}</div> },
    { key: 'status', header: 'Статус', render: user => <span className={`fl-badge fl-badge-${user.status === 'ACTIVE' ? 'success' : user.status === 'INACTIVE' ? 'neutral' : 'danger'}`}>{user.status === 'ACTIVE' ? 'Активен' : user.status === 'INACTIVE' ? 'Удалён' : 'Заблокирован'}</span> },
    { key: 'created', header: 'Создан', numeric: true, render: user => new Date(user.createdAt).toLocaleDateString('ru-RU') },
    { key: 'actions', header: 'Действия', render: user => <div className={styles.actions}>
      <Button onClick={() => open({ type: 'roles', user })}>Роли</Button>
      <OverflowMenu label={`Действия пользователя ${user.email}`} items={[
        ...(user.status !== 'INACTIVE' ? [{ label: user.status === 'ACTIVE' ? 'Заблокировать' : 'Разблокировать', onSelect: () => open({ type: 'status', user }) }] : []),
        { label: 'Сменить пароль', onSelect: () => open({ type: 'password', user }) },
      ]} />
    </div> },
  ]
  return <section className={styles.page}>
    <PageHeader title="Пользователи" actions={<Button variant="primary" title={!roles.length ? 'Нет доступного справочника ролей' : undefined} disabled={!roles.length} onClick={() => open({ type: 'create' })}>Создать пользователя</Button>} />
    {!roles.length && (rolesError || !rolesLoading) && <p className="fl-meta">Создание пользователя станет доступно после загрузки справочника ролей.</p>}
    <AdminNav />
    <div className={styles.toolbar}>
      <label className="fl-field">Поиск пользователей<input type="search" placeholder="Email, фамилия или имя" value={query} onChange={event => setQuery(event.target.value)} /></label>
      <label className="fl-field">Фильтр по роли<select value={role} onChange={event => { setRole(event.target.value); setPage(0) }}><option value="">Все роли</option>
        {roles.map(item => <option key={item.code} value={item.code}>{roleLabel(item.code, item.name)}</option>)}</select></label>
    </div>
    {success && <p role="status">{success}</p>}
    {(error || rolesError) && <Notice tone="danger">{error || rolesError} <Button onClick={() => setReload(value => value + 1)}>Повторить</Button></Notice>}
    {loading && <div role="status"><p>Загружаем пользователей…</p><div className="fl-skeleton" aria-hidden="true"><div /><div /><div /></div></div>}
    {data && <>
      {!data.items.length ? <EmptyState title="Пользователи не найдены — измените запрос" action={<Button onClick={() => { setQuery(''); setRole(''); setPage(0) }}>Сбросить поиск</Button>}><p>Попробуйте другое имя, email или роль.</p></EmptyState> : <div className={styles.usersCards}><DataTable cards caption="Пользователи платформы" columns={columns} rows={data.items} rowKey={user => user.id} minWidth={900} /></div>}
      <ActionBar className={styles.pagination}><Button title={page === 0 ? 'Вы на первой странице' : loading ? 'Дождитесь загрузки пользователей' : undefined} disabled={page === 0 || loading} onClick={() => setPage(value => value - 1)}>Назад</Button>
        <span className="fl-numeric">Страница {page + 1} из {Math.max(1, Math.ceil(data.total / data.size))} · Всего {data.total}</span><Button title={(page + 1) * data.size >= data.total ? 'Вы на последней странице' : loading ? 'Дождитесь загрузки пользователей' : undefined} disabled={(page + 1) * data.size >= data.total || loading} onClick={() => setPage(value => value + 1)}>Далее</Button></ActionBar>
    </>}
    {action && <OrganizerDialog title={action.type === 'create' ? 'Создать пользователя' : action.type === 'roles' ? 'Роли пользователя' : action.type === 'password' ? 'Сменить пароль' : 'Изменить статус'} busy={busy} onClose={() => setAction(null)}>
      <form className={styles.form} onSubmit={submit}><fieldset disabled={busy} className={`fl-fieldset ${styles.form}`}><legend>Данные пользователя</legend>
        {action.type !== 'create' && <p>{action.user.email}</p>}
        {action.type === 'create' && <>
          <label className="fl-field">Email<input name="email" type="email" autoComplete="off" required /></label>
          <label className="fl-field">Имя<input name="firstName" maxLength={128} required /></label>
          <label className="fl-field">Фамилия<input name="lastName" maxLength={128} required /></label>
          <label className="fl-field">Дата рождения<input name="birthDate" type="date" required /></label>
          {passwordFields}{roleCheckboxes}
        </>}
        {action.type === 'password' && passwordFields}
        {action.type === 'roles' && <><p>Глобальные роли пользователя</p>{roleCheckboxes}
          {scopedRoles.length > 0 && <p>Роли в отдельных организациях или соревнованиях: {scopedRoles.join(', ')}. Они сохраняются при изменении глобальных ролей.</p>}
        </>}
        {removesAdmin && <label className={styles.check}><span><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} /> Подтверждаю снятие роли администратора платформы</span></label>}
        {action.type === 'status' && <p>{action.user.status === 'ACTIVE' ? 'Заблокировать пользователя? Вход и существующие токены станут недоступны.' : 'Разблокировать пользователя?'}</p>}
      </fieldset>
        {formError && <Notice tone="danger">{formError}</Notice>}
        {action.type === 'roles' && !roleReady && <p className="fl-meta">Дождитесь загрузки ролей пользователя. Если загрузка завершилась ошибкой, закройте диалог и откройте его снова.</p>}
        {busy && <p role="status">Сохраняем изменения…</p>}
        <ActionBar className="fl-dialog-actions">
          <Button disabled={busy} title={busy ? 'Дождитесь завершения текущего действия' : undefined} onClick={() => setAction(null)}>Отмена</Button>
          <Button type="submit" variant="primary" loading={busy} title={busy ? 'Дождитесь завершения текущего действия' : action.type === 'roles' && !roleReady ? 'Дождитесь загрузки ролей пользователя' : undefined} disabled={busy || (action.type === 'roles' && !roleReady)}>{action.type === 'status' ? 'Подтвердить' : 'Сохранить'}</Button>
        </ActionBar>
      </form>
    </OrganizerDialog>}
  </section>
}
