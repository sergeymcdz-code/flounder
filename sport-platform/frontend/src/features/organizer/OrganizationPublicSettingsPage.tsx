import { useEffect, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { getOrganizations, type OrganizationResponse } from '../../api/organizations.api'
import { ApiError, apiFetch, apiDownload } from '../../api/http'
import type { PublicProfile } from '../public/calendar.types'
import styles from '../public/Calendar.module.css'

export function OrganizationPublicSettingsPage() {
  const token = useAuth().session?.accessToken ?? ''
  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([])
  const [id, setId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    getOrganizations(token).then(rows => { if (active) { setOrganizations(rows); setId(rows[0]?.id ?? '') } })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Не удалось загрузить организации.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token])
  return <section className={styles.settings}>
    <h1>Публичная страница организации</h1>
    {loading && <p role="status">Загружаем организации…</p>}
    {error && <p role="alert">{error}</p>}
    {!loading && !error && !organizations.length && <p>У вас пока нет организаций.</p>}
    {!!organizations.length && <label>Организация<select value={id} onChange={e => setId(e.target.value)}>{organizations.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}</select></label>}
    {id && <ProfileForm key={`${token}:${id}`} token={token} id={id} organization={organizations.find(o => o.id === id)!} />}
  </section>
}
function ProfileForm({ token, id, organization }: { token: string; id: string; organization: OrganizationResponse }) {
  const path = `/api/v1/organizations/${id}`
  const [profile, setProfile] = useState<PublicProfile>()
  const [savedSlug, setSavedSlug] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [readonly, setReadonly] = useState(false)
  const [logoRevision, setLogoRevision] = useState(0)
  const [logoPreview, setLogoPreview] = useState('')
  const hasLogo = profile?.hasLogo ?? false
  useEffect(() => {
    let active = true; let url = ''
    if (hasLogo) void apiDownload(`${path}/logo`, token).then(blob => {
      if (active) { url = URL.createObjectURL(blob); setLogoPreview(url) }
    }).catch(() => { if (active) setError('Не удалось загрузить логотип.') })
    return () => { active = false; if (url) URL.revokeObjectURL(url) }
  }, [path, token, hasLogo, logoRevision])
  useEffect(() => {
    let active = true
    apiFetch<PublicProfile>(`${path}/public-profile`, { accessToken: token }).then(row => {
      if (active) { setProfile(row); setSavedSlug(row.publicSlug ?? '') }
    }).catch(cause => {
      if (active) {
        if (cause instanceof ApiError && cause.status === 403) setReadonly(true)
        else setError(cause instanceof Error ? cause.message : 'Не удалось загрузить профиль.')
      }
    })
    return () => { active = false }
  }, [path, token])
  async function mutate(action: () => Promise<PublicProfile | void>, success: string) {
    setBusy(true); setError(''); setMessage('')
    try {
      const result = await action()
      if (result) { setProfile(result); setSavedSlug(result.publicSlug ?? '') }
      setLogoRevision(n => n + 1); setMessage(success)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось сохранить изменения.') }
    finally { setBusy(false) }
  }
  function upload(file?: File) {
    if (!file) return
    if (file.size > 2 * 1024 * 1024) { setError('Размер логотипа превышает 2 МБ.'); return }
    const body = new FormData(); body.append('file', file)
    void mutate(() => apiFetch<PublicProfile>(`${path}/logo`, { accessToken: token, method: 'POST', body }), 'Логотип загружен.')
  }
  return <>
    {error && <p role="alert">{error}</p>}
    {readonly && <div><h2>{organization.name}</h2>{organization.city && <p>{organization.city}</p>}<p>Только просмотр. Настройки публичной страницы доступны владельцу и администратору организации.</p><a href="/">Открыть публичный календарь</a></div>}
    {!profile && !error && !readonly && <p role="status">Загружаем профиль…</p>}
    {profile && <form className={styles.settings} onSubmit={e => {
      e.preventDefault()
      void mutate(() => apiFetch<PublicProfile>(`${path}/public-profile`, { accessToken: token, method: 'PUT', body: JSON.stringify(profile) }), 'Публичная страница сохранена.')
    }}>
      <fieldset disabled={busy}>
        <legend>Профиль организации</legend>
        <label>Адрес страницы<input required pattern="[a-z0-9][a-z0-9-]{2,127}" maxLength={128} value={profile.publicSlug ?? ''} onChange={e => setProfile({ ...profile, publicSlug: e.target.value })} aria-describedby="slug-preview" /></label>
        <p id="slug-preview">sportgate.pro/o/{profile.publicSlug || 'адрес-организации'}</p>
        {savedSlug && profile.isPublic && <a href={`/o/${encodeURIComponent(savedSlug)}`} target="_blank" rel="noopener noreferrer">Открыть публичную страницу ↗</a>}
        <label>Описание<textarea maxLength={2000} value={profile.description ?? ''} onChange={e => setProfile({ ...profile, description: e.target.value })} /></label>
        <label>Сайт<input type="url" maxLength={255} placeholder="https://" value={profile.website ?? ''} onChange={e => setProfile({ ...profile, website: e.target.value })} /></label>
        <label>Публичный e-mail<input type="email" maxLength={255} value={profile.publicEmail ?? ''} onChange={e => setProfile({ ...profile, publicEmail: e.target.value })} /></label>
        <label>Публичный телефон<input type="tel" maxLength={32} value={profile.publicPhone ?? ''} onChange={e => setProfile({ ...profile, publicPhone: e.target.value })} /></label>
        <label><span><input type="checkbox" checked={profile.isPublic} onChange={e => setProfile({ ...profile, isPublic: e.target.checked })} /> Показывать организацию публично</span></label>
        <button type="submit">{busy ? 'Сохраняем…' : 'Сохранить'}</button>
      </fieldset>
      <fieldset disabled={busy}><legend>Логотип</legend>
        {profile.hasLogo && logoPreview && <img className={styles.logo} src={logoPreview} alt="Логотип организации" />}
        <label>Загрузить логотип (PNG или JPG, до 2 МБ)<input type="file" accept="image/png,image/jpeg" onChange={e => { upload(e.target.files?.[0]); e.target.value = '' }} /></label>
        {profile.hasLogo && <button type="button" className="fl-button-ghost" onClick={() => void mutate(async () => {
          await apiFetch(`${path}/logo`, { accessToken: token, method: 'DELETE' }); setProfile(p => p && { ...p, hasLogo: false })
        }, 'Логотип удалён.')}>Удалить логотип</button>}
      </fieldset>
    </form>}
    <p role="status">{message}</p>
  </>
}
