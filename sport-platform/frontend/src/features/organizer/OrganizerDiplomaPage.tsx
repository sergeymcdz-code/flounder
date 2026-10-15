import { useState } from 'react'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { diplomaDefaults, placeholders, saveDiploma, uploadBackground, type DiplomaSettings } from '../../api/print-forms.api'
import { useOrganizerEvent } from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'

export function OrganizerDiplomaPage() {
  const { event, accessToken, busy, run } = useOrganizerEvent()
  const [form, setForm] = useState<DiplomaSettings>(() => {
    try { return { ...diplomaDefaults, ...JSON.parse(event.settingsJson || '{}').diploma } } catch { return diplomaDefaults }
  })
  const [message, setMessage] = useState('')
  const [fileError, setFileError] = useState('')
  const archived = event.status === 'ARCHIVED'
  return <article className={styles.card}><h2>Грамоты</h2>
    <form className={styles.stack} onSubmit={e => { e.preventDefault(); void run(async () => { await saveDiploma(event.id, form, accessToken); setMessage('Шаблон сохранён') }) }}>
      <label><input type="checkbox" disabled={archived} checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} />Включить грамоты</label>
      <label>Заголовок<input maxLength={4000} disabled={archived} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></label>
      <label>Текст грамоты<textarea rows={6} maxLength={4000} disabled={archived} value={form.bodyTemplate} onChange={e => setForm({ ...form, bodyTemplate: e.target.value })} /></label>
      <p>Плейсхолдеры: {placeholders.map(p => `{${p}}`).join(' ')}. placeText: 1-е, 2-е, 3-е.</p>
      <label>Подписи<textarea maxLength={4000} disabled={archived} value={form.footer} onChange={e => setForm({ ...form, footer: e.target.value })} /></label>
      <label><input type="checkbox" disabled={archived} checked={form.showLogo} onChange={e => setForm({ ...form, showLogo: e.target.checked })} />Показывать SportGate</label>
      <button disabled={busy || archived} type="submit">Сохранить шаблон</button>
      {message && <p role="status">{message}</p>}
    </form>
    <label>Фон грамоты (PNG/JPEG, до 5 МБ)<input type="file" accept="image/png,image/jpeg" disabled={busy || archived} onChange={e => {
      const file = e.target.files?.[0]
      if (!file) return
      setFileError('')
      if (file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg'].includes(file.type)) { setFileError('Выберите PNG или JPEG размером до 5 МБ'); return }
      void run(async () => { const result = await uploadBackground(event.id, file, accessToken); setForm(current => ({ ...current, backgroundFileKey: result.backgroundFileKey })); setMessage('Фон загружен') })
    }} /></label>
    {fileError && <p role="alert">{fileError}</p>}
    {form.backgroundFileKey && <button disabled={busy || archived} onClick={() => setForm({ ...form, backgroundFileKey: null })}>Убрать фон из шаблона</button>}
    <p>Сохраните изменения перед скачиванием пробной грамоты.</p>
    <PdfDownloadButton path={`/api/v1/events/${event.id}/documents/diplomas.pdf?sample=true`} filename="Пробная-грамота.pdf" accessToken={accessToken} label="Пробная грамота (PDF)" />
  </article>
}
