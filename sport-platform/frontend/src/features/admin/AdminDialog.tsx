import { useEffect, useRef, type ReactNode } from 'react'
import styles from './Admin.module.css'

export function AdminDialog({ title, children, busy, onClose }: {
  title: string; children: ReactNode; busy: boolean; onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const element = dialog.current
    element?.showModal()
    return () => { element?.close(); previous?.focus() }
  }, [])
  return <dialog ref={dialog} className={styles.dialog} aria-label={title}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <div className={styles.heading}><h2>{title}</h2>
      <button title={busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-secondary" type="button" disabled={busy} onClick={onClose} aria-label="Закрыть">×</button></div>
    {children}
  </dialog>
}
