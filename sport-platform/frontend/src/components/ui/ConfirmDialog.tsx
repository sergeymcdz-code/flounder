import { useEffect, useId, useRef, useState } from 'react'
export function ConfirmDialog({ title, description, confirmLabel, tone = 'primary', reason = false, reasonRequired = reason, busy = false, busyLabel = 'Сохраняем…', error, onConfirm, onClose }: {
  title: string; description: string; confirmLabel: string; tone?: 'primary' | 'danger'; reason?: boolean; reasonRequired?: boolean; busy?: boolean; busyLabel?: string; error?: string; onConfirm: (reason: string) => void; onClose: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const confirm = useRef<HTMLButtonElement>(null)
  const id = useId()
  const [value, setValue] = useState('')
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    dialog?.showModal()
    ;(tone === 'danger' ? cancel : confirm).current?.focus()
    return () => { dialog?.close(); previous?.focus() }
  }, [tone])
  return <dialog ref={ref} className="fl-confirm" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} onCancel={e => { e.preventDefault(); if (!busy) onClose() }}>
    <h2 id={`${id}-title`}>{title}</h2><p id={`${id}-description`}>{description}</p>
    <form onSubmit={e => { e.preventDefault(); if (!busy && (!reasonRequired || value.trim())) onConfirm(value.trim()) }}>
      {reason && <label className="fl-field">Причина{!reasonRequired && ' (необязательно)'}<textarea required={reasonRequired} maxLength={500} value={value} onChange={e => setValue(e.target.value)} /></label>}
      {error && <p role="alert">{error}</p>}
      <div className="fl-actionbar fl-form-actions"><button ref={cancel} className="fl-btn fl-btn-secondary" type="button" disabled={busy} onClick={onClose}>Отмена</button><button ref={confirm} className={`fl-btn fl-btn-${tone}`} type="submit" disabled={busy || (reasonRequired && !value.trim())}>{busy ? busyLabel : confirmLabel}</button></div>
    </form>
  </dialog>
}
