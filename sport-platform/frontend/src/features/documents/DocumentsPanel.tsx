import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useEffect, useState, type FormEvent } from 'react'
import { deleteDocument, reviewDocument, documentTypes, downloadDocument, getDocuments, uploadDocument, type DocumentType, type ParticipantDocument } from '../../api/documents.api'
import styles from './DocumentsPanel.module.css'

export function DocumentsPanel({ profileId, accessToken, eventId, manager = false, onChanged }: {
  profileId: string; accessToken: string; eventId?: string; manager?: boolean; onChanged?: () => void
}) {
  const [documents, setDocuments] = useState<ParticipantDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [review, setReview] = useState<{ document: ParticipantDocument; status: 'ACCEPTED' | 'REJECTED' } | null>(null)
  const [type, setType] = useState<DocumentType>('MEDICAL_CERT')
  useEffect(() => {
    let active = true
    void Promise.resolve().then(() => { if (active) { setLoading(true); setError('') } })
    getDocuments(profileId, accessToken, eventId).then((data) => { if (active) setDocuments(data) })
      .catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Не удалось загрузить документы.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [profileId, accessToken, eventId])
  async function action(operation: () => Promise<void>) {
    setBusy(true); setError('')
    try { await operation() }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось выполнить действие.') }
    finally { setBusy(false) }
  }
  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const data = new FormData(form)
    const file = data.get('file')
    if (!(file instanceof File) || !file.size) { setError('Выберите документ.'); return }
    if (file.size > 10 * 1024 * 1024) { setError('Размер документа превышает 10 МБ.'); return }
    if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) { setError('Разрешены только PDF, JPG и PNG.'); return }
    for (const name of ['issuedAt', 'validUntil']) if (!data.get(name)) data.delete(name)
    if (eventId) data.set('eventId', eventId)
    await action(async () => {
      await uploadDocument(profileId, data, accessToken)
      setDocuments(await getDocuments(profileId, accessToken, eventId))
      form.reset(); onChanged?.()
    })
  }
  return <section className={styles.panel} aria-label="Документы участника">
    <h3>Документы</h3>
    <p>PDF, JPG или PNG до 10 МБ. Документы видны вам и организаторам ваших соревнований.</p>
    {loading && <p role="status">Загружаем документы…</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {!loading && !documents.length && <p>Документы пока не загружены.</p>}
    <ul className={styles.list}>
      {(Object.keys(documentTypes) as DocumentType[]).map((key) => {
        const rows = documents.filter((d) => d.type === key)
        return <li key={key}><strong>{documentTypes[key]}</strong>{!rows.length && <span> — Не загружен</span>}
          {rows.map((d) => <div key={d.id} className={styles.document}>
            <span><StatusBadge kind="document" code={d.status} /> · {d.originalName}</span>
            <span>Выдан: {d.issuedAt ?? 'не указано'} · Действует до: {d.validUntil ?? 'не указано'}</span>
            {d.rejectReason && <span className={styles.error}>{d.rejectReason}</span>}
            <div className={styles.actions}>
              {manager && eventId && d.status === 'PENDING' && <><button className="fl-btn fl-btn-secondary" type="button" disabled={busy} onClick={() => setReview({ document: d, status: 'ACCEPTED' })}>Принять</button><button className="fl-btn fl-btn-secondary" type="button" disabled={busy} onClick={() => setReview({ document: d, status: 'REJECTED' })}>Отклонить…</button></>}
              <button className="fl-btn fl-btn-secondary" disabled={busy} type="button" onClick={() => void action(async () => {
                const blob = await downloadDocument(d.id, accessToken, eventId)
                const url = URL.createObjectURL(blob)
                const a = document.createElement('a'); a.href = url; a.download = d.originalName; a.click()
                setTimeout(() => URL.revokeObjectURL(url), 1000)
              })}>Скачать</button>
              {!manager && d.status === 'PENDING' && <button className="fl-btn fl-btn-secondary" disabled={busy} type="button" onClick={() => void action(async () => {
                await deleteDocument(profileId, d.id, accessToken)
                setDocuments(await getDocuments(profileId, accessToken, eventId)); onChanged?.()
              })}>Удалить</button>}
            </div>
          </div>)}
        </li>
      })}
    </ul>
    {review && eventId && <ConfirmDialog title={review.status === 'ACCEPTED' ? 'Принять документ?' : 'Отклонить документ?'} description={`${documentTypes[review.document.type]} · ${review.document.originalName}. Участник увидит результат проверки и причину отклонения.`} confirmLabel={review.status === 'ACCEPTED' ? 'Принять' : 'Отклонить'} tone={review.status === 'ACCEPTED' ? 'primary' : 'danger'} reason={review.status === 'REJECTED'} busy={busy} error={error} onClose={() => setReview(null)} onConfirm={rejectReason => void action(async () => {
      await reviewDocument(profileId, review.document.id, { status: review.status, rejectReason: rejectReason || null }, accessToken, eventId)
      setDocuments(await getDocuments(profileId, accessToken, eventId)); setReview(null); onChanged?.()
    })} />}
    <form onSubmit={(e) => void upload(e)} className={styles.form}>
      <label className="fl-field">Тип документа<select name="type" value={type} onChange={(e) => setType(e.target.value as DocumentType)}>
        {Object.entries(documentTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="fl-field">Файл документа<input name="file" type="file" accept="image/*,application/pdf" required /></label>
      <label className="fl-field">Дата выдачи<input name="issuedAt" type="date" /></label>
      <label className="fl-field">Действует до<input name="validUntil" type="date" /></label>
      <button className="fl-btn fl-btn-secondary" disabled={busy || loading} type="submit">{busy ? 'Сохраняем…' : 'Загрузить документ'}</button>
    </form>
  </section>
}
